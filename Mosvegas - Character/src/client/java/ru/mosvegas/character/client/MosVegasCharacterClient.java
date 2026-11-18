package ru.mosvegas.character.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import ru.mosvegas.character.MosVegasCharacter;
import ru.mosvegas.character.client.render.BustFeatureRenderer;
import ru.mosvegas.character.client.render.HairFeatureRenderer;
import ru.mosvegas.character.client.render.HairModels;
import ru.mosvegas.character.client.screen.CharacterEditorScreen;
import ru.mosvegas.character.client.skin.SkinComposer;
import ru.mosvegas.character.data.AppearanceLookup;
import ru.mosvegas.character.data.Appearance;
import ru.mosvegas.character.net.CharacterNet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MosVegasCharacterClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AppearanceLookup.setClient(ClientCharacters::effective);
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerEntityRenderer player) {
                helper.register(new BustFeatureRenderer(player));
                helper.register(new HairFeatureRenderer(player));
            }
        });
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return MosVegasCharacter.id("skin_layers");
            }

            @Override
            public void reload(ResourceManager manager) {
                SkinComposer.reload();
                HairModels.reload();
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(CharacterNet.OPEN_EDITOR, (client, handler, buf, sender) -> {
            Appearance current = buf.readBoolean() ? Appearance.read(buf) : null;
            int n = buf.readVarInt();
            List<String> outfits = new ArrayList<>(n);
            for (int i = 0; i < n; i++) outfits.add(buf.readString(64));
            client.execute(() -> {
                ClientCharacters.requireEditor(current, outfits);
                client.setScreen(new CharacterEditorScreen());
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(CharacterNet.CLOSE_EDITOR, (client, handler, buf, sender) -> client.execute(() -> {
            ClientCharacters.releaseEditor();
            if (client.currentScreen instanceof CharacterEditorScreen) client.setScreen(null);
        }));
        ClientPlayNetworking.registerGlobalReceiver(CharacterNet.APPEARANCE, (client, handler, buf, sender) -> {
            UUID owner = buf.readUuid();
            Appearance a = buf.readBoolean() ? Appearance.read(buf) : null;
            client.execute(() -> {
                ClientCharacters.set(owner, a);
                if (client.world != null) {
                    var p = client.world.getPlayerByUuid(owner);
                    if (p != null) p.calculateDimensions();
                }
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientCharacters::clear));
        ClientTickEvents.END_CLIENT_TICK.register(MosVegasCharacterClient::tick);
    }

    private static final boolean AUTOTEST = Boolean.getBoolean("mosvegas.character.autotest");
    private static int testTicks;
    private static int testStage;

    private static void shot(MinecraftClient client, String name) {
        net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), t -> {});
    }

    private static void autotest(MinecraftClient client) {
        if (client.player == null) return;
        testTicks++;
        int duo = Integer.getInteger("mosvegas.character.duo", 0);
        if (duo > 0) {
            duoTest(client, duo);
            return;
        }
        int gallery = Integer.getInteger("mosvegas.character.gallery", -1);
        if (gallery >= 0) {
            if (testStage == 0 && client.currentScreen instanceof CharacterEditorScreen && testTicks > 30) {
                submit(ClientCharacters.draft().copy());
                testStage = 1;
                testTicks = 0;
            } else if (testStage == 0 && testTicks > 120) {
                testStage = 1;
                testTicks = 0;
            } else if (testStage == 1 && client.currentScreen == null && testTicks > 30) {
                client.setScreen(new ru.mosvegas.character.client.dev.GalleryScreen(gallery));
                testStage = 2;
                testTicks = 0;
            } else if (testStage == 2 && testTicks > 30) {
                shot(client, "autotest_gallery_" + gallery + ".png");
                testStage = 3;
                testTicks = 0;
            } else if (testStage == 3 && testTicks > 10) {
                client.scheduleStop();
                testStage = 4;
            }
            return;
        }
        if (testStage == 0) {
            if (client.currentScreen instanceof CharacterEditorScreen && testTicks > 40) {
                testStage = 10;
                testTicks = 0;
            } else if (testTicks > 100) {
                testStage = 1;
                testTicks = 0;
            }
        } else if (testStage >= 10 && testStage < 30) {
            if (!(client.currentScreen instanceof CharacterEditorScreen editor)) return;
            int s = testStage - 10;
            if (testTicks == 1) {
                Appearance a = ClientCharacters.draft();
                a.gender = s == 6 || s == 9 ? 0 : 1;
                a.skin = 2; a.eyes = 4; a.hairColor = 5; a.height = 2; a.build = 1;
                if (Integer.getInteger("mosvegas.character.stepGender", -1) >= 0) a.gender = Integer.getInteger("mosvegas.character.stepGender", -1);
                if (Boolean.getBoolean("mosvegas.character.onlyStep")) s = Integer.getInteger("mosvegas.character.step", 0);
                if (a.gender == 0) { a.hairColor = 1; a.skin = 3; a.eyes = 1; a.beard = 0; }
                editor.setStep(Math.min(s, 7));
                editor.setZoom(s == 8 ? 1f : 0f);
                if (s == 9) editor.openConfirm();
            }
            if (testTicks == 14) {
                shot(client, "autotest_editor_" + s + ".png");
                testTicks = 0;
                testStage++;
                if (Boolean.getBoolean("mosvegas.character.onlyStep") && testStage < 20) testStage = 20;
                if (testStage == 20) {
                    editor.closeConfirm();
                    submit(ClientCharacters.draft().copy());
                    testStage = 1;
                }
            }
        } else if (testStage == 1 && client.currentScreen == null && testTicks > 40) {
            shot(client, "autotest_world.png");
            testStage = 2;
            testTicks = 0;
        } else if (testStage == 2 && testTicks > 20) {
            client.scheduleStop();
            testStage = 3;
        }
    }

    private static void duoTest(MinecraftClient client, int duo) {
        if (testStage == 0) {
            if (client.currentScreen instanceof CharacterEditorScreen && testTicks > 30) {
                Appearance a = ClientCharacters.draft().copy();
                if (duo == 1) {
                    a.gender = Appearance.MALE; a.skin = 3; a.eyes = 1; a.hairColor = 1; a.hair = 3; a.beard = 9; a.build = 2; a.height = 3;
                } else {
                    a.gender = Appearance.FEMALE; a.skin = 1; a.eyes = 4; a.hairColor = 5; a.hair = 8; a.beard = 0; a.build = 1; a.height = 1;
                }
                submit(a);
                testStage = 1;
                testTicks = 0;
            } else if (testTicks > 200) {
                testStage = 1;
                testTicks = 0;
            }
        } else if (testStage == 1 && client.currentScreen == null && testTicks > 20) {
            client.player.setPosition(client.player.getX() + (duo == 1 ? -2 : 2), client.player.getY(), client.player.getZ());
            testStage = 2;
            testTicks = 0;
        } else if (testStage == 2) {
            net.minecraft.entity.player.PlayerEntity other = null;
            for (net.minecraft.entity.player.PlayerEntity p : client.world.getPlayers()) if (p != client.player) other = p;
            if (other != null && ClientCharacters.get(other.getUuid()) != null) {
                double dx = other.getX() - client.player.getX(), dz = other.getZ() - client.player.getZ();
                double dy = other.getY() + 1.2 - client.player.getEyeY();
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                client.player.setYaw(yaw);
                client.player.setHeadYaw(yaw);
                client.player.setBodyYaw(yaw);
                client.player.setPitch(pitch);
                if (testTicks > 100) {
                    client.options.hudHidden = true;
                    shot(client, "autotest_duo_" + duo + ".png");
                    testStage = 3;
                    testTicks = 0;
                }
            } else if (testTicks > 7200) {
                shot(client, "autotest_duo_" + duo + "_alone.png");
                testStage = 3;
                testTicks = 0;
            }
        } else if (testStage == 3 && testTicks > 20) {
            client.options.hudHidden = false;
            boolean alone = client.world.getPlayers().size() < 2;
            if (duo == 2 || alone || testTicks > 1200) {
                client.scheduleStop();
                testStage = 4;
            }
        }
    }

    private static void tick(MinecraftClient client) {
        if (AUTOTEST) autotest(client);
        if (!ClientCharacters.editorRequired() || client.player == null || client.getNetworkHandler() == null) return;
        if (!(client.currentScreen instanceof CharacterEditorScreen)) client.setScreen(new CharacterEditorScreen());
    }

    public static void submit(Appearance appearance) {
        PacketByteBuf buf = PacketByteBufs.create();
        appearance.write(buf);
        ClientPlayNetworking.send(CharacterNet.SUBMIT, buf);
    }
}
