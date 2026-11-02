package ru.mosvegas.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mosvegas.menu.mixin.NativeImageAccessor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class VideoBackground {
    private static final Logger LOGGER = LoggerFactory.getLogger("MosVegasMenu");
    private static final int SOURCE_FRAME_COUNT = 1470;
    private static final double SOURCE_FPS = 30.0;
    private static final Identifier TEXTURE_ID = new Identifier(MosVegasMenuClient.MOD_ID, "menu_video_stream");

    // A larger ready queue absorbs short resource-loading and GC stalls. Frames
    // live in native memory, so this does not add pressure to the Java heap.
    private static final BlockingQueue<DecodedFrame> QUEUE = new ArrayBlockingQueue<>(12);
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final AtomicLong UPLOAD_COUNT = new AtomicLong();
    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "MosVegas menu video decoder");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY + 1);
        return thread;
    });

    private static volatile long startedAt;
    private static long decodedSequence = -1;
    private static long displayedSequence = -1;
    private static NativeImageBackedTexture texture;

    private VideoBackground() {}

    public static void initialize() {
        if (RUNNING.compareAndSet(false, true)) DECODER.execute(VideoBackground::decodeLoop);
    }

    public static void render(DrawContext context, int width, int height) {
        // Start the playback clock only when the title screen is actually shown.
        // The decoder can fill the ready queue while Minecraft is still loading.
        if (startedAt == 0L) startedAt = System.nanoTime();
        long wanted = wantedSequence();

        DecodedFrame newest = null;
        DecodedFrame frame;
        while ((frame = QUEUE.peek()) != null && frame.sequence <= wanted) {
            frame = QUEUE.poll();
            if (newest != null) newest.image.close();
            newest = frame;
        }
        if (newest != null) upload(newest);
        if (texture == null) return;

        double screenRatio = (double) width / Math.max(1, height);
        double videoRatio = 16.0 / 9.0;
        int drawWidth, drawHeight, x, y;
        if (screenRatio > videoRatio) {
            drawWidth = width;
            drawHeight = (int) Math.ceil(width / videoRatio);
            x = 0;
            y = (height - drawHeight) / 2;
        } else {
            drawHeight = height;
            drawWidth = (int) Math.ceil(height * videoRatio);
            x = (width - drawWidth) / 2;
            y = 0;
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        context.drawTexture(TEXTURE_ID, x, y, 0, 0, drawWidth, drawHeight, drawWidth, drawHeight);
    }

    private static void decodeLoop() {
        while (RUNNING.get()) {
            try {
                long wanted = wantedSequence();
                // A long render-thread pause can leave stale frames in the queue.
                // Resync only when more than the complete buffer was missed; the
                // previous three-frame threshold caused visible jumps on tiny stalls.
                if (decodedSequence < wanted - QUEUE.size() - 2) {
                    clearQueue();
                    decodedSequence = wanted - 1;
                }
                if (QUEUE.remainingCapacity() == 0) {
                    TimeUnit.MILLISECONDS.sleep(2);
                    continue;
                }
                long next = decodedSequence + 1;
                int resourceFrame = (int) Math.floorMod(next, SOURCE_FRAME_COUNT);
                QUEUE.put(new DecodedFrame(next, decode(resourceFrame)));
                decodedSequence = next;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception error) {
                LOGGER.error("Could not decode menu video frame", error);
                try { TimeUnit.MILLISECONDS.sleep(100); } catch (InterruptedException ignored) { return; }
            }
        }
    }

    private static long wantedSequence() {
        if (startedAt == 0L) return 0L;
        long elapsed = Math.max(0, System.nanoTime() - startedAt);
        return (long) (elapsed * SOURCE_FPS / 1_000_000_000L);
    }

    private static void clearQueue() {
        DecodedFrame frame;
        while ((frame = QUEUE.poll()) != null) frame.image.close();
    }

    private static NativeImage decode(int frame) throws IOException {
        String path = String.format("/assets/mosvegas/textures/video/frame-%04d.jpg", frame + 1);
        try (InputStream input = VideoBackground.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing menu video frame: " + path);
            // NativeImage's public reader deliberately accepts PNG only. Decode
            // our JPEGs through stb directly, then transfer the resulting native
            // RGBA buffer to NativeImage without creating a BufferedImage or int[].
            byte[] encoded = input.readAllBytes();
            ByteBuffer source = MemoryUtil.memAlloc(encoded.length);
            ByteBuffer pixels = null;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                source.put(encoded).flip();
                IntBuffer imageWidth = stack.mallocInt(1);
                IntBuffer imageHeight = stack.mallocInt(1);
                IntBuffer components = stack.mallocInt(1);
                pixels = STBImage.stbi_load_from_memory(
                    source, imageWidth, imageHeight, components, NativeImage.Format.RGBA.getChannelCount());
                if (pixels == null) throw new IOException("Could not decode JPEG: " + STBImage.stbi_failure_reason());
                int width = imageWidth.get(0);
                int height = imageHeight.get(0);
                NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
                long target = ((NativeImageAccessor) (Object) image).mosvegas$getPointer();
                MemoryUtil.memCopy(MemoryUtil.memAddress(pixels), target, (long) width * height * 4);
                return image;
            } finally {
                if (pixels != null) STBImage.stbi_image_free(pixels);
                MemoryUtil.memFree(source);
            }
        }
    }

    private static void upload(DecodedFrame frame) {
        try {
            if (texture == null || texture.getImage() == null
                || texture.getImage().getWidth() != frame.image.getWidth()
                || texture.getImage().getHeight() != frame.image.getHeight()) {
                if (texture != null) texture.close();
                texture = new NativeImageBackedTexture(frame.image.getWidth(), frame.image.getHeight(), false);
                texture.setFilter(true, false);
                MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE_ID, texture);
            }
            texture.getImage().copyFrom(frame.image);
            texture.upload();
            texture.setFilter(true, false); // bilinear fullscreen scaling
            displayedSequence = frame.sequence;
            long uploads = UPLOAD_COUNT.incrementAndGet();
            if (uploads == 1 || uploads == 60) {
                LOGGER.info("MosVegas video playback active: upload {}, source frame {}", uploads,
                    Math.floorMod(frame.sequence, SOURCE_FRAME_COUNT));
            }
        } finally {
            frame.image.close();
        }
    }

    private record DecodedFrame(long sequence, NativeImage image) {}
}
