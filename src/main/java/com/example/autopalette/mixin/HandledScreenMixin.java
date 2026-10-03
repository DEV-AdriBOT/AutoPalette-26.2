/* Port changes for Minecraft 26.2: Copyright (c) 2026 NeonDev.
   MIT terms: LICENSE-NeonDev-MIT; original work: Apache 2.0. */
package com.example.autopalette.mixin;

import com.example.autopalette.client.util.MaterialHighlighter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={AbstractContainerScreen.class})
public class HandledScreenMixin {
    @Inject(method={"extractSlot"}, at={@At(value="TAIL")})
    private void onDrawSlot(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        Identifier itemId;
        String itemIdStr;
        ItemStack stack;
        if (MaterialHighlighter.isActive() && !(stack = slot.getItem()).isEmpty() && MaterialHighlighter.isNeeded(itemIdStr = (itemId = BuiltInRegistries.ITEM.getKey(stack.getItem())).toString())) {
            context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x5055FF55);
        }
    }
}
