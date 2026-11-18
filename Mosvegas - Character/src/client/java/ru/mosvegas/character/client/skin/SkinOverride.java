package ru.mosvegas.character.client.skin;

import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import ru.mosvegas.character.client.ClientCharacters;
import ru.mosvegas.character.data.Appearance;

import java.util.UUID;

public final class SkinOverride {
    private SkinOverride() {}

    public static SkinTextures apply(UUID id, SkinTextures original) {
        if (id == null) return original;
        Appearance a = ClientCharacters.effective(id);
        if (a == null) return original;
        Identifier tex = SkinComposer.texture(a);
        if (tex == null) return original;
        SkinTextures.Model model = a.gender == Appearance.FEMALE ? SkinTextures.Model.SLIM : SkinTextures.Model.WIDE;
        return new SkinTextures(tex, null, original.capeTexture(), original.elytraTexture(), model, original.secure());
    }
}
