package studio.merami.sliminterfacefabric.client.plates;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import studio.merami.slimcore.plates.PlateNumber;
import studio.merami.slimcore.plates.PlateRegions;
import studio.merami.sliminterfacefabric.SlimVehicle;
import studio.merami.sliminterfacefabric.client.render.PlateTextures;
import studio.merami.sliminterfacefabric.client.ui.GarageScreen;
import studio.merami.sliminterfacefabric.client.ui.GarageUi;
import studio.merami.sliminterfacefabric.client.ui.GarageUi.Area;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class PlateShopScreen extends GarageScreen {
    private static final int W = 624;
    private static final int H = 344;
    private static final int UNIT = 3;
    private static final int PLATE_W = 420;
    private static final int PLATE_X = 182;
    private static final int PLATE_Y = 152;
    private static final int PRESS_X = 392;
    private static final int RAM_W = 60;
    private static final int RAM_H = 200;
    private static final int RAM_REST = 124;
    private static final int RAM_CONTACT = 232;
    private static final int GAUGE_X = 198;
    private static final float GAUGE_Y = 63.5f;
    private static final int NEEDLE_PIVOT_X = 5;
    private static final int NEEDLE_PIVOT_Y = 18;
    private static final float GAUGE_SWEEP = 120;
    private static final int FACE_W = 30;
    private static final int FACE_H = 32;
    private static final float GLYPH_ON_FACE = .17f;
    private static final float DIGIT_ADVANCE = 19 / 32f;
    private static final float DIGIT_GAP = .42f;
    private static final float GHOST_ALPHA = .08f;
    private static final int REGION_CELLS = 3;
    private static final int BALANCE_CELLS = 6;
    private static final float REGION_DIGIT = 32;
    private static final float BALANCE_DIGIT = 24;
    private static final int CHIP_W = 44;
    private static final int CHIP_H = 22;
    private static final int CHIP_STEP_X = 46;
    private static final int CHIP_STEP_Y = 25;
    private static final int CHIPS_IN_ROW = 3;
    private static final int MINI_CELL_W = 9;
    private static final int MINI_CELL_H = 14;
    private static final int MINI_GAP = 2;
    private static final int MINI_REGION_W = 14;
    private static final float PRE_EXTENSION = .05f;
    private static final float HOLD_EXTENSION = .2f;
    private static final long FIRST_TRAVEL_MS = 380;
    private static final long TRAVEL_MS = 240;
    private static final long LOCK_MS = 40;
    private static final long PRELOAD_MS = 80;
    private static final long LAST_HOLD_MS = 420;
    private static final long SLAM_MS = 50;
    private static final long DWELL_MS = 55;
    private static final long RETRACT_MS = 170;
    private static final long CLEAR_MS = 94;
    private static final long RETURN_MS = 430;
    private static final long WIPE_MS = 420;
    private static final int MIN_DRUM_STEPS = 14;
    private static final float IDLE_DRUM_SPEED = 9;
    private static final long ANSWER_TIMEOUT_MS = 6000;
    private static final long TICK_GAP_MS = 45;
    private static final long HEAT_MS = 300;
    private static final long JOLT_MS = 260;
    private static final long KICK_MS = 320;
    private static final long FLASH_MS = 130;
    private static final long PAID_MS = 1400;
    private static final long REVEAL_MS = 900;
    private static final long COUNT_MS = 700;
    private static final long DENIED_MS = 350;
    private static final long DONE_MS = 3000;
    private static final int SCAN_TRAIL = 14;
    private static final int GOLD = 0xFFC94A;
    private static final int[] SCALE = {0, 3, 5, 7, 10, 12, 15};
    private static final float[] CHORD = {1, 1.189f, 1.498f, 1.782f};
    private static final String[] SPIN_KEYS = {"gui.slimvehicle.plates.full", "gui.slimvehicle.plates.letters", "gui.slimvehicle.plates.digits"};
    private static final Sheet MACHINE = Sheet.of("machine", W, H, 1, 1);
    private static final Sheet PRESS_TOP = Sheet.of("press_top", 448, 92, 1, 1);
    private static final Sheet RAM = Sheet.of("ram", RAM_W, RAM_H, 1, 1);
    private static final Sheet SLED = Sheet.of("sled", 444, 112, 1, 1);
    private static final Sheet SLED_TOP = Sheet.of("sled_top", 444, 112, 1, 1);
    private static final Sheet KEY_REGION = Sheet.of("key_region", CHIP_W, CHIP_H, 1, 4);
    private static final Sheet KEY_MODE = Sheet.of("key_mode", 104, 70, 1, 4);
    private static final Sheet KEY_GO = Sheet.of("key_go", 114, 70, 1, 4);
    private static final Sheet KEY_BACK = Sheet.of("key_back", 136, 30, 1, 2);
    private static final Sheet KEY_PREV = Sheet.of("key_prev", 22, 42, 1, 3);
    private static final Sheet KEY_NEXT = Sheet.of("key_next", 22, 42, 1, 3);
    private static final Sheet SEGMENTS = Sheet.of("segments", 22, 32, 10, 1);
    private static final Sheet LAMPS = Sheet.of("lamps", 16, 16, 4, 1);
    private static final Sheet NEEDLE = Sheet.of("needle", 10, 24, 1, 1);
    private static final Sheet PUFF = Sheet.of("puff", 32, 32, 1, 1);
    private static final Sheet FACE = Sheet.of("face", FACE_W, FACE_H, 1, 1);
    private static final int KEY_NORMAL = 0;
    private static final int KEY_HOVER = 1;
    private static final int KEY_ACTIVE = 2;
    private static final int KEY_OFF = 3;
    private static final int LAMP_GREEN = 1;
    private static final int LAMP_AMBER = 2;
    private static final int LAMP_RED = 3;

    private record Sheet(ResourceLocation id, int w, int h, int columns, int rows) {
        static Sheet of(String name, int w, int h, int columns, int rows) {
            return new Sheet(SlimVehicle.id("textures/gui/plates/" + name + ".png"), w, h, columns, rows);
        }

        void draw(GuiGraphics gui, float x, float y, int column, int row) {
            RenderSystem.enableBlend();
            gui.pose().pushPose();
            gui.pose().translate(x, y, 0);
            gui.pose().scale(1f / UNIT, 1f / UNIT, 1);
            gui.blit(id, 0, 0, column * w * UNIT, row * h * UNIT, w * UNIT, h * UNIT, columns * w * UNIT, rows * h * UNIT);
            gui.pose().popPose();
        }
    }

    private record Queued(long at, SoundEvent sound, float pitch, float volume) {}

    private record Spark(float x, float y, float vx, float vy, long born, int life) {}

    private record Puff(float x, float y, float vx, float vy, long born, int life) {}

    private record RegionChip(String code, Area area) {}

    private record Line(Component text, int color) {}

    private static final class Slot {
        final boolean digit;
        char shown;
        char target;
        boolean rolled;
        float alpha = 1;
        long struckAt = -10000;

        Slot(boolean digit, char shown) {
            this.digit = digit;
            this.shown = shown;
        }
    }

    private static final class Press {
        final int[] slots;
        final long[] move;
        final long[] locked;
        final long[] strike;
        int nextLock;
        int nextStrike;

        Press(int[] slots, long start) {
            this.slots = slots;
            move = new long[slots.length];
            locked = new long[slots.length];
            strike = new long[slots.length];
            long time = start;
            for (int k = 0; k < slots.length; k++) {
                move[k] = time;
                locked[k] = time + (k == 0 ? FIRST_TRAVEL_MS : TRAVEL_MS) + LOCK_MS;
                strike[k] = locked[k] + (held(k) ? LAST_HOLD_MS : PRELOAD_MS) + SLAM_MS;
                time = strike[k] + DWELL_MS + CLEAR_MS;
            }
        }

        boolean held(int k) {
            return k == slots.length - 1 && k > 0;
        }

        long homeAt() {
            return strike[slots.length - 1] + DWELL_MS + CLEAR_MS;
        }

        long end() {
            return homeAt() + RETURN_MS;
        }

        int spinning(long now) {
            for (int k = 0; k < slots.length; k++) if (now >= move[k] && now < locked[k]) return k;
            return -1;
        }
    }

    private final Random random = new Random();
    private final List<Queued> queued = new ArrayList<>();
    private final List<Spark> sparks = new ArrayList<>();
    private final List<Puff> puffs = new ArrayList<>();
    private final Slot[] slots;
    private final Area[] modes = new Area[3];
    private final List<RegionChip> chips = new ArrayList<>();
    private final String style;
    private final String template;
    private int landedCount;
    private long lastFrame = Util.getMillis();
    private PlateShopClient.Menu menu;
    private String region;
    private String platePreview;
    private int selected = 1;
    private boolean spinning;
    private int spinAction;
    private long requestedAt;
    private long lastTick;
    private long finishedAt = -10000;
    private boolean answered;
    private String status = "";
    private boolean statusOk;
    private String balance;
    private String paid;
    private long paidAt = -10000;
    private boolean rare;
    private double balanceFrom = Double.NaN;
    private long balanceAt = -10000;
    private long deniedAt = -10000;
    private Press press;
    private double drum;
    private boolean drumDigits;
    private int spinSlot = -1;
    private double spinFrom;
    private double spinTo;
    private float gauge = .05f;
    private long joltAt = -10000;
    private float joltPower;
    private long kickAt = -10000;
    private float kickPower;
    private long flashAt = -10000;
    private boolean hoverLocked;
    private int left;
    private int top;
    private Area bay = new Area(0, 0, 0, 0);
    private Area window = new Area(0, 0, 0, 0);
    private Area sign = new Area(0, 0, 0, 0);
    private Area lcd = new Area(0, 0, 0, 0);
    private Area balanceArea = new Area(0, 0, 0, 0);
    private Area prevArea = new Area(0, 0, 0, 0);
    private Area nextArea = new Area(0, 0, 0, 0);
    private Area backArea = new Area(0, 0, 0, 0);
    private Area submitArea = new Area(0, 0, 0, 0);

    public PlateShopScreen(PlateShopClient.Menu menu) {
        super(Component.translatable("gui.slimvehicle.plates.title"));
        this.menu = menu;
        style = menu.style();
        template = PlateNumber.POLICE.equals(style) ? "A0001" : "A001AA";
        slots = new Slot[PlateNumber.bodyLength(style)];
        boolean numbered = PlateNumber.valid(menu.plate());
        for (int i = 0; i < slots.length; i++) slots[i] = new Slot(!PlateNumber.letterSlot(style, i), numbered ? menu.plate().charAt(i) : 0);
        region = numbered ? PlateNumber.region(menu.plate()) : PlateRegions.DEFAULT;
        platePreview = menu.plate();
        status = shownResult(menu);
        statusOk = menu.ok();
        balance = money(menu);
    }

    @Override
    protected void layout() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        bay = new Area(left + 170, top + 48, 444, 207);
        window = new Area(left + 339, top + 90, 106, 32);
        sign = new Area(left + 262, top + 51, 260, 25);
        lcd = new Area(left + 42, top + 64, 84, 42);
        balanceArea = new Area(left + 452, top + 7, 164, 30);
        prevArea = new Area(left + 16, top + 64, 22, 42);
        nextArea = new Area(left + 130, top + 64, 22, 42);
        backArea = new Area(left + 16, top + 302, 136, 30);
        submitArea = new Area(left + 498, top + 264, 114, 70);
        for (int i = 0; i < modes.length; i++) modes[i] = new Area(left + 172 + i * 108, top + 264, 104, 70);
        chips.clear();
        placeChips(PlateRegions.MOSCOW, top + 124);
        placeChips(PlateRegions.OBLAST, top + 238);
    }

    private void placeChips(List<String> codes, int y) {
        for (int i = 0; i < codes.size(); i++) {
            chips.add(new RegionChip(codes.get(i), new Area(left + 16 + i % CHIPS_IN_ROW * CHIP_STEP_X, y + i / CHIPS_IN_ROW * CHIP_STEP_Y, CHIP_W, CHIP_H)));
        }
    }

    private static float plateScale() {
        return PLATE_W / (float) PlateTextures.WIDTH;
    }

    private float shiftFor(int slot) {
        return PRESS_X - PLATE_X - (PlateTextures.slots(style)[slot] + PlateTextures.CELL_WIDTH / 2f) * plateScale();
    }

    public void accept(PlateShopClient.Menu next) {
        menu = next;
        if (!next.hasCar()) {
            onClose();
            return;
        }
        if (!spinning) {
            status = shownResult(next);
            statusOk = next.ok();
            balance = money(next);
            platePreview = next.plate();
            setShown(next.plate());
            return;
        }
        answered = true;
        if (!next.ok()) {
            for (Slot slot : slots) slot.rolled = false;
            finish();
            return;
        }
        List<Integer> rolled = new ArrayList<>();
        String plate = next.plate();
        for (int i = 0; i < slots.length; i++) {
            if (!slots[i].rolled) continue;
            slots[i].target = plate.charAt(i);
            rolled.add(i);
        }
        if (rolled.isEmpty()) {
            finish();
            return;
        }
        press = new Press(rolled.stream().mapToInt(Integer::intValue).toArray(), Math.max(Util.getMillis(), requestedAt + WIPE_MS));
        for (long start : press.move) queued.add(new Queued(start, SoundEvents.PISTON_CONTRACT, .9f, .16f));
        for (long strike : press.strike) queued.add(new Queued(strike - SLAM_MS, SoundEvents.PISTON_EXTEND, .55f, .3f));
    }

    private void setShown(String plate) {
        boolean numbered = PlateNumber.valid(plate);
        for (int i = 0; i < slots.length; i++) slots[i].shown = numbered ? plate.charAt(i) : 0;
    }

    private boolean spun(int action, int slot) {
        boolean letter = PlateNumber.letterSlot(style, slot);
        return action == 1 || action == 2 && letter || action == 3 && !letter;
    }

    private void submit() {
        if (spinning || !allowed(selected)) return;
        if (!affordable(selected)) {
            status = "";
            deniedAt = Util.getMillis();
            refusal();
            return;
        }
        spinning = true;
        answered = false;
        spinAction = selected;
        requestedAt = Util.getMillis();
        landedCount = 0;
        spinSlot = -1;
        status = "";
        for (int i = 0; i < slots.length; i++) slots[i].rolled = spun(selected, i);
        play(SoundEvents.PISTON_CONTRACT, .7f, .4f);
        later(90, SoundEvents.DISPENSER_DISPENSE, .8f, .25f);
        PlateShopClient.request(selected, region);
    }

    private void updatePress(long now) {
        if (press == null) return;
        while (press.nextLock < press.slots.length && now >= press.locked[press.nextLock]) lock(press.nextLock++, now);
        while (press.nextStrike < press.slots.length && now >= press.strike[press.nextStrike]) strike(press.nextStrike++, now);
        if (now >= press.end()) {
            press = null;
            finish();
        }
    }

    private void lock(int k, long now) {
        if (spinSlot == k) moveDrum(spinTo, now);
        kick(now, .5f);
        play(SoundEvents.IRON_DOOR_CLOSE, 1.7f, .22f);
    }

    private void strike(int k, long now) {
        boolean held = press.held(k);
        Slot slot = slots[press.slots[k]];
        slot.shown = slot.target;
        slot.rolled = false;
        slot.alpha = 1;
        slot.struckAt = now;
        joltAt = now;
        joltPower = held ? 3.5f : 2;
        kick(now, held ? 3.4f : 1.7f);
        flashAt = now;
        float x = left + PRESS_X;
        float y = top + RAM_CONTACT;
        burst(x - RAM_W / 2f, x + RAM_W / 2f, y, held ? 30 : 16);
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 3; i++) {
                puffs.add(new Puff(x + side * 32, y - 30 - i * 14, side * (14 + random.nextFloat() * 22), -20 - random.nextFloat() * 18, now + DWELL_MS + i * 30L, 420 + random.nextInt(200)));
            }
        }
        play(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, .75f, .42f);
        play(SoundEvents.ANVIL_LAND, held ? .5f : .7f, held ? .4f : .24f);
        later(DWELL_MS, SoundEvents.FIRE_EXTINGUISH, 1.7f, .1f);
        int note = SCALE[Math.min(landedCount, SCALE.length - 1)];
        play(SoundEvents.NOTE_BLOCK_CHIME.value(), (float) Math.pow(2, (note - 6) / 12.0), .35f);
        landedCount++;
    }

    private void kick(long now, float power) {
        kickAt = now;
        kickPower = power;
    }

    private void burst(float x0, float x1, float y, int count) {
        long now = Util.getMillis();
        for (int i = 0; i < count; i++) {
            boolean right = random.nextBoolean();
            float vx = (right ? 1 : -1) * (40 + random.nextFloat() * 130);
            float vy = -(30 + random.nextFloat() * 150);
            sparks.add(new Spark(right ? x1 : x0, y, vx, vy, now, 200 + random.nextInt(260)));
        }
    }

    private void finish() {
        spinning = false;
        finishedAt = Util.getMillis();
        platePreview = menu.plate();
        status = shownResult(menu);
        statusOk = menu.ok();
        if (menu.ok() && !menu.free() && menu.prices()[spinAction - 1] > 0) {
            paid = "-" + money(menu.prices()[spinAction - 1]);
            paidAt = finishedAt;
            balanceFrom = menu.balanceValue() + menu.prices()[spinAction - 1];
            balanceAt = finishedAt;
        }
        balance = money(menu);
        if (!menu.numbered()) {
            for (Slot slot : slots) slot.shown = 0;
        }
        if (menu.numbered()) region = PlateNumber.region(menu.plate());
        rare = menu.ok() && rare(menu.plate());
        if (menu.ok()) {
            play(SoundEvents.ANVIL_USE, 1.5f, .3f);
            for (int i = 0; i < CHORD.length; i++) later(70 + i * 70L, SoundEvents.NOTE_BLOCK_PLING.value(), CHORD[i], .35f);
            later(360, SoundEvents.PLAYER_LEVELUP, 1.6f, .18f);
            if (rare) later(260, SoundEvents.BEACON_POWER_SELECT, 1.3f, .5f);
        } else {
            refusal();
        }
    }

    private void refusal() {
        play(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), .7f, .6f);
        later(130, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), .53f, .6f);
    }

    private void later(long delay, SoundEvent sound, float pitch, float volume) {
        queued.add(new Queued(Util.getMillis() + delay, sound, pitch, volume));
    }

    private static boolean rare(String plate) {
        if (!PlateNumber.valid(plate)) return false;
        String body = PlateNumber.body(plate);
        StringBuilder digits = new StringBuilder();
        StringBuilder letters = new StringBuilder();
        for (char character : body.toCharArray()) (Character.isDigit(character) ? digits : letters).append(character);
        boolean sameDigits = digits.chars().distinct().count() == 1;
        boolean sameLetters = letters.length() > 1 && letters.chars().distinct().count() == 1;
        String number = digits.toString();
        return sameDigits || sameLetters || number.equals("001") || number.equals("007") || number.equals("123");
    }

    private void tickSound(long now) {
        if (now - lastTick < TICK_GAP_MS) return;
        lastTick = now;
        play(SoundEvents.NOTE_BLOCK_HAT.value(), .9f + random.nextFloat() * .2f, .1f);
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private static String shownResult(PlateShopClient.Menu menu) {
        return menu.ok() ? "" : menu.result();
    }

    private static String money(PlateShopClient.Menu menu) {
        return Double.isNaN(menu.balanceValue()) ? menu.balance() : money(menu.balanceValue());
    }

    private static String money(double amount) {
        return GarageUi.number((long) Math.floor(amount + 1e-6)) + "$";
    }

    private boolean affordable(int action) {
        double value = menu.balanceValue();
        return menu.free() || Double.isNaN(value) || value >= menu.prices()[action - 1];
    }

    private boolean allowed(int action) {
        return action == 1 || PlateNumber.valid(platePreview);
    }

    private void choose(int action) {
        if (spinning || !allowed(action) || action == selected) return;
        selected = action;
        status = "";
        play(SoundEvents.STONE_BUTTON_CLICK_ON, 1.5f, .25f);
    }

    private void chooseRegion(String code) {
        if (spinning || code.equals(region)) return;
        region = code;
        status = "";
        play(SoundEvents.STONE_BUTTON_CLICK_ON, 1.3f, .25f);
    }

    private void stepRegion(int direction) {
        int index = 0;
        for (int i = 0; i < chips.size(); i++) if (chips.get(i).code.equals(region)) index = i;
        chooseRegion(chips.get(Math.floorMod(index + direction, chips.size())).code);
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) onClose();
        if (spinning && !answered && Util.getMillis() - requestedAt > ANSWER_TIMEOUT_MS) {
            accept(new PlateShopClient.Menu("gui.slimvehicle.plates.no_answer", false, menu.hasCar(), menu.style(), menu.plate(), menu.free(), menu.balance(), menu.balanceValue(),
                    menu.prices(), menu.priceTexts()));
        }
    }

    private void moveDrum(double to, long now) {
        if (Math.floor(to) != Math.floor(drum)) tickSound(now);
        drum = to;
    }

    private void beginSpin(int k) {
        spinSlot = k;
        Slot slot = slots[press.slots[k]];
        drumDigits = slot.digit;
        String set = drumDigits ? PlateNumber.DIGITS : PlateNumber.LETTERS;
        int index = Math.max(0, set.indexOf(slot.target));
        spinFrom = drum;
        double floor = Math.ceil(spinFrom + MIN_DRUM_STEPS);
        spinTo = floor + Math.floorMod(index - (int) Math.floorMod((long) floor, set.length()), set.length());
    }

    private void updateDrum(long now, double seconds) {
        if (press == null) {
            if (spinning) moveDrum(drum + seconds * IDLE_DRUM_SPEED, now);
            return;
        }
        int k = press.spinning(now);
        if (k < 0) return;
        if (k != spinSlot) beginSpin(k);
        float u = Mth.clamp((now - press.move[k]) / (float) (press.locked[k] - press.move[k]), 0, 1);
        float rest = 1 - u;
        moveDrum(spinFrom + (spinTo - spinFrom) * (1 - rest * rest * rest), now);
    }

    @Override
    protected void draw(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        long now = Util.getMillis();
        double seconds = Math.min(.1, Math.max(0, (now - lastFrame) / 1000.0));
        lastFrame = now;
        updateDrum(now, seconds);
        updatePress(now);
        for (Slot slot : slots) slot.alpha = Mth.clamp(slot.alpha + (float) seconds * (slot.rolled && slot.shown != 0 ? -6 : 6), 0, 1);
        queued.removeIf(sound -> {
            if (sound.at() > now) return false;
            play(sound.sound(), sound.pitch(), sound.volume());
            return true;
        });
        float extension = ram(now);
        float pressure = press == null ? .05f : extension > 0 ? .4f + .6f * extension : .14f;
        gauge += (pressure - gauge) * (1 - (float) Math.exp(-seconds * (pressure > gauge ? 16 : 5)));
        gui.fill(0, 0, width, height, 0xE6080A0D);
        float since = now - kickAt;
        float decay = since < KICK_MS ? kickPower * (float) Math.exp(-since / 70) : 0;
        float shakeX = decay * Mth.sin(since * .09f);
        float shakeY = decay * Mth.cos(since * .11f);
        gui.pose().pushPose();
        gui.pose().translate(shakeX, shakeY, 0);
        MACHINE.draw(gui, left, top, 0, 0);
        header(gui, now);
        regions(gui, mouseX, mouseY);
        bayContent(gui, now, extension, shakeX, shakeY);
        modeKeys(gui, mouseX, mouseY);
        goKey(gui, mouseX, mouseY, now);
        gui.pose().popPose();
    }

    private void header(GuiGraphics gui, long now) {
        Component mark = Component.literal("М").withStyle(ChatFormatting.BOLD);
        gui.drawString(font, mark, left + 25 - font.width(mark) / 2, top + 15, GarageUi.AMBER, false);
        GarageUi.bigText(gui, font, Component.translatable("gui.slimvehicle.plates.title").withStyle(ChatFormatting.BOLD), left + 44, top + 7, 2, GarageUi.WHITE);
        gui.drawString(font, Component.translatable("gui.slimvehicle.plates.subtitle"), left + 44, top + 26, GarageUi.MUTED, false);
        balanceBox(gui, now);
    }

    private static void tint(GuiGraphics gui, int rgb, float alpha) {
        gui.setColor((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, alpha);
    }

    private static void digit(GuiGraphics gui, int value, float x, float y, float size, int color, float alpha) {
        tint(gui, color, alpha);
        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);
        gui.pose().scale(size / SEGMENTS.h, size / SEGMENTS.h, 1);
        SEGMENTS.draw(gui, 0, 0, value, 0);
        gui.pose().popPose();
        gui.setColor(1, 1, 1, 1);
    }

    private static float digitsWidth(int cells, int group, float size) {
        float advance = size * DIGIT_ADVANCE;
        return cells * advance + (group > 0 ? (cells - 1) / group * advance * DIGIT_GAP : 0);
    }

    private static void digits(GuiGraphics gui, String text, float right, float y, float size, int cells, int group, int color) {
        float advance = size * DIGIT_ADVANCE;
        float x = right;
        for (int cell = 0; cell < cells; cell++) {
            x -= advance;
            digit(gui, 8, x, y, size, color, GHOST_ALPHA);
            if (cell < text.length()) digit(gui, text.charAt(text.length() - 1 - cell) - '0', x, y, size, color, 1);
            if (group > 0 && (cell + 1) % group == 0) x -= advance * DIGIT_GAP;
        }
    }

    private void balanceBox(GuiGraphics gui, long now) {
        double value = menu.balanceValue();
        int right = balanceArea.right() - 8;
        if (Double.isNaN(value)) {
            digits(gui, "", right, balanceArea.y() + 3, BALANCE_DIGIT, BALANCE_CELLS, 3, GarageUi.AMBER_BRIGHT);
            if (balance != null) gui.drawString(font, balance, right - font.width(balance), balanceArea.y() + 11, GarageUi.AMBER_BRIGHT, false);
            return;
        }
        double shownValue = value;
        int color = GarageUi.AMBER_BRIGHT;
        float counting = (now - balanceAt) / (float) COUNT_MS;
        if (!Double.isNaN(balanceFrom) && counting < 1) {
            float eased = 1 - (1 - counting) * (1 - counting) * (1 - counting);
            shownValue = balanceFrom + (value - balanceFrom) * eased;
            color = GarageUi.mix(GarageUi.RED, GarageUi.AMBER_BRIGHT, counting);
        }
        String text = Long.toString((long) Math.floor(shownValue + 1e-6));
        int cells = Math.max(BALANCE_CELLS, text.length());
        Component dollar = Component.literal("$").withStyle(ChatFormatting.BOLD);
        int numbersRight = right - font.width(dollar) - 3;
        float available = numbersRight - balanceArea.x() - 8;
        float size = Math.min(BALANCE_DIGIT, BALANCE_DIGIT * available / digitsWidth(cells, 3, BALANCE_DIGIT));
        digits(gui, text, numbersRight, balanceArea.y() + (balanceArea.h() - size) / 2, size, cells, 3, color);
        gui.drawString(font, dollar, numbersRight + 3, balanceArea.y() + 11, color, false);
        Component label = Component.translatable("gui.slimvehicle.plates.balance_label");
        if (numbersRight - digitsWidth(cells, 3, size) - balanceArea.x() >= font.width(label) + 12) gui.drawString(font, label, balanceArea.x() + 8, balanceArea.y() + 11, GarageUi.MUTED, false);
        long since = now - paidAt;
        if (paid == null || since > PAID_MS) return;
        float t = since / (float) PAID_MS;
        int alpha = Math.max(4, (int) (255 * (1 - t * t)));
        gui.drawString(font, paid, balanceArea.x() - font.width(paid) - 8, balanceArea.y() + 11 - Math.round(10 * t), alpha << 24 | GarageUi.RED & 0xFFFFFF, false);
    }

    private void regions(GuiGraphics gui, int mouseX, int mouseY) {
        GarageUi.title(gui, font, "gui.slimvehicle.plates.region", left + 16, top + 52, 136);
        float block = digitsWidth(REGION_CELLS, 0, REGION_DIGIT);
        digits(gui, region, lcd.x() + (lcd.w() + block) / 2, lcd.y() + (lcd.h() - REGION_DIGIT) / 2, REGION_DIGIT, REGION_CELLS, 0, GarageUi.AMBER_BRIGHT);
        KEY_PREV.draw(gui, prevArea.x(), prevArea.y(), 0, spinning ? 2 : prevArea.inside(mouseX, mouseY) ? 1 : 0);
        KEY_NEXT.draw(gui, nextArea.x(), nextArea.y(), 0, spinning ? 2 : nextArea.inside(mouseX, mouseY) ? 1 : 0);
        gui.drawString(font, Component.translatable("gui.slimvehicle.plates.moscow"), left + 16, top + 112, GarageUi.MUTED, false);
        gui.drawString(font, Component.translatable("gui.slimvehicle.plates.oblast"), left + 16, top + 226, GarageUi.MUTED, false);
        for (RegionChip chip : chips) {
            boolean active = chip.code.equals(region);
            int state = active ? KEY_ACTIVE : spinning ? KEY_OFF : chip.area.inside(mouseX, mouseY) ? KEY_HOVER : KEY_NORMAL;
            KEY_REGION.draw(gui, chip.area.x(), chip.area.y(), 0, state);
            int color = active ? GarageUi.ON_AMBER : spinning ? GarageUi.FAINT : GarageUi.WHITE;
            Component text = Component.literal(chip.code).withStyle(ChatFormatting.BOLD);
            gui.drawString(font, text, chip.area.x() + (chip.area.w() - font.width(text) + 1) / 2, chip.area.y() + 6, color, false);
        }
        boolean hover = backArea.inside(mouseX, mouseY);
        KEY_BACK.draw(gui, backArea.x(), backArea.y(), 0, hover ? 1 : 0);
        Component back = Component.translatable("gui.slimvehicle.plates.back").withStyle(ChatFormatting.BOLD);
        gui.drawString(font, back, backArea.x() + (backArea.w() - font.width(back)) / 2, backArea.y() + 10, GarageUi.WHITE, false);
        gui.drawString(font, "Esc", backArea.right() - font.width("Esc") - 8, backArea.y() + 10, GarageUi.MUTED, false);
    }

    private static float heavy(float u) {
        return u < .5f ? 4 * u * u * u : 1 - (float) Math.pow(-2 * u + 2, 3) / 2;
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    private float plateShift(long now) {
        if (press == null) return 0;
        float from = 0;
        for (int k = 0; k < press.slots.length; k++) {
            float to = shiftFor(press.slots[k]);
            if (now < press.move[k]) return from;
            long travel = press.locked[k] - LOCK_MS - press.move[k];
            if (now < press.move[k] + travel) return Mth.lerp(heavy((now - press.move[k]) / (float) travel), from, to);
            from = to;
        }
        return Mth.lerp(heavy(Mth.clamp((now - press.homeAt()) / (float) RETURN_MS, 0, 1)), from, 0);
    }

    private float ram(long now) {
        if (press == null) return 0;
        for (int k = press.slots.length - 1; k >= 0; k--) {
            long armed = press.locked[k];
            if (now < armed) continue;
            long strike = press.strike[k];
            long slam = strike - SLAM_MS;
            float pre = press.held(k) ? HOLD_EXTENSION : PRE_EXTENSION;
            if (now < slam) return pre * smooth((now - armed) / (float) (slam - armed));
            if (now < strike) {
                float u = (now - slam) / (float) SLAM_MS;
                return Mth.lerp(u * u, pre, 1);
            }
            if (now < strike + DWELL_MS) return 1;
            float rest = 1 - Mth.clamp((now - strike - DWELL_MS) / (float) RETRACT_MS, 0, 1);
            return rest * rest;
        }
        return 0;
    }

    private float ramJitter(long now) {
        if (press == null) return 0;
        int last = press.slots.length - 1;
        if (!press.held(last)) return 0;
        long armed = press.locked[last];
        long slam = press.strike[last] - SLAM_MS;
        if (now < armed || now >= slam) return 0;
        float hold = (now - armed) / (float) (slam - armed);
        return hold * hold * Mth.sin(now * .09f) * 1.3f;
    }

    private float jolt(long now) {
        long since = now - joltAt;
        if (since > JOLT_MS) return 0;
        return joltPower * (float) Math.exp(-since / 55.0) * Mth.cos(since * .11f);
    }

    private void bayContent(GuiGraphics gui, long now, float extension, float shakeX, float shakeY) {
        scissor(gui, bay.x() + shakeX, bay.y() + shakeY, bay.right() + shakeX, bay.bottom() + shakeY);
        plateAssembly(gui, now, plateShift(now));
        float ramBottom = top + Mth.lerp(extension, RAM_REST, RAM_CONTACT);
        RAM.draw(gui, left + PRESS_X - RAM_W / 2f + ramJitter(now), ramBottom - RAM_H, 0, 0);
        effects(gui, now);
        gui.disableScissor();
        drumWindow(gui, shakeX, shakeY);
        PRESS_TOP.draw(gui, left + 168, top + 46, 0, 0);
        needle(gui);
        signLine(gui, now);
    }

    private void plateAssembly(GuiGraphics gui, long now, float shift) {
        float scale = plateScale();
        float x = left + PLATE_X + shift;
        float y = top + PLATE_Y + jolt(now);
        SLED.draw(gui, x - 12, y - 8, 0, 0);
        whole(gui, PlateTextures.base(style), x, y, scale);
        int[] positions = PlateTextures.slots(style);
        int ink = PlateTextures.ink(style);
        float slotY = y + PlateTextures.SLOT_Y * scale;
        if (!menu.numbered() && !spinning) {
            for (int i = 0; i < slots.length; i++) glyph(gui, template.charAt(i), x + positions[i] * scale, slotY, scale, ink, .13f);
        }
        float regionScale = scale * PlateTextures.REGION_SCALE;
        for (int i = 0; i < region.length(); i++) glyph(gui, region.charAt(i), x + PlateTextures.regionX(region, i) * scale, y + PlateTextures.REGION_Y * scale, regionScale, ink, 1);
        for (int i = 0; i < slots.length; i++) {
            Slot slot = slots[i];
            if (slot.shown == 0 || slot.alpha <= 0) continue;
            float slotX = x + positions[i] * scale;
            glyph(gui, slot.shown, slotX, slotY, scale, ink, slot.alpha);
            float heat = (now - slot.struckAt) / (float) HEAT_MS;
            if (heat >= 0 && heat < 1) glyph(gui, slot.shown, slotX, slotY, scale, GarageUi.AMBER & 0xFFFFFF, (1 - heat) * .9f);
        }
        SLED_TOP.draw(gui, x - 12, y - 8, 0, 0);
        long sinceReveal = now - finishedAt;
        if (statusOk && sinceReveal < REVEAL_MS) reveal(gui, x, y, PLATE_W, PlateTextures.HEIGHT * scale, sinceReveal / (float) REVEAL_MS);
    }

    private void effects(GuiGraphics gui, long now) {
        float flash = (now - flashAt) / (float) FLASH_MS;
        if (flash >= 0 && flash < 1) {
            float size = 90 + 70 * flash;
            tint(gui, GarageUi.AMBER_BRIGHT & 0xFFFFFF, (1 - flash) * .85f);
            gui.pose().pushPose();
            gui.pose().translate(left + PRESS_X, top + RAM_CONTACT - 6, 0);
            gui.pose().scale(size / PUFF.w, size / PUFF.h * .55f, 1);
            PUFF.draw(gui, -PUFF.w / 2f, -PUFF.h / 2f, 0, 0);
            gui.pose().popPose();
            gui.setColor(1, 1, 1, 1);
        }
        puffs.removeIf(puff -> now - puff.born > puff.life);
        for (Puff puff : puffs) {
            if (now < puff.born) continue;
            float seconds = (now - puff.born) / 1000f;
            float life = (now - puff.born) / (float) puff.life;
            float size = 14 + 34 * life;
            tint(gui, 0xDDE3EA, (1 - life) * .32f);
            gui.pose().pushPose();
            gui.pose().translate(puff.x + puff.vx * seconds, puff.y + puff.vy * seconds, 0);
            gui.pose().scale(size / PUFF.w, size / PUFF.h, 1);
            PUFF.draw(gui, -PUFF.w / 2f, -PUFF.h / 2f, 0, 0);
            gui.pose().popPose();
            gui.setColor(1, 1, 1, 1);
        }
        sparks.removeIf(spark -> now - spark.born > spark.life);
        gui.pose().pushPose();
        gui.pose().scale(1f / UNIT, 1f / UNIT, 1);
        for (Spark spark : sparks) {
            float seconds = (now - spark.born) / 1000f;
            float fade = 1 - (now - spark.born) / (float) spark.life;
            int color = Math.round(255 * Math.min(1, fade * 2)) << 24 | GarageUi.mix(0xFFFF7A2A, 0xFFFFE6A8, fade) & 0xFFFFFF;
            for (int trail = 0; trail < 4; trail++) {
                float at = Math.max(0, seconds - trail * .006f);
                int x = Mth.floor((spark.x + spark.vx * at) * UNIT);
                int y = Mth.floor((spark.y + spark.vy * at + 320 * at * at) * UNIT);
                gui.fill(x, y, x + 2, y + 2, color);
            }
        }
        gui.pose().popPose();
    }

    private void drumWindow(GuiGraphics gui, float shakeX, float shakeY) {
        gui.fill(window.x(), window.y(), window.right(), window.bottom(), 0xFF07090C);
        scissor(gui, window.x() + shakeX, window.y() + shakeY, window.right() + shakeX, window.bottom() + shakeY);
        String set = drumDigits ? PlateNumber.DIGITS : PlateNumber.LETTERS;
        int base = Mth.floor(drum);
        for (int d = 3; d >= 0; d--) {
            int nearSide = base - d;
            int farSide = base + 1 + d;
            boolean nearFirst = Math.abs(nearSide - drum) > Math.abs(farSide - drum);
            drumFace(gui, set, nearFirst ? nearSide : farSide);
            drumFace(gui, set, nearFirst ? farSide : nearSide);
        }
        gui.disableScissor();
    }

    private void drumFace(GuiGraphics gui, String set, int index) {
        double step = 2 * Math.PI / set.length();
        double angle = (index - drum) * step;
        float cos = (float) Math.cos(angle);
        if (cos <= .03f) return;
        float radius = FACE_W / 2f / (float) Math.tan(step / 2);
        float cx = window.x() + window.w() / 2f + radius * (float) Math.sin(angle);
        float cy = window.y() + window.h() / 2f;
        float light = .22f + .78f * cos;
        gui.setColor(light, light, light, 1);
        gui.pose().pushPose();
        gui.pose().translate(cx, cy, 0);
        gui.pose().scale(cos, 1, 1);
        FACE.draw(gui, -FACE_W / 2f, -FACE_H / 2f, 0, 0);
        gui.setColor(1, 1, 1, 1);
        char character = set.charAt(Math.floorMod(index, set.length()));
        int ink = Math.round(0x16 * light) << 16 | Math.round(0x1A * light) << 8 | Math.round(0x20 * light);
        glyph(gui, character, -PlateTextures.CELL_WIDTH * GLYPH_ON_FACE / 2, -PlateTextures.CELL_HEIGHT * GLYPH_ON_FACE / 2, GLYPH_ON_FACE, ink, 1);
        gui.pose().popPose();
    }

    private void needle(GuiGraphics gui) {
        gui.pose().pushPose();
        gui.pose().translate(left + GAUGE_X, top + GAUGE_Y, 0);
        gui.pose().mulPose(Axis.ZP.rotationDegrees(-GAUGE_SWEEP + 2 * GAUGE_SWEEP * Mth.clamp(gauge, 0, 1)));
        NEEDLE.draw(gui, -NEEDLE_PIVOT_X, -NEEDLE_PIVOT_Y, 0, 0);
        gui.pose().popPose();
    }

    private Line currentLine(long now) {
        if (spinning) return new Line(Component.translatable("gui.slimvehicle.plates.spinning").append(".".repeat((int) (now / 300 % 4))), GarageUi.AMBER_BRIGHT);
        if (hoverLocked) return new Line(Component.translatable("gui.slimvehicle.plates.need_full"), GarageUi.AMBER_BRIGHT);
        if (!status.isEmpty()) return new Line(Component.translatable(status), statusOk ? GarageUi.GREEN : GarageUi.RED);
        if (allowed(selected) && !affordable(selected)) return new Line(Component.translatable("gui.slimvehicle.plates.short", money(menu.prices()[selected - 1] - menu.balanceValue())), GarageUi.RED);
        if (menu.numbered()) return new Line(Component.translatable("gui.slimvehicle.plates.current", PlateNumber.display(menu.plate())), GarageUi.SOFT);
        return new Line(Component.translatable("gui.slimvehicle.plates.no_plate"), GarageUi.AMBER_BRIGHT);
    }

    private void signLine(GuiGraphics gui, long now) {
        int lamp = LAMP_GREEN;
        float glow = .55f;
        if (spinning) {
            lamp = LAMP_AMBER;
            glow = .4f + .6f * Math.abs(Mth.sin(now / 140f));
        } else if (!status.isEmpty() && !statusOk) {
            lamp = LAMP_RED;
            glow = 1;
        } else if (statusOk && now - finishedAt < DONE_MS) {
            glow = 1;
        }
        LAMPS.draw(gui, sign.x() + 5, sign.y() + 4.5f, 0, 0);
        gui.setColor(1, 1, 1, glow);
        LAMPS.draw(gui, sign.x() + 5, sign.y() + 4.5f, lamp, 0);
        gui.setColor(1, 1, 1, 1);
        Line line = currentLine(now);
        int textLeft = sign.x() + 26;
        int room = sign.right() - 8 - textLeft;
        int textWidth = font.width(line.text);
        float scale = Math.min(1, room / (float) textWidth);
        gui.pose().pushPose();
        gui.pose().translate(textLeft + (room - textWidth * scale) / 2, sign.y() + (sign.h() - 8 * scale) / 2, 0);
        gui.pose().scale(scale, scale, 1);
        gui.drawString(font, line.text, 0, 0, line.color, false);
        gui.pose().popPose();
        GarageUi.scanlines(gui, sign);
    }

    private void reveal(GuiGraphics gui, float x, float y, float w, float h, float t) {
        int colour = (rare ? GOLD : GarageUi.AMBER) & 0xFFFFFF;
        brackets(gui, x, y, w, h, 3 + 5 * (1 - t), (int) (255 * (1 - t * t)) << 24 | colour);
        float sweep = t / .7f;
        if (sweep >= 1) return;
        int x0 = Mth.floor(x);
        int x1 = Mth.ceil(x + w);
        int y0 = Mth.floor(y);
        int y1 = Mth.ceil(y + h);
        int line = y0 + Math.round((y1 - y0 + SCAN_TRAIL) * sweep);
        for (int row = 0; row < SCAN_TRAIL; row++) {
            int at = line - row;
            if (at < y0 || at >= y1) continue;
            int alpha = row == 0 ? 0xE0 : (int) (0x58 * (1 - row / (float) SCAN_TRAIL));
            gui.fill(x0, at, x1, at + 1, alpha << 24 | colour);
        }
    }

    private static void brackets(GuiGraphics gui, float x, float y, float w, float h, float grow, int argb) {
        int x0 = Mth.floor(x - grow);
        int y0 = Mth.floor(y - grow);
        int x1 = Mth.ceil(x + w + grow);
        int y1 = Mth.ceil(y + h + grow);
        int arm = Math.max(3, Math.round(Math.min(w, h) * .22f));
        gui.fill(x0, y0, x0 + arm, y0 + 1, argb);
        gui.fill(x0, y0 + 1, x0 + 1, y0 + arm, argb);
        gui.fill(x1 - arm, y0, x1, y0 + 1, argb);
        gui.fill(x1 - 1, y0 + 1, x1, y0 + arm, argb);
        gui.fill(x0, y1 - 1, x0 + arm, y1, argb);
        gui.fill(x0, y1 - arm, x0 + 1, y1 - 1, argb);
        gui.fill(x1 - arm, y1 - 1, x1, y1, argb);
        gui.fill(x1 - 1, y1 - arm, x1, y1 - 1, argb);
    }

    private static void whole(GuiGraphics gui, ResourceLocation texture, float x, float y, float scale) {
        RenderSystem.enableBlend();
        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);
        gui.pose().scale(scale, scale, 1);
        gui.blit(texture, 0, 0, 0, 0, PlateTextures.WIDTH, PlateTextures.HEIGHT, PlateTextures.WIDTH, PlateTextures.HEIGHT);
        gui.pose().popPose();
    }

    private static void glyph(GuiGraphics gui, char character, float x, float y, float scale, int ink, float alpha) {
        if (PlateTextures.CHARACTERS.indexOf(character) < 0) return;
        RenderSystem.enableBlend();
        tint(gui, ink, alpha);
        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);
        gui.pose().scale(scale, scale, 1);
        gui.blit(PlateTextures.GLYPHS, 0, 0, PlateTextures.glyphU(character), PlateTextures.glyphV(character), PlateTextures.CELL_WIDTH, PlateTextures.CELL_HEIGHT,
                PlateTextures.ATLAS_WIDTH, PlateTextures.ATLAS_HEIGHT);
        gui.pose().popPose();
        gui.setColor(1, 1, 1, 1);
    }

    private void modeKeys(GuiGraphics gui, int mouseX, int mouseY) {
        hoverLocked = false;
        for (int i = 0; i < modes.length; i++) {
            int action = i + 1;
            Area area = modes[i];
            boolean enabled = allowed(action);
            boolean hover = !spinning && area.inside(mouseX, mouseY);
            if (hover && !enabled) hoverLocked = true;
            boolean chosen = selected == action;
            KEY_MODE.draw(gui, area.x(), area.y(), 0, !enabled ? KEY_OFF : chosen ? KEY_ACTIVE : hover ? KEY_HOVER : KEY_NORMAL);
            gui.drawString(font, Integer.toString(action), area.x() + 6, area.y() + 5, enabled ? GarageUi.MUTED : GarageUi.FAINT, false);
            Component title = Component.translatable(SPIN_KEYS[i]).withStyle(ChatFormatting.BOLD);
            gui.drawString(font, title, area.x() + (area.w() - font.width(title) + 1) / 2, area.y() + 13, enabled ? GarageUi.WHITE : GarageUi.FAINT, false);
            miniPlate(gui, area, action, enabled);
            modePrice(gui, area, i, enabled, chosen);
        }
    }

    private void miniPlate(GuiGraphics gui, Area area, int action, boolean enabled) {
        String body = PlateNumber.valid(platePreview) ? PlateNumber.body(platePreview) : template;
        int width = body.length() * MINI_CELL_W + (body.length() - 1) * MINI_GAP + 3 + MINI_REGION_W;
        int x = area.x() + (area.w() - width) / 2;
        int y = area.y() + 27;
        for (int i = 0; i < body.length(); i++) {
            boolean rolled = spun(action, i);
            gui.fill(x, y, x + MINI_CELL_W, y + MINI_CELL_H, !enabled ? 0xFF15181D : rolled ? GarageUi.AMBER : 0xFF0E1116);
            if (!rolled) {
                String text = PlateNumber.cyrillic(String.valueOf(body.charAt(i)));
                gui.drawString(font, text, x + (MINI_CELL_W - font.width(text) + 1) / 2, y + 3, enabled ? GarageUi.SOFT : GarageUi.FAINT, false);
            }
            x += MINI_CELL_W + MINI_GAP;
        }
        x += 3 - MINI_GAP;
        gui.fill(x, y, x + MINI_REGION_W, y + MINI_CELL_H, enabled ? 0xFF0E1116 : 0xFF15181D);
        gui.fill(x, y + MINI_CELL_H - 2, x + MINI_REGION_W, y + MINI_CELL_H, enabled ? GarageUi.RIM_LIGHT : GarageUi.RIM);
    }

    private void modePrice(GuiGraphics gui, Area area, int index, boolean enabled, boolean chosen) {
        int color = !enabled ? GarageUi.FAINT : !affordable(index + 1) ? GarageUi.RED : chosen ? GarageUi.AMBER_BRIGHT : GarageUi.WHITE;
        long price = menu.prices()[index];
        Component text = (price == 0 || menu.free() ? Component.translatable("gui.slimvehicle.plates.free") : Component.literal(money(price))).withStyle(ChatFormatting.BOLD);
        gui.drawString(font, text, area.x() + (area.w() - font.width(text) + 1) / 2, area.y() + 50, color, false);
    }

    private void goKey(GuiGraphics gui, int mouseX, int mouseY, long now) {
        float denied = (now - deniedAt) / (float) DENIED_MS;
        int nudge = denied < 1 ? Math.round(Mth.sin(denied * 40) * 3 * (1 - denied)) : 0;
        boolean canSubmit = !spinning && allowed(selected) && affordable(selected);
        boolean hover = canSubmit && submitArea.inside(mouseX, mouseY);
        int x = submitArea.x() + nudge;
        int y = submitArea.y();
        KEY_GO.draw(gui, x, y, 0, spinning ? KEY_ACTIVE : !canSubmit ? KEY_OFF : hover ? KEY_HOVER : KEY_NORMAL);
        int ink = canSubmit || spinning ? GarageUi.ON_AMBER : GarageUi.FAINT;
        int shift = spinning ? 1 : 0;
        Component label = Component.translatable("gui.slimvehicle.plates.submit").withStyle(ChatFormatting.BOLD);
        gui.drawString(font, label, x + (submitArea.w() - font.width(label) + 1) / 2, y + 22 + shift, ink, false);
        long price = menu.prices()[selected - 1];
        if (price > 0 && !menu.free()) {
            Component sum = Component.literal(money(price)).withStyle(ChatFormatting.BOLD);
            gui.drawString(font, sum, x + (submitArea.w() - font.width(sum) + 1) / 2, y + 36 + shift, ink, false);
        }
        if (press != null) {
            int barLeft = x + 14;
            int barRight = x + submitArea.w() - 14;
            gui.fill(barLeft, y + 52, barRight, y + 55, 0x60000000);
            gui.fill(barLeft, y + 52, barLeft + Math.round((barRight - barLeft) * progress(now)), y + 55, GarageUi.ON_AMBER);
        }
    }

    private float progress(long now) {
        return Mth.clamp((now - press.move[0]) / (float) (press.end() - press.move[0]), 0, 1);
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.clicked(mouseX, mouseY, button);
        if (backArea.inside(mouseX, mouseY)) {
            play(SoundEvents.STONE_BUTTON_CLICK_ON, 1, .4f);
            onClose();
            return true;
        }
        if (submitArea.inside(mouseX, mouseY)) {
            submit();
            return true;
        }
        if (prevArea.inside(mouseX, mouseY) || nextArea.inside(mouseX, mouseY)) {
            stepRegion(prevArea.inside(mouseX, mouseY) ? -1 : 1);
            return true;
        }
        for (RegionChip chip : chips) {
            if (chip.area.inside(mouseX, mouseY)) {
                chooseRegion(chip.code);
                return true;
            }
        }
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].inside(mouseX, mouseY) && !spinning && allowed(i + 1)) {
                choose(i + 1);
                return true;
            }
        }
        return super.clicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            submit();
            return true;
        }
        int action = key - GLFW.GLFW_KEY_0;
        if (action >= 1 && action <= 3 && !spinning && allowed(action)) {
            choose(action);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            stepRegion(key == GLFW.GLFW_KEY_LEFT ? -1 : 1);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
