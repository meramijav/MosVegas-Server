package ru.mosvegas.core.mixin;

import net.minecraft.inventory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DoubleInventory.class)
public interface DoubleInventoryAccessor {
    @Accessor("first") Inventory mosvegas$first();
    @Accessor("second") Inventory mosvegas$second();
}
