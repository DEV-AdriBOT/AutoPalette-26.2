/* Port changes for Minecraft 26.2: Copyright (c) 2026 NeonDev.
   MIT terms: LICENSE-NeonDev-MIT; original work: Apache 2.0. */
package com.example.autopalette;

import java.io.File;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.Minecraft;

public class ArtMapDraw
implements ModInitializer {
    public static final String MOD_ID = "autopalette";

    public void onInitialize() {
        File configFolder = new File(Minecraft.getInstance().gameDirectory, "config/autopalette");
        File imagesFolder = new File(configFolder, "images");
        if (!imagesFolder.exists()) {
            imagesFolder.mkdirs();
        }
    }
}
