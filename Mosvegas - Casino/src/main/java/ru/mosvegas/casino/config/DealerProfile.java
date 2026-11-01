package ru.mosvegas.casino.config;

import java.util.ArrayList;
import java.util.List;

/**
 * One croupier. {@code skin} accepts:
 * <ul>
 *   <li>a resource id: {@code mosvegas_casino:textures/entity/dealer/anna.png} (resource pack);</li>
 *   <li>a direct link to a 64x64 PNG skin: {@code https://example.com/skin.png};</li>
 *   <li>a Minecraft account: {@code player:Notch} (resolved by the server through the Mojang API).</li>
 * </ul>
 * {@code model}/{@code animations} optionally replace the GeckoLib files for a fully custom dealer.
 */
public final class DealerProfile {
    public String id;
    public String name;
    /** "female" uses the slim (Alex) arms model, "male" the classic (Steve) one. */
    public String gender = "female";
    public String skin;
    public String model = "";
    public String animations = "";
    /** Table style ids this dealer works at; empty = all tables. */
    public List<String> styles = new ArrayList<>();
    public boolean enabled = true;
    /**
     * Voice lines: folder {@code assets/mosvegas_casino/voice/<voice>/} (resource pack, .ogg or .wav);
     * empty = by gender, the built-in "female" / "male" sets.
     */
    public String voice = "";
    /** Voice pitch 0.7..1.4; 0 = a small stable variation per dealer, so two dealers never sound the same. */
    public float voicePitch = 0;

    public DealerProfile() {}

    public DealerProfile(String id, String name, String gender, String skin) {
        this.id = id;
        this.name = name;
        this.gender = gender;
        this.skin = skin;
    }

    public DealerProfile(String id, String name, String gender, String skin, float voicePitch) {
        this(id, name, gender, skin);
        this.voicePitch = voicePitch;
    }

    public boolean slim() { return !"male".equalsIgnoreCase(gender); }

    public String voiceSet() { return voice == null || voice.isBlank() ? (slim() ? "female" : "male") : voice; }

    public float pitch() {
        if (voicePitch > 0) return Math.max(0.7f, Math.min(1.4f, voicePitch));
        return 0.94f + Math.floorMod(id == null ? 0 : id.hashCode(), 9) * 0.015f;
    }
}
