package ru.mosvegas.character.data;

public final class CharacterPalette {
    public static final int[] SKIN = {
            0xF7DBC3, 0xEEC8AA, 0xE1B694, 0xCDA07C, 0xBA8762, 0x9E6C4C, 0x7A523A, 0x563A2A
    };
    public static final String[] SKIN_NAMES = {
            "Фарфоровый", "Светлый", "Светло-бежевый", "Бежевый", "Смуглый", "Оливковый", "Тёмный", "Очень тёмный"
    };
    public static final int[] EYES = {
            0x603E26, 0x3C281C, 0x806434, 0x4E804A, 0x4676BE, 0x82AAD7, 0x828C96, 0xB07828
    };
    public static final String[] EYE_NAMES = {
            "Карие", "Тёмно-карие", "Ореховые", "Зелёные", "Синие", "Голубые", "Серые", "Янтарные"
    };
    public static final int[] HAIR = {
            0x1C1A1A, 0x3A281E, 0x62422C, 0x8C6644, 0xB08C5A, 0xDABE82, 0xE8E2D2, 0xB05428, 0x783024, 0x6E645C, 0x969696, 0xE1E1E1
    };
    public static final String[] HAIR_NAMES = {
            "Чёрный", "Тёмно-каштановый", "Каштановый", "Русый", "Тёмно-русый блонд", "Блонд", "Платиновый", "Рыжий",
            "Медный", "Пепельный", "Седой", "Белый"
    };
    public static final String[] BUILD_NAMES = {"Худое", "Обычное", "Спортивное", "Крупное"};
    public static final String[] BEARD_NAMES = {"Без бороды", "Щетина", "Усы", "Эспаньолка", "Бальбо", "Шкиперская", "Короткая", "Якорь", "Бакенбарды", "Окладистая", "Длинная"};
    public static final float[] HEIGHT_SCALE = {0.92f, 0.96f, 1.0f, 1.04f, 1.08f};
    public static final int[] HEIGHT_CM = {166, 173, 180, 187, 194};
    public static final int[] HEIGHT_CM_FEMALE = {156, 162, 168, 174, 180};

    private CharacterPalette() {}

    public static float heightScale(Appearance a) {
        if (a == null) return 1f;
        float s = HEIGHT_SCALE[Math.floorMod(a.height, HEIGHT_SCALE.length)];
        return a.gender == Appearance.FEMALE ? s * 0.94f : s;
    }

    public static int heightCm(Appearance a) {
        int i = Math.floorMod(a.height, HEIGHT_CM.length);
        return a.gender == Appearance.FEMALE ? HEIGHT_CM_FEMALE[i] : HEIGHT_CM[i];
    }
}
