package studio.merami.sliminterfacefabric.client.plates;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import studio.merami.slimcore.plates.PlateNumber;
import studio.merami.slimcore.plates.PlateRegions;
import studio.merami.sliminterfacefabric.client.render.PlateTextures;
import studio.merami.sliminterfacefabric.client.ui.BrandUi;
import studio.merami.sliminterfacefabric.client.ui.BrandUi.Box;
import studio.merami.sliminterfacefabric.client.ui.BrandUi.Icon;
import studio.merami.sliminterfacefabric.client.ui.BrandUi.Strip;
import studio.merami.sliminterfacefabric.client.ui.BrandUi.Type;
import studio.merami.sliminterfacefabric.client.ui.GarageScreen;
import studio.merami.sliminterfacefabric.client.ui.GarageUi;
import studio.merami.sliminterfacefabric.client.ui.GarageUi.Area;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class PlateShopScreen extends GarageScreen {
    private static final int W = 420;
    private static final int H = 376;
    private static final int PAD = 14;
    private static final int RIGHT_X = PAD;
    private static final int RIGHT_W = W - 2 * RIGHT_X;
    private static final int WELL_Y = 68;
    private static final int WELL_H = 100;
    private static final int PLATE_W = 300;
    private static final int PLATE_TOP = 18;
    private static final int MODE_W = 124;
    private static final int MODE_H = 56;
    private static final int MODE_STEP = 134;
    private static final int MODE_STEP_Y = 62;
    private static final int MODE_Y = 196;
    private static final int MODES_IN_ROW = 3;
    private static final int FULL = 1;
    private static final int LETTER = 4;
    private static final int DIGIT = 5;
    private static final int REGION = 6;
    private static final float REGION_SPEED = 11;
    private static final int BUTTON_W = 196;
    private static final int BUTTON_H = 30;
    private static final float GHOST_ALPHA = .13f;
    private static final float REEL_SPEED = 17;
    private static final long RAMP_MS = 220;
    private static final long SPIN_MIN_MS = 650;
    private static final long STAGGER_MS = 230;
    private static final int SETTLE_STEPS = 4;
    private static final long BOUNCE_MS = 220;
    private static final float BOUNCE = .07f;
    private static final long HEAT_MS = 380;
    private static final long FINISH_DELAY_MS = 260;
    private static final long REVEAL_MS = 900;
    private static final long COUNT_MS = 700;
    private static final long PAID_MS = 1400;
    private static final long DENIED_MS = 350;
    private static final long ANSWER_TIMEOUT_MS = 6000;
    private static final long TICK_GAP_MS = 45;
    private static final int RARE_STARS = 22;
    private static final int[] SCALE = {0, 3, 5, 7, 10, 12, 15};
    private static final float[] CHORD = {1, 1.189f, 1.498f, 1.782f};
    private static final String[] SPIN_KEYS = {"gui.slimvehicle.plates.full", "gui.slimvehicle.plates.letters", "gui.slimvehicle.plates.digits",
            "gui.slimvehicle.plates.letter", "gui.slimvehicle.plates.digit", "gui.slimvehicle.plates.region_spin"};
    private static final String[] HINT_KEYS = {"gui.slimvehicle.plates.full_hint", "gui.slimvehicle.plates.letters_hint", "gui.slimvehicle.plates.digits_hint",
            "gui.slimvehicle.plates.letter_hint", "gui.slimvehicle.plates.digit_hint", "gui.slimvehicle.plates.region_spin_hint"};

    private record Queued(long at, SoundEvent sound, float pitch, float volume) {}

    private record Star(float x, float y, float vx, float vy, float spin, long born, int life, float size) {}

    private record Line(String text, int color) {}

    private static final class Slot {
        final boolean digit;
        final int index;
        char shown;
        char start;
        char target;
        boolean rolled;
        double reel;
        double from;
        double to;
        long settleAt = -1;
        long settleStart;
        long settleEnd;
        long land = Long.MIN_VALUE;
        long landedAt = -10000;

        Slot(boolean digit, int index, char shown) {
            this.digit = digit;
            this.index = index;
            this.shown = shown;
        }

        char at(long n) {
            if (n == land) return target;
            if (n == 0 && start != 0) return start;
            if (digit) {
                int from = start == 0 ? index * 3 : PlateNumber.DIGITS.indexOf(start);
                return PlateNumber.DIGITS.charAt((int) Math.floorMod(from + n, (long) PlateNumber.DIGITS.length()));
            }
            return PlateNumber.LETTERS.charAt((int) Math.floorMod(n * 7 + index * 5L, (long) PlateNumber.LETTERS.length()));
        }
    }

    private final Random random = new Random();
    private final List<Queued> queued = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();
    private final Slot[] slots;
    private final Area[] modes = new Area[6];
    private final String style;
    private final String template;
    private int landedCount;
    private long lastLanded;
    private long lastFrame = Util.getMillis();
    private PlateShopClient.Menu menu;
    private String region;
    private String platePreview;
    private int selected = 1;
    private int pick = -1;
    private boolean regionRolling;
    private int regionDigit = -1;
    private char regionStart;
    private double regionReel;
    private long regionSettleAt = -1;
    private String regionTarget;
    private long regionLandedAt = -10000;
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
    private boolean hoverLocked;
    private int left;
    private int top;
    private Area panel = new Area(0, 0, 0, 0);
    private Area closeArea = new Area(0, 0, 0, 0);
    private Area submitArea = new Area(0, 0, 0, 0);

    public PlateShopScreen(PlateShopClient.Menu menu) {
        super(Component.translatable("gui.slimvehicle.plates.title"));
        this.menu = menu;
        style = menu.style();
        template = PlateNumber.POLICE.equals(style) ? "A0001" : "A001AA";
        slots = new Slot[PlateNumber.bodyLength(style)];
        boolean numbered = PlateNumber.valid(menu.plate());
        for (int i = 0; i < slots.length; i++) slots[i] = new Slot(!PlateNumber.letterSlot(style, i), i, numbered ? menu.plate().charAt(i) : 0);
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
        panel = new Area(left, top, W, H);
        closeArea = new Area(left + W - PAD - 22, top + 13, 22, 22);
        submitArea = new Area(left + W - PAD - BUTTON_W, top + H - 44, BUTTON_W, BUTTON_H);
        for (int i = 0; i < modes.length; i++) {
            modes[i] = new Area(left + RIGHT_X + i % MODES_IN_ROW * MODE_STEP, top + MODE_Y + i / MODES_IN_ROW * MODE_STEP_Y, MODE_W, MODE_H);
        }
    }

    private static float plateScale() {
        return PLATE_W / (float) PlateTextures.WIDTH;
    }

    private float plateX() {
        return left + RIGHT_X + (RIGHT_W - PLATE_W) / 2f;
    }

    private float plateY() {
        return top + WELL_Y + PLATE_TOP;
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
            regionRolling = false;
            finish();
            return;
        }
        String plate = next.plate();
        long base = Math.max(Util.getMillis(), requestedAt + SPIN_MIN_MS);
        int order = 0;
        for (int i = 0; i < slots.length; i++) {
            if (!slots[i].rolled) continue;
            slots[i].target = plate.charAt(i);
            slots[i].settleAt = base + order++ * STAGGER_MS;
        }
        if (regionRolling) {
            regionTarget = PlateNumber.region(plate);
            regionSettleAt = base;
            return;
        }
        if (order == 0) finish();
    }

    private void setShown(String plate) {
        boolean numbered = PlateNumber.valid(plate);
        for (int i = 0; i < slots.length; i++) slots[i].shown = numbered ? plate.charAt(i) : 0;
    }

    private boolean spun(int action, int slot) {
        boolean letter = PlateNumber.letterSlot(style, slot);
        return action == FULL || action == 2 && letter || action == 3 && !letter || (action == LETTER || action == DIGIT) && slot == pick;
    }

    private static PlateNumber.Spin spin(int action) {
        return PlateNumber.Spin.values()[action - 1];
    }

    private boolean pickable(int action, int slot) {
        return (action == LETTER || action == DIGIT) && PlateNumber.slotFits(style, spin(action), slot);
    }

    private int pickCount() {
        return slots.length + PlateRegions.DIGITS;
    }

    private void stepPick(int direction) {
        for (int step = 1; step <= pickCount(); step++) {
            int slot = Math.floorMod(pick + direction * step, pickCount());
            if (pickable(selected, slot)) {
                pickSlot(slot);
                return;
            }
        }
    }

    private void pickSlot(int slot) {
        if (spinning || slot == pick && pickable(selected, slot)) return;
        int action = slot < slots.length && PlateNumber.letterSlot(style, slot) ? LETTER : DIGIT;
        if (!allowed(action)) return;
        selected = action;
        pick = slot;
        status = "";
        play(SoundEvents.STONE_BUTTON_CLICK_ON, 1.7f, .25f);
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
        regionDigit = selected == DIGIT && pick >= slots.length ? pick - slots.length : -1;
        regionRolling = selected == REGION || !menu.numbered() || regionDigit >= 0;
        if (regionDigit >= 0) {
            region = PlateRegions.twoDigit(region);
            regionStart = region.charAt(regionDigit);
        }
        regionReel = 0;
        regionSettleAt = -1;
        landedCount = 0;
        status = "";
        for (int i = 0; i < slots.length; i++) {
            Slot slot = slots[i];
            slot.rolled = spun(selected, i);
            slot.start = slot.shown;
            slot.reel = 0;
            slot.settleAt = -1;
            slot.settleEnd = 0;
            slot.land = Long.MIN_VALUE;
        }
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.25f, .3f);
        later(60, SoundEvents.NOTE_BLOCK_BIT.value(), 1.5f, .25f);
        PlateShopClient.request(selected, pick);
    }

    private void updateReels(long now, double seconds) {
        if (!spinning) return;
        float ramp = Mth.clamp((now - requestedAt) / (float) RAMP_MS, 0, 1);
        boolean waiting = regionRolling;
        if (regionRolling) {
            if (regionSettleAt >= 0 && now >= regionSettleAt) {
                landRegion(now);
            } else {
                double next = regionReel + seconds * REGION_SPEED * ramp;
                if (Math.floor(next) != Math.floor(regionReel)) tickSound(now);
                regionReel = next;
            }
        }
        for (Slot slot : slots) {
            if (!slot.rolled) continue;
            waiting = true;
            if (slot.settleAt < 0 || now < slot.settleAt) {
                moveReel(slot, slot.reel + seconds * REEL_SPEED * ramp, now);
                continue;
            }
            if (slot.settleEnd == 0) {
                slot.from = slot.reel;
                slot.land = (long) Math.ceil(slot.reel) + SETTLE_STEPS;
                slot.to = slot.land;
                slot.settleStart = now;
                slot.settleEnd = now + Math.max(1, Math.round(3 * (slot.to - slot.from) / REEL_SPEED * 1000));
            }
            float u = Mth.clamp((now - slot.settleStart) / (float) (slot.settleEnd - slot.settleStart), 0, 1);
            float rest = 1 - u;
            moveReel(slot, slot.from + (slot.to - slot.from) * (1 - rest * rest * rest), now);
            if (u >= 1) land(slot, now);
        }
        if (answered && !waiting && now - lastLanded >= FINISH_DELAY_MS) finish();
    }

    private void moveReel(Slot slot, double to, long now) {
        if (Math.floor(to) != Math.floor(slot.reel)) tickSound(now);
        slot.reel = to;
    }

    private void land(Slot slot, long now) {
        slot.shown = slot.target;
        slot.rolled = false;
        slot.landedAt = now;
        slot.settleAt = -1;
        slot.settleEnd = 0;
        slot.land = Long.MIN_VALUE;
        lastLanded = now;
        int note = SCALE[Math.min(landedCount, SCALE.length - 1)];
        play(SoundEvents.NOTE_BLOCK_CHIME.value(), (float) Math.pow(2, (note - 6) / 12.0), .35f);
        play(SoundEvents.STONE_BUTTON_CLICK_ON, 1.8f, .12f);
        landedCount++;
    }

    private String rollingRegion() {
        if (regionDigit >= 0) {
            char[] code = region.toCharArray();
            long step = PlateNumber.DIGITS.indexOf(regionStart) + (long) Math.floor(regionReel);
            code[regionDigit] = PlateNumber.DIGITS.charAt((int) Math.floorMod(step, (long) PlateNumber.DIGITS.length()));
            return new String(code);
        }
        List<String> codes = PlateRegions.CODES;
        return codes.get((int) Math.floorMod((long) Math.floor(regionReel) * 37 + 76, (long) codes.size()));
    }

    private void landRegion(long now) {
        region = regionTarget;
        regionRolling = false;
        regionSettleAt = -1;
        regionLandedAt = now;
        lastLanded = now;
        int note = SCALE[Math.min(landedCount, SCALE.length - 1)];
        play(SoundEvents.NOTE_BLOCK_CHIME.value(), (float) Math.pow(2, (note - 6) / 12.0), .35f);
        play(SoundEvents.STONE_BUTTON_CLICK_ON, 1.8f, .12f);
        landedCount++;
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
            for (int i = 0; i < CHORD.length; i++) later(40 + i * 70L, SoundEvents.NOTE_BLOCK_PLING.value(), CHORD[i], .35f);
            later(330, SoundEvents.PLAYER_LEVELUP, 1.6f, .18f);
            if (rare) {
                later(240, SoundEvents.BEACON_POWER_SELECT, 1.3f, .5f);
                burst();
            }
        } else {
            refusal();
        }
    }

    private void burst() {
        long now = Util.getMillis();
        float cx = plateX() + PLATE_W / 2f;
        float cy = plateY() + PlateTextures.HEIGHT * plateScale() / 2;
        for (int i = 0; i < RARE_STARS; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            float speed = 60 + random.nextFloat() * 120;
            stars.add(new Star(cx + (random.nextFloat() - .5f) * PLATE_W * .8f, cy, (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed - 70,
                    (random.nextFloat() - .5f) * 540, now + random.nextInt(120), 700 + random.nextInt(500), 5 + random.nextFloat() * 5));
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
        return action == FULL || PlateNumber.valid(platePreview);
    }

    private void choose(int action) {
        if (spinning || !allowed(action) || action == selected) return;
        selected = action;
        if ((action == LETTER || action == DIGIT) && !pickable(action, pick)) {
            pick = -1;
            for (int slot = 0; slot < pickCount() && pick < 0; slot++) if (pickable(action, slot)) pick = slot;
        }
        status = "";
        play(SoundEvents.STONE_BUTTON_CLICK_ON, 1.5f, .25f);
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

    @Override
    protected void draw(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        long now = Util.getMillis();
        double seconds = Math.min(.1, Math.max(0, (now - lastFrame) / 1000.0));
        lastFrame = now;
        updateReels(now, seconds);
        queued.removeIf(sound -> {
            if (sound.at() > now) return false;
            play(sound.sound(), sound.pitch(), sound.volume());
            return true;
        });
        gui.fillGradient(0, 0, width, height, 0xDC120A0A, 0xEC0A0606);
        gui.fillGradient(0, height / 2, width, height, 0x00000000, 0x3A5A1418);
        BrandUi.box(gui, this, Box.PANEL, panel);
        BrandUi.hair(gui, this, panel);
        header(gui, now, mouseX, mouseY);
        plateArea(gui, now, mouseX, mouseY);
        modeCards(gui, mouseX, mouseY);
        footer(gui, mouseX, mouseY, now);
        effects(gui, now);
    }

    private void header(GuiGraphics gui, long now, int mouseX, int mouseY) {
        BrandUi.logo(gui, this, left + 12, top + 6, 38);
        BrandUi.text(gui, this, I18n.get("gui.slimvehicle.plates.title"), Type.TITLE, left + 56, top + 12, BrandUi.WHITE);
        BrandUi.text(gui, this, I18n.get("gui.slimvehicle.plates.subtitle"), Type.SMALL, left + 56, top + 30, BrandUi.TEXT);
        BrandUi.close(gui, this, closeArea, closeArea.inside(mouseX, mouseY));
        balanceBox(gui, now, closeArea.x() - 10, top + 13);
        BrandUi.divider(gui, this, left + PAD, left + W - PAD, top + 50, 1);
    }

    private void balanceBox(GuiGraphics gui, long now, float right, float y) {
        double value = menu.balanceValue();
        String label = I18n.get("gui.slimvehicle.plates.balance_label");
        if (Double.isNaN(value)) {
            BrandUi.balance(gui, this, label, balance == null ? "—" : balance, right, y, BrandUi.GOLD_LIGHT);
            return;
        }
        double shownValue = value;
        int color = BrandUi.GOLD_LIGHT;
        float counting = (now - balanceAt) / (float) COUNT_MS;
        if (!Double.isNaN(balanceFrom) && counting < 1) {
            float eased = 1 - (1 - counting) * (1 - counting) * (1 - counting);
            shownValue = balanceFrom + (value - balanceFrom) * eased;
            color = GarageUi.mix(BrandUi.RED_SOFT, BrandUi.GOLD_LIGHT, counting);
        }
        String text = money(shownValue);
        BrandUi.balance(gui, this, label, text, right, y, color);
        long since = now - paidAt;
        if (paid == null || since > PAID_MS) return;
        float t = since / (float) PAID_MS;
        float x = right - BrandUi.width(this, text, Type.VALUE) - 8 - BrandUi.width(this, paid, Type.LABEL);
        BrandUi.text(gui, this, paid, Type.LABEL, x, y + 12 - 10 * t, BrandUi.alpha(BrandUi.RED_SOFT, 1 - t * t));
    }

    private void plateArea(GuiGraphics gui, long now, int mouseX, int mouseY) {
        int x0 = left + RIGHT_X;
        BrandUi.box(gui, this, Box.WELL, x0, top + WELL_Y, RIGHT_W, WELL_H, 1);
        plate(gui, now);
        picks(gui, mouseX, mouseY);
        Line line = currentLine(now);
        if (line == null) return;
        String text = BrandUi.fit(this, line.text, Type.BODY, RIGHT_W);
        BrandUi.centred(gui, this, text, Type.BODY, x0 + RIGHT_W / 2f, top + WELL_Y + WELL_H + 15, line.color);
    }

    private void plate(GuiGraphics gui, long now) {
        float scale = plateScale();
        float x = plateX();
        float y = plateY();
        gui.fill(Math.round(x) + 2, Math.round(y) + 4, Math.round(x + PLATE_W) - 2, Math.round(y + PlateTextures.HEIGHT * scale) + 3, 0x50000000);
        whole(gui, PlateTextures.base(style), x, y, scale);
        int[] positions = PlateTextures.slots(style);
        int ink = PlateTextures.ink(style);
        float slotY = y + PlateTextures.SLOT_Y * scale;
        float cellW = PlateTextures.CELL_WIDTH * scale;
        float cellH = PlateTextures.CELL_HEIGHT * scale;
        if (!menu.numbered() && !spinning) {
            for (int i = 0; i < slots.length; i++) glyph(gui, template.charAt(i), x + positions[i] * scale, slotY, scale, ink, GHOST_ALPHA);
        }
        float regionScale = scale * PlateTextures.REGION_SCALE;
        String code = regionRolling ? rollingRegion() : region;
        float regionHeat = (now - regionLandedAt) / (float) HEAT_MS;
        float regionAlpha = menu.numbered() || spinning ? 1 : GHOST_ALPHA;
        for (int i = 0; i < code.length(); i++) {
            float gx = x + PlateTextures.regionX(code, i) * scale;
            float gy = y + PlateTextures.REGION_Y * scale;
            glyph(gui, code.charAt(i), gx, gy, regionScale, ink, regionAlpha);
            if (regionHeat >= 0 && regionHeat < 1) glyph(gui, code.charAt(i), gx, gy, regionScale, BrandUi.GOLD & 0xFFFFFF, (1 - regionHeat) * .9f);
        }
        for (int i = 0; i < slots.length; i++) {
            Slot slot = slots[i];
            float slotX = x + positions[i] * scale;
            if (slot.rolled) {
                scissor(gui, slotX, slotY, slotX + cellW, slotY + cellH);
                long n = (long) Math.floor(slot.reel);
                float frac = (float) (slot.reel - n);
                glyph(gui, slot.at(n), slotX, slotY + frac * cellH, scale, ink, 1);
                glyph(gui, slot.at(n + 1), slotX, slotY + (frac - 1) * cellH, scale, ink, 1);
                gui.disableScissor();
                continue;
            }
            if (slot.shown == 0) continue;
            float since = now - slot.landedAt;
            float bounce = since >= 0 && since < BOUNCE_MS ? Mth.sin((float) Math.PI * since / BOUNCE_MS) * (1 - since / BOUNCE_MS) * BOUNCE * cellH : 0;
            scissor(gui, slotX, slotY, slotX + cellW, slotY + cellH);
            glyph(gui, slot.shown, slotX, slotY + bounce, scale, ink, 1);
            float heat = since / HEAT_MS;
            if (heat >= 0 && heat < 1) glyph(gui, slot.shown, slotX, slotY + bounce, scale, BrandUi.GOLD & 0xFFFFFF, (1 - heat) * .9f);
            gui.disableScissor();
        }
        long sinceReveal = now - finishedAt;
        if (statusOk && sinceReveal < REVEAL_MS) shine(gui, x, y, PLATE_W, PlateTextures.HEIGHT * scale, sinceReveal / (float) REVEAL_MS);
    }

    private Area slotArea(int slot) {
        float scale = plateScale();
        float x = plateX() + PlateTextures.slots(style)[slot] * scale;
        float y = plateY() + PlateTextures.SLOT_Y * scale;
        return new Area(Math.round(x), Math.round(y), Math.round(PlateTextures.CELL_WIDTH * scale), Math.round(PlateTextures.CELL_HEIGHT * scale));
    }

    private Area regionArea() {
        float scale = plateScale();
        float glyph = PlateTextures.CELL_WIDTH * PlateTextures.REGION_SCALE;
        float x0 = plateX() + PlateTextures.regionX(region, 0) * scale;
        float x1 = plateX() + (PlateTextures.regionX(region, region.length() - 1) + glyph) * scale;
        float y = plateY() + PlateTextures.REGION_Y * scale;
        return new Area(Math.round(x0), Math.round(y), Math.round(x1 - x0), Math.round(PlateTextures.CELL_HEIGHT * PlateTextures.REGION_SCALE * scale));
    }

    private Area regionDigitArea(int index) {
        float scale = plateScale();
        int glyph = region.length() - PlateRegions.DIGITS + index;
        float x = plateX() + PlateTextures.regionX(region, glyph) * scale;
        float y = plateY() + PlateTextures.REGION_Y * scale;
        return new Area(Math.round(x), Math.round(y), Math.round(PlateTextures.CELL_WIDTH * PlateTextures.REGION_SCALE * scale),
                Math.round(PlateTextures.CELL_HEIGHT * PlateTextures.REGION_SCALE * scale));
    }

    private Area pickArea(int slot) {
        return slot < slots.length ? slotArea(slot) : regionDigitArea(slot - slots.length);
    }

    private void picks(GuiGraphics gui, int mouseX, int mouseY) {
        if (spinning || !PlateNumber.valid(platePreview)) return;
        for (int slot = 0; slot < pickCount(); slot++) {
            Area area = pickArea(slot);
            boolean hover = area.inside(mouseX, mouseY);
            if (slot == pick && pickable(selected, slot)) frame(gui, area, BrandUi.GOLD, 2);
            else if (hover) frame(gui, area, BrandUi.alpha(BrandUi.GOLD_LIGHT, .8f), 1);
            else if (pickable(selected, slot)) frame(gui, area, BrandUi.alpha(BrandUi.GOLD_LIGHT, .3f), 1);
        }
        if (selected == REGION) frame(gui, regionArea(), BrandUi.GOLD, 2);
    }

    private static void frame(GuiGraphics gui, Area area, int color, int thickness) {
        int x0 = area.x() - 2;
        int y0 = area.y() - 2;
        int x1 = area.right() + 2;
        int y1 = area.bottom() + 2;
        gui.fill(x0, y0, x1, y0 + thickness, color);
        gui.fill(x0, y1 - thickness, x1, y1, color);
        gui.fill(x0, y0 + thickness, x0 + thickness, y1 - thickness, color);
        gui.fill(x1 - thickness, y0 + thickness, x1, y1 - thickness, color);
    }

    private void shine(GuiGraphics gui, float x, float y, float w, float h, float t) {
        float sweep = t / .75f;
        if (sweep >= 1) return;
        float band = 46;
        float at = x - band + (w + band) * sweep;
        scissor(gui, x, y, x + w, y + h);
        BrandUi.strip(gui, this, Strip.HAIR, at, y, band, h, false, BrandUi.alpha(rare ? BrandUi.GOLD_LIGHT : BrandUi.WHITE, .95f));
        gui.disableScissor();
    }

    private Line currentLine(long now) {
        if (hoverLocked) return new Line(I18n.get("gui.slimvehicle.plates.need_full"), BrandUi.GOLD_LIGHT);
        if (spinning) return null;
        if (!status.isEmpty()) return new Line(I18n.get(status), statusOk ? BrandUi.GREEN : BrandUi.RED_SOFT);
        if (allowed(selected) && !affordable(selected)) return new Line(I18n.get("gui.slimvehicle.plates.short", money(menu.prices()[selected - 1] - menu.balanceValue())), BrandUi.RED_SOFT);
        if (statusOk && now - finishedAt < 4000) return new Line(I18n.get("message.slimvehicle.plates.done"), BrandUi.GREEN);
        return null;
    }

    private void modeCards(GuiGraphics gui, int mouseX, int mouseY) {
        hoverLocked = false;
        for (int i = 0; i < modes.length; i++) {
            int action = i + 1;
            Area area = modes[i];
            boolean enabled = allowed(action);
            boolean hover = !spinning && area.inside(mouseX, mouseY);
            if (hover && !enabled) hoverLocked = true;
            boolean chosen = selected == action;
            float alpha = enabled ? 1 : .42f;
            Box box = chosen ? Box.CARD_ON : hover && enabled ? Box.CARD_HOVER : Box.CARD;
            BrandUi.box(gui, this, box, area.x(), area.y(), area.w(), area.h(), alpha);
            BrandUi.icon(gui, this, chosen ? Icon.RADIO_ON : Icon.RADIO, area.right() - 17, area.y() + 8, BrandUi.alpha(BrandUi.WHITE, alpha));
            BrandUi.text(gui, this, I18n.get(SPIN_KEYS[i]), Type.LABEL, area.x() + 9, area.y() + 9, BrandUi.alpha(BrandUi.WHITE, alpha));
            String[] hint = I18n.get(HINT_KEYS[i]).split("\n");
            for (int line = 0; line < hint.length && line < 2; line++) {
                BrandUi.text(gui, this, hint[line], Type.SMALL, area.x() + 9, area.y() + 21 + line * 8, BrandUi.alpha(BrandUi.MUTED, alpha));
            }
            if (!enabled) {
                BrandUi.text(gui, this, I18n.get("gui.slimvehicle.plates.need_full"), Type.SMALL, area.x() + 9, area.bottom() - 12, BrandUi.alpha(BrandUi.TEXT, alpha));
                continue;
            }
            int color = !affordable(action) ? BrandUi.RED_SOFT : BrandUi.GOLD_LIGHT;
            BrandUi.text(gui, this, price(i), Type.LABEL, area.x() + 9, area.bottom() - 13, color);
        }
    }

    private String price(int index) {
        long price = menu.prices()[index];
        return price == 0 || menu.free() ? I18n.get("gui.slimvehicle.plates.free") : money(price);
    }

    private void footer(GuiGraphics gui, int mouseX, int mouseY, long now) {
        int x0 = left + RIGHT_X;
        BrandUi.text(gui, this, I18n.get("gui.slimvehicle.plates.to_pay"), Type.CAP, x0, top + H - 40, BrandUi.MUTED);
        int color = allowed(selected) && !affordable(selected) ? BrandUi.RED_SOFT : BrandUi.GOLD_LIGHT;
        BrandUi.text(gui, this, price(selected - 1), Type.VALUE, x0, top + H - 28, color);
        float denied = (now - deniedAt) / (float) DENIED_MS;
        int nudge = denied < 1 ? Math.round(Mth.sin(denied * 40) * 3 * (1 - denied)) : 0;
        boolean canSubmit = !spinning && allowed(selected) && affordable(selected);
        boolean hover = canSubmit && submitArea.inside(mouseX, mouseY);
        Area button = new Area(submitArea.x() + nudge, submitArea.y(), submitArea.w(), submitArea.h());
        String label = spinning ? I18n.get("gui.slimvehicle.plates.spinning") : I18n.get("gui.slimvehicle.plates.submit");
        BrandUi.primary(gui, this, button, label, spinning ? null : "Enter", !spinning, canSubmit || spinning, hover);
    }

    private void effects(GuiGraphics gui, long now) {
        stars.removeIf(star -> now - star.born > star.life);
        for (Star star : stars) {
            if (now < star.born) continue;
            float seconds = (now - star.born) / 1000f;
            float life = (now - star.born) / (float) star.life;
            float x = star.x + star.vx * seconds;
            float y = star.y + star.vy * seconds + 160 * seconds * seconds;
            gui.pose().pushPose();
            gui.pose().translate(x, y, 0);
            gui.pose().mulPose(Axis.ZP.rotationDegrees(star.spin * seconds));
            BrandUi.icon(gui, this, Icon.STAR, -star.size / 2, -star.size / 2, star.size, star.size, BrandUi.alpha(BrandUi.WHITE, 1 - life * life));
            gui.pose().popPose();
        }
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
        gui.setColor((ink >> 16 & 255) / 255f, (ink >> 8 & 255) / 255f, (ink & 255) / 255f, alpha);
        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);
        gui.pose().scale(scale, scale, 1);
        gui.blit(PlateTextures.GLYPHS, 0, 0, PlateTextures.glyphU(character), PlateTextures.glyphV(character), PlateTextures.CELL_WIDTH, PlateTextures.CELL_HEIGHT,
                PlateTextures.ATLAS_WIDTH, PlateTextures.ATLAS_HEIGHT);
        gui.pose().popPose();
        gui.setColor(1, 1, 1, 1);
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.clicked(mouseX, mouseY, button);
        if (closeArea.inside(mouseX, mouseY)) {
            play(SoundEvents.STONE_BUTTON_CLICK_ON, 1, .4f);
            onClose();
            return true;
        }
        if (submitArea.inside(mouseX, mouseY)) {
            submit();
            return true;
        }
        if (!spinning && PlateNumber.valid(platePreview)) {
            for (int slot = 0; slot < pickCount(); slot++) {
                if (pickArea(slot).inside(mouseX, mouseY)) {
                    pickSlot(slot);
                    return true;
                }
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
        if (action >= 1 && action <= modes.length && !spinning && allowed(action)) {
            choose(action);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            int direction = key == GLFW.GLFW_KEY_LEFT ? -1 : 1;
            if (!spinning && (selected == LETTER || selected == DIGIT)) stepPick(direction);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
