package ru.mosvegas.character.data;

import net.minecraft.network.PacketByteBuf;

import java.util.List;

public final class Appearance {
    public static final int MALE = 0;
    public static final int FEMALE = 1;

    public int gender = MALE;
    public int height = 2;
    public int build = 1;
    public int skin = 2;
    public int eyes = 0;
    public int hairColor = 0;
    public int hair = 0;
    public int beard = 0;
    public String outfit = "";

    public Appearance copy() {
        Appearance a = new Appearance();
        a.gender = gender;
        a.height = height;
        a.build = build;
        a.skin = skin;
        a.eyes = eyes;
        a.hairColor = hairColor;
        a.hair = hair;
        a.beard = beard;
        a.outfit = outfit;
        return a;
    }

    public boolean isValid(List<String> outfits) {
        if (gender < 0 || gender >= CharacterOptions.GENDERS) return false;
        if (height < 0 || height >= CharacterOptions.HEIGHTS) return false;
        if (build < 0 || build >= CharacterOptions.BUILDS) return false;
        if (skin < 0 || skin >= CharacterOptions.SKIN_TONES) return false;
        if (eyes < 0 || eyes >= CharacterOptions.EYE_COLORS) return false;
        if (hairColor < 0 || hairColor >= CharacterOptions.HAIR_COLORS) return false;
        if (hair < 0 || hair >= CharacterOptions.HAIRS_PER_GENDER) return false;
        if (beard < 0 || beard >= CharacterOptions.BEARDS) return false;
        if (gender == FEMALE && beard != 0) return false;
        if (outfit == null) return false;
        return outfits.isEmpty() ? outfit.isEmpty() : outfits.contains(outfit);
    }

    public void write(PacketByteBuf buf) {
        buf.writeByte(gender);
        buf.writeByte(height);
        buf.writeByte(build);
        buf.writeByte(skin);
        buf.writeByte(eyes);
        buf.writeByte(hairColor);
        buf.writeByte(hair);
        buf.writeByte(beard);
        buf.writeString(outfit, 64);
    }

    public static Appearance read(PacketByteBuf buf) {
        Appearance a = new Appearance();
        a.gender = buf.readByte();
        a.height = buf.readByte();
        a.build = buf.readByte();
        a.skin = buf.readByte();
        a.eyes = buf.readByte();
        a.hairColor = buf.readByte();
        a.hair = buf.readByte();
        a.beard = buf.readByte();
        a.outfit = buf.readString(64);
        return a;
    }
}
