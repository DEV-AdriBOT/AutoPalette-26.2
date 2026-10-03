/* Port changes for Minecraft 26.2: Copyright (c) 2026 NeonDev.
   MIT terms: LICENSE-NeonDev-MIT; original work: Apache 2.0. */
package com.example.autopalette.client;

import com.example.autopalette.client.gui.DrawScreen;
import com.example.autopalette.client.painter.AutoPainter;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.Screen;

public class ArtMapDrawClient
implements ClientModInitializer {
    private static KeyMapping openGuiKey;

    public void onInitializeClient() {
        openGuiKey = KeyMappingHelper.registerKeyMapping((KeyMapping)new KeyMapping("key.autopalette.open", InputConstants.Type.KEYSYM, 72, KeyMapping.Category.register(Identifier.fromNamespaceAndPath("autopalette", "keybindings"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.consumeClick()) {
                if (client.player == null) continue;
                client.gui.setScreen((Screen)new DrawScreen());
            }
            AutoPainter.INSTANCE.clientTick();
        });
    }
}
