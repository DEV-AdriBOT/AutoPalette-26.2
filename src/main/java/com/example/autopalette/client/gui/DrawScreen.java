/* Port changes for Minecraft 26.2: Copyright (c) 2026 NeonDev.
   MIT terms: LICENSE-NeonDev-MIT; original work: Apache 2.0. */
package com.example.autopalette.client.gui;

import com.example.autopalette.client.painter.AutoPainter;
import com.example.autopalette.client.palette.ArtMapPalette;
import com.example.autopalette.client.util.MaterialHighlighter;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public class DrawScreen
extends Screen {
    private final ArtMapPalette palette = new ArtMapPalette();
    private final List<File> imageFiles = new ArrayList<File>();
    private int selectedImageIndex = -1;
    private int scrollOffset = 0;
    private int drawDelay = 2;
    private boolean smoothCam = true;
    private float rotationSpeed = 30.0f;
    private static int selectedOrderIndex = 0;
    private final String[] paintOrders = new String[]{"Row by Row (L to R)", "Row by Row (R to L)", "Column by Column", "Snake", "Color-Optimized"};
    private int ditheringModeIndex = 1;
    private static int canvasSizeIndex = 0;
    private static int imageFitIndex = 0;
    private static final int[] CANVAS_SIZES = {32, 64, 128};
    private static final String[] IMAGE_FITS = {"Fit", "Fill", "Stretch"};
    private boolean dyesOnly = false;
    private int maxColorsIndex = 0;
    private final int[] colorLimits = new int[]{-1, 32, 16, 8};
    private final int previewSize = 128;
    private static final int[][] BAYER_4X4 = new int[][]{{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};
    private static final int[][] BAYER_8X8 = new int[][]{{0, 48, 12, 60, 3, 51, 15, 63}, {32, 16, 44, 28, 35, 19, 47, 31}, {8, 56, 4, 52, 11, 59, 7, 55}, {40, 24, 36, 20, 43, 27, 39, 23}, {2, 50, 14, 62, 1, 49, 13, 61}, {34, 18, 46, 30, 33, 17, 45, 29}, {10, 58, 6, 54, 9, 57, 5, 53}, {42, 26, 38, 22, 41, 25, 37, 21}};
    private DynamicTexture previewTexture = null;
    private Identifier previewTextureId = null;
    private ArtMapPalette.MappedColor[][] mappedColorsGrid = null;
    private Button drawButton;
    private Button orderButton;
    private Button ditheringButton;
    private Button smoothCamButton;
    private Button delayMinusButton;
    private Button delayPlusButton;
    private Button settingsTabButton;
    private Button materialsTabButton;
    private Button matScrollUpButton;
    private Button matScrollDownButton;
    private Button maxColorsButton;
    private Button dyesOnlyButton;
    private Button highlightButton;
    private Button exportMaterialsButton;
    private Button autoVerifyButton;
    private Button canvasSizeButton;
    private Button imageFitButton;
    private boolean autoVerify = false;
    private Button autoSaveButton;
    private EditBox autoSaveTextField;
    private static boolean autoSave = false;
    private static String saveName = "";
    private boolean showMaterialsTab = false;
    private int materialScrollOffset = 0;
    private final List<MaterialEntry> materialsList = new ArrayList<MaterialEntry>();

    public DrawScreen() {
        super((Component)Component.literal((String)"AutoPalette Panel v1.2.6"));
    }

    protected void init() {
        this.refreshImageList();
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u25b2"), button -> {
            if (this.scrollOffset > 0) {
                --this.scrollOffset;
            }
        }).bounds(10, 35, 120, 15).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u25bc"), button -> {
            if (this.scrollOffset < Math.max(0, this.imageFiles.size() - 5)) {
                ++this.scrollOffset;
            }
        }).bounds(10, 145, 120, 15).build());
        for (int i = 0; i < 5; ++i) {
            int index = i;
            this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Empty"), button -> {
                int targetIndex = this.scrollOffset + index;
                if (targetIndex < this.imageFiles.size()) {
                    this.selectedImageIndex = targetIndex;
                    this.loadSelectedImagePreview();
                }
            }).bounds(10, 52 + i * 18, 120, 16).build());
        }
        this.settingsTabButton = (Button)this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Settings"), button -> {
            this.showMaterialsTab = false;
            this.updateTabVisibility();
        }).bounds(145, 35, 72, 20).build());
        this.materialsTabButton = (Button)this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Materials"), button -> {
            this.showMaterialsTab = true;
            this.updateTabVisibility();
        }).bounds(220, 35, 75, 20).build());
        this.delayMinusButton = (Button)this.addRenderableWidget(Button.builder((Component)Component.literal((String)"-"), button -> {
            if (this.drawDelay > 0) {
                --this.drawDelay;
            }
        }).bounds(145, 80, 20, 20).build());
        this.delayPlusButton = (Button)this.addRenderableWidget(Button.builder((Component)Component.literal((String)"+"), button -> {
            if (this.drawDelay < 20) {
                ++this.drawDelay;
            }
        }).bounds(225, 80, 20, 20).build());
        this.ditheringButton = (Button)this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Dithering: Floyd-Steinberg"), button -> {
            this.ditheringModeIndex = (this.ditheringModeIndex + 1) % 4;
            this.updateButtonsText();
            this.loadSelectedImagePreview();
        }).bounds(145, 105, 150, 20).build());
        this.smoothCamButton = (Button)this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Cam: ON"), button -> {
            this.smoothCam = !this.smoothCam;
            this.updateButtonsText();
        }).bounds(145, 130, 73, 20).build());
        this.autoVerifyButton = (Button)this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Verify: OFF"), button -> {
            this.autoVerify = !this.autoVerify;
            this.updateButtonsText();
        }).bounds(222, 130, 73, 20).build());
        this.orderButton = (Button)this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Order: Color-Optimized"), button -> {
            this.selectedOrderIndex = (this.selectedOrderIndex + 1) % this.paintOrders.length;
            this.updateButtonsText();
        }).bounds(145, 155, 150, 20).build());
        this.maxColorsButton = (Button)this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Max Colors: Unlimited"), button -> {
            this.maxColorsIndex = (this.maxColorsIndex + 1) % this.colorLimits.length;
            this.updateButtonsText();
            this.loadSelectedImagePreview();
            this.materialScrollOffset = 0;
        }).bounds(145, 180, 150, 20).build());
        this.matScrollUpButton = (Button)this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u25b2"), button -> {
            if (this.materialScrollOffset > 0) {
                --this.materialScrollOffset;
            }
        }).bounds(280, 75, 15, 20).build());
        this.matScrollDownButton = (Button)this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u25bc"), button -> {
            if (this.materialScrollOffset < this.materialsList.size() - 8) {
                ++this.materialScrollOffset;
            }
        }).bounds(280, 100, 15, 20).build());
        this.dyesOnlyButton = (Button)this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Dyes Only: OFF"), button -> {
            this.dyesOnly = !this.dyesOnly;
            this.updateButtonsText();
            this.loadSelectedImagePreview();
        }).bounds(145, 205, 150, 20).build());
        this.highlightButton = (Button)this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Highlight: OFF"), button -> {
            MaterialHighlighter.setActive(!MaterialHighlighter.isActive());
            if (MaterialHighlighter.isActive()) {
                MaterialHighlighter.clear();
                for (MaterialEntry entry : this.materialsList) {
                    if (entry.itemId == null || entry.itemId.isEmpty()) continue;
                    MaterialHighlighter.addNeededItem(entry.itemId);
                }
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage((Component)Component.literal((String)"\u00a7aMaterial highlighting enabled!"));
                }
            } else {
                MaterialHighlighter.clear();
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage((Component)Component.literal((String)"\u00a7cMaterial highlighting disabled."));
                }
            }
            this.updateButtonsText();
        }).bounds(145, 205, 150, 20).build());
        this.autoSaveButton = (Button)this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Save: OFF"), button -> {
            autoSave = !autoSave;
            this.updateButtonsText();
        }).bounds(145, 230, 60, 20).build());
        this.autoSaveTextField = new EditBox(this.font, 210, 230, 85, 20, (Component)Component.literal((String)"Save Name"));
        this.autoSaveTextField.setMaxLength(32);
        this.autoSaveTextField.setValue(saveName);
        this.addRenderableWidget(this.autoSaveTextField);
        this.exportMaterialsButton = this.addRenderableWidget(Button.builder(Component.literal("Export Materials"), button -> this.exportMaterials()).bounds(145, 230, 150, 20).build());
        this.canvasSizeButton = this.addRenderableWidget(Button.builder(Component.literal("Canvas: 32 x 32"), button -> {
            canvasSizeIndex = (canvasSizeIndex + 1) % CANVAS_SIZES.length;
            this.updateButtonsText();
            this.loadSelectedImagePreview();
        }).bounds(300, 230, 128, 20).build());
        this.imageFitButton = this.addRenderableWidget(Button.builder(Component.literal("Image: Fit"), button -> {
            imageFitIndex = (imageFitIndex + 1) % IMAGE_FITS.length;
            this.updateButtonsText();
            this.loadSelectedImagePreview();
        }).bounds(300, 255, 128, 20).build());
        this.drawButton = (Button)this.addRenderableWidget(Button.builder((Component)Component.literal((String)"START DRAWING"), button -> {
            if (AutoPainter.INSTANCE.isActive()) {
                AutoPainter.INSTANCE.stopPainting();
            } else if (this.mappedColorsGrid != null) {
                if (this.autoSaveTextField != null) {
                    saveName = this.autoSaveTextField.getValue();
                }
                AutoPainter.INSTANCE.startPainting(this.mappedColorsGrid, this.drawDelay, this.smoothCam, this.rotationSpeed, this.paintOrders[this.selectedOrderIndex], this.autoVerify, autoSave, saveName);
                this.onClose();
            }
        }).bounds(145, 255, 150, 20).build());
        this.updateButtonsText();
        this.updateListButtonLabels();
    }

    private void refreshImageList() {
        File[] files;
        this.imageFiles.clear();
        File folder = new File(Minecraft.getInstance().gameDirectory, "config/autopalette/images");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        if ((files = folder.listFiles()) != null) {
            for (File file : files) {
                if (!file.getName().endsWith(".png") && !file.getName().endsWith(".jpg") && !file.getName().endsWith(".jpeg")) continue;
                this.imageFiles.add(file);
            }
        }
    }

    private void updateButtonsText() {
        String ditheringText = switch (this.ditheringModeIndex) {
            case 0 -> "None";
            case 1 -> "Floyd-Steinberg";
            case 2 -> "Bayer 4x4";
            case 3 -> "Bayer 8x8";
            default -> "None";
        };
        this.ditheringButton.setMessage((Component)Component.literal((String)("Dithering: " + ditheringText)));
        this.canvasSizeButton.setMessage(Component.literal("Canvas: " + CANVAS_SIZES[canvasSizeIndex] + " x " + CANVAS_SIZES[canvasSizeIndex]));
        this.imageFitButton.setMessage(Component.literal("Image: " + IMAGE_FITS[imageFitIndex]));
        this.smoothCamButton.setMessage((Component)Component.literal((String)("Cam: " + (this.smoothCam ? "ON" : "OFF"))));
        if (this.autoVerifyButton != null) {
            this.autoVerifyButton.setMessage((Component)Component.literal((String)("Verify: " + (this.autoVerify ? "ON" : "OFF"))));
        }
        this.orderButton.setMessage((Component)Component.literal((String)("Order: " + this.getShortOrderName(this.paintOrders[this.selectedOrderIndex]))));
        int limit = this.colorLimits[this.maxColorsIndex];
        this.maxColorsButton.setMessage((Component)Component.literal((String)("Max Colors: " + String.valueOf(limit == -1 ? "Unlimited" : Integer.valueOf(limit)))));
        this.dyesOnlyButton.setMessage((Component)Component.literal((String)("Dyes Only: " + (this.dyesOnly ? "ON" : "OFF"))));
        if (this.highlightButton != null) {
            this.highlightButton.setMessage((Component)Component.literal((String)("Highlight: " + (MaterialHighlighter.isActive() ? "ON" : "OFF"))));
        }
        if (this.autoSaveButton != null) {
            this.autoSaveButton.setMessage((Component)Component.literal((String)("Save: " + (autoSave ? "ON" : "OFF"))));
        }
        if (AutoPainter.INSTANCE.isActive()) {
            this.drawButton.setMessage((Component)Component.literal((String)"STOP DRAWING"));
        } else {
            this.drawButton.setMessage((Component)Component.literal((String)"START DRAWING"));
        }
    }

    private String getShortOrderName(String fullName) {
        if (fullName.contains("Row")) {
            return "Row by Row";
        }
        if (fullName.contains("Column")) {
            return "Col by Col";
        }
        return fullName;
    }

    private void updateListButtonLabels() {
        int listBtnCount = 0;
        for (GuiEventListener child : this.children()) {
            Button btn;
            if (!(child instanceof Button) || (btn = (Button)child).getX() != 10 || btn.getY() < 52 || btn.getY() > 124) continue;
            int targetIndex = this.scrollOffset + listBtnCount;
            if (targetIndex < this.imageFiles.size()) {
                Object name = this.imageFiles.get(targetIndex).getName();
                if (((String)name).length() > 14) {
                    name = ((String)name).substring(0, 11) + "...";
                }
                btn.setMessage((Component)Component.literal((String)name));
                btn.active = true;
            } else {
                btn.setMessage((Component)Component.literal((String)"- Empty -"));
                btn.active = false;
            }
            ++listBtnCount;
        }
    }

    private ArtMapPalette.MappedColor getClosestColor(int r, int g, int b) {
        ArtMapPalette.MappedColor closest = null;
        double minDistance = Double.MAX_VALUE;
        for (ArtMapPalette.MappedColor color : this.palette.getMappedColors()) {
            double dist;
            if (this.dyesOnly && color.shade != ArtMapPalette.ShadeLevel.BASE || !((dist = this.getColorDistance(r, g, b, color.r, color.g, color.b)) < minDistance)) continue;
            minDistance = dist;
            closest = color;
        }
        return closest;
    }

    private ArtMapPalette.MappedColor getClosestColorFrom(int r, int g, int b, Set<ArtMapPalette.PaletteEntry> allowedEntries) {
        ArtMapPalette.MappedColor closest = null;
        double minDistance = Double.MAX_VALUE;
        for (ArtMapPalette.MappedColor color : this.palette.getMappedColors()) {
            double dist;
            if (!allowedEntries.contains(color.baseEntry) || this.dyesOnly && color.shade != ArtMapPalette.ShadeLevel.BASE || !((dist = this.getColorDistance(r, g, b, color.r, color.g, color.b)) < minDistance)) continue;
            minDistance = dist;
            closest = color;
        }
        return closest;
    }

    private double getColorDistance(int r1, int g1, int b1, int r2, int g2, int b2) {
        long rDiff = r1 - r2;
        long gDiff = g1 - g2;
        long bDiff = b1 - b2;
        return Math.sqrt(rDiff * rDiff + gDiff * gDiff + bDiff * bDiff);
    }

    private void loadSelectedImagePreview() {
        if (this.selectedImageIndex < 0 || this.selectedImageIndex >= this.imageFiles.size()) {
            return;
        }
        File file = this.imageFiles.get(this.selectedImageIndex);
        try {
            int rgb;
            int x;
            BufferedImage originalImage = ImageIO.read(file);
            if (originalImage == null) {
                return;
            }
            int resolution = CANVAS_SIZES[canvasSizeIndex];
            BufferedImage resized = new BufferedImage(resolution, resolution, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = resized.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, resolution, resolution);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scale = imageFitIndex == 0
                    ? Math.min((double) resolution / originalImage.getWidth(), (double) resolution / originalImage.getHeight())
                    : Math.max((double) resolution / originalImage.getWidth(), (double) resolution / originalImage.getHeight());
            if (imageFitIndex == 2) {
                graphics.drawImage(originalImage, 0, 0, resolution, resolution, null);
            } else {
                int width = Math.max(1, (int) Math.round(originalImage.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(originalImage.getHeight() * scale));
                graphics.drawImage(originalImage, (resolution - width) / 2, (resolution - height) / 2, width, height, null);
            }
            graphics.dispose();
            this.mappedColorsGrid = new ArtMapPalette.MappedColor[resolution][resolution];
            NativeImage previewImg = new NativeImage(128, 128, false);
            int limit = this.colorLimits[this.maxColorsIndex];
            HashSet<ArtMapPalette.PaletteEntry> allowedBaseEntries = new HashSet<ArtMapPalette.PaletteEntry>();
            if (limit == -1) {
                allowedBaseEntries.addAll(this.palette.getBaseEntries());
            } else {
                HashMap<ArtMapPalette.PaletteEntry, Integer> frequencyMap = new HashMap<ArtMapPalette.PaletteEntry, Integer>();
                for (int y = 0; y < resolution; ++y) {
                    for (x = 0; x < resolution; ++x) {
                        int b;
                        int g;
                        rgb = resized.getRGB(x, y);
                        int r = rgb >> 16 & 0xFF;
                        ArtMapPalette.MappedColor closest = this.getClosestColor(r, g = rgb >> 8 & 0xFF, b = rgb & 0xFF);
                        if (closest == null || closest.baseEntry == null) continue;
                        frequencyMap.put(closest.baseEntry, frequencyMap.getOrDefault(closest.baseEntry, 0) + 1);
                    }
                }
                ArrayList<Map.Entry<ArtMapPalette.PaletteEntry, Integer>> sortedEntries = new ArrayList<>(frequencyMap.entrySet());
                sortedEntries.sort((e1, e2) -> Integer.compare((Integer)e2.getValue(), (Integer)e1.getValue()));
                for (int i = 0; i < Math.min(limit, sortedEntries.size()); ++i) {
                    allowedBaseEntries.add((ArtMapPalette.PaletteEntry)((Map.Entry)sortedEntries.get(i)).getKey());
                }
                if (allowedBaseEntries.isEmpty() && !this.palette.getBaseEntries().isEmpty()) {
                    allowedBaseEntries.add(this.palette.getBaseEntries().get(0));
                }
            }
            if (this.ditheringModeIndex == 1) {
                int y;
                double[][][] rgbGrid = new double[resolution][resolution][3];
                for (y = 0; y < resolution; ++y) {
                    for (x = 0; x < resolution; ++x) {
                        rgb = resized.getRGB(x, y);
                        rgbGrid[x][y][0] = rgb >> 16 & 0xFF;
                        rgbGrid[x][y][1] = rgb >> 8 & 0xFF;
                        rgbGrid[x][y][2] = rgb & 0xFF;
                    }
                }
                for (y = 0; y < resolution; ++y) {
                    for (x = 0; x < resolution; ++x) {
                        ArtMapPalette.MappedColor closest;
                        double r = Math.min(255.0, Math.max(0.0, rgbGrid[x][y][0]));
                        double g = Math.min(255.0, Math.max(0.0, rgbGrid[x][y][1]));
                        double b = Math.min(255.0, Math.max(0.0, rgbGrid[x][y][2]));
                        this.mappedColorsGrid[x][y] = closest = this.getClosestColorFrom((int)r, (int)g, (int)b, allowedBaseEntries);
                        double errR = r - (double)closest.r;
                        double errG = g - (double)closest.g;
                        double errB = b - (double)closest.b;
                        this.distributeError(rgbGrid, x + 1, y, errR, errG, errB, 0.4375);
                        this.distributeError(rgbGrid, x - 1, y + 1, errR, errG, errB, 0.1875);
                        this.distributeError(rgbGrid, x, y + 1, errR, errG, errB, 0.3125);
                        this.distributeError(rgbGrid, x + 1, y + 1, errR, errG, errB, 0.0625);
                        int abgr = 0xFF000000 | closest.b << 16 | closest.g << 8 | closest.r;
                        previewImg.setPixelABGR(x, y, abgr);
                    }
                }
            } else if (this.ditheringModeIndex == 2 || this.ditheringModeIndex == 3) {
                int size = this.ditheringModeIndex == 2 ? 4 : 8;
                int[][] bayerMatrix = this.ditheringModeIndex == 2 ? BAYER_4X4 : BAYER_8X8;
                double spread = 32.0;
                for (int y = 0; y < resolution; ++y) {
                    for (int x2 = 0; x2 < resolution; ++x2) {
                        ArtMapPalette.MappedColor closest;
                        int rgb2 = resized.getRGB(x2, y);
                        int origR = rgb2 >> 16 & 0xFF;
                        int origG = rgb2 >> 8 & 0xFF;
                        int origB = rgb2 & 0xFF;
                        double factor = ((double)bayerMatrix[x2 % size][y % size] + 0.5) / (double)(size * size) - 0.5;
                        int r = (int)((double)origR + factor * spread);
                        int g = (int)((double)origG + factor * spread);
                        int b = (int)((double)origB + factor * spread);
                        r = Math.min(255, Math.max(0, r));
                        g = Math.min(255, Math.max(0, g));
                        b = Math.min(255, Math.max(0, b));
                        this.mappedColorsGrid[x2][y] = closest = this.getClosestColorFrom(r, g, b, allowedBaseEntries);
                        int abgr = 0xFF000000 | closest.b << 16 | closest.g << 8 | closest.r;
                        previewImg.setPixelABGR(x2, y, abgr);
                    }
                }
            } else {
                for (int y = 0; y < resolution; ++y) {
                    for (int x3 = 0; x3 < resolution; ++x3) {
                        ArtMapPalette.MappedColor closest;
                        int rgb3 = resized.getRGB(x3, y);
                        int r = rgb3 >> 16 & 0xFF;
                        int g = rgb3 >> 8 & 0xFF;
                        int b = rgb3 & 0xFF;
                        this.mappedColorsGrid[x3][y] = closest = this.getClosestColorFrom(r, g, b, allowedBaseEntries);
                        int abgr = 0xFF000000 | closest.b << 16 | closest.g << 8 | closest.r;
                        previewImg.setPixelABGR(x3, y, abgr);
                    }
                }
            }
            int pixelScale = 128 / resolution;
            for (int py = 0; py < 128; ++py) {
                for (int px = 0; px < 128; ++px) {
                    ArtMapPalette.MappedColor color = this.mappedColorsGrid[px / pixelScale][py / pixelScale];
                    int abgr = 0xFF000000 | color.b << 16 | color.g << 8 | color.r;
                    previewImg.setPixelABGR(px, py, abgr);
                }
            }
            if (this.previewTexture != null) {
                this.previewTexture.close();
            }
            this.previewTexture = new DynamicTexture(() -> "autopalette_preview", previewImg);
            this.previewTextureId = Identifier.fromNamespaceAndPath((String)"autopalette", (String)"preview");
            Minecraft.getInstance().getTextureManager().register(this.previewTextureId, (AbstractTexture)this.previewTexture);
            this.calculateMaterialsList();
            this.materialScrollOffset = 0;
            this.updateTabVisibility();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void distributeError(double[][][] rgbGrid, int x, int y, double errR, double errG, double errB, double factor) {
        if (x >= 0 && x < CANVAS_SIZES[canvasSizeIndex] && y >= 0 && y < CANVAS_SIZES[canvasSizeIndex]) {
            double[] dArray = rgbGrid[x][y];
            dArray[0] = dArray[0] + errR * factor;
            double[] dArray2 = rgbGrid[x][y];
            dArray2[1] = dArray2[1] + errG * factor;
            double[] dArray3 = rgbGrid[x][y];
            dArray3[2] = dArray3[2] + errB * factor;
        }
    }

    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        this.updateListButtonLabels();
        context.centeredText(this.font, this.title, this.width / 2, 8, -1);
        context.text(this.font, (Component)Component.literal((String)"Images Directory"), 10, 22, -5592406);
        context.text(this.font, (Component)Component.literal((String)"Settings Panel"), 145, 22, -5592406);
        context.text(this.font, (Component)Component.literal((String)"Preview (Mapped)"), 300, 22, -5592406);
        int previewX = 300;
        int previewY = 35;
        context.outline(previewX - 1, previewY - 1, 130, 130, -5592406);
        if (this.previewTextureId != null) {
            context.blit(RenderPipelines.GUI_TEXTURED, this.previewTextureId, previewX, previewY, 0.0f, 0.0f, 128, 128, 128, 128);
        } else {
            context.fill(previewX, previewY, previewX + 128, previewY + 128, -13421773);
            context.centeredText(this.font, (Component)Component.literal((String)"No Preview"), previewX + 64, previewY + 64 - 4, -8947849);
        }
        if (!this.showMaterialsTab) {
            if (this.selectedImageIndex >= 0 && this.selectedImageIndex < this.imageFiles.size()) {
                Object selectedName = this.imageFiles.get(this.selectedImageIndex).getName();
                if (((String)selectedName).length() > 22) {
                    selectedName = ((String)selectedName).substring(0, 19) + "...";
                }
                context.text(this.font, (Component)Component.literal((String)("Selected: " + (String)selectedName)), 145, 58, -10496);
            } else {
                context.text(this.font, (Component)Component.literal((String)"Selected: None"), 145, 58, -65536);
            }
            context.text(this.font, (Component)Component.literal((String)"Delay:"), 145, 72, -1);
            context.centeredText(this.font, (Component)Component.literal((String)(this.drawDelay + " Ticks")), 195, 85, -16711936);
        } else {
            context.text(this.font, (Component)Component.literal((String)"Needed Materials:"), 145, 60, -21846);
            int startY = 75;
            int itemsCount = Math.min(8, this.materialsList.size() - this.materialScrollOffset);
            for (int i = 0; i < itemsCount; ++i) {
                Object name;
                Item item;
                MaterialEntry entry = this.materialsList.get(this.materialScrollOffset + i);
                int y = startY + i * 18;
                if (entry.itemId != null && !entry.itemId.isEmpty() && (item = (Item)BuiltInRegistries.ITEM.getValue(Identifier.parse((String)entry.itemId))) != Items.AIR) {
                    context.item(new ItemStack((ItemLike)item), 145, y - 3);
                }
                if (((String)(name = entry.name)).length() > 13) {
                    name = ((String)name).substring(0, 11) + "..";
                }
                int stacks = entry.count / 64;
                int remaining = entry.count % 64;
                String amtStr = "" + entry.count;
                if (stacks > 0) {
                    amtStr = stacks + "s+" + remaining;
                }
                context.text(this.font, (Component)Component.literal((String)name), 165, y, entry.color);
                context.text(this.font, (Component)Component.literal((String)("x" + amtStr)), 252, y, -4473925);
            }
            if (this.materialsList.isEmpty()) {
                context.text(this.font, (Component)Component.literal((String)"No image selected"), 145, 90, -8947849);
            }
        }
        int statusY = previewY + 128 + 10;
        context.text(this.font, (Component)Component.literal((String)"Status:"), 300, statusY, -5592406);
        context.text(this.font, (Component)Component.literal((String)AutoPainter.INSTANCE.getStatusText()), 300, statusY + 13, -1);
        if (AutoPainter.INSTANCE.isActive()) {
            int progressWidth = 110;
            int progressX = 300;
            int progressY = statusY + 28;
            int progressVal = (int)((double)progressWidth * ((double)AutoPainter.INSTANCE.getProgressPercent() / 100.0));
            context.outline(progressX - 1, progressY - 1, progressWidth + 2, 8, -5592406);
            context.fill(progressX, progressY, progressX + progressWidth, progressY + 6, -13421773);
            context.fill(progressX, progressY, progressX + progressVal, progressY + 6, -16711936);
        }
    }

    private void calculateMaterialsList() {
        this.materialsList.clear();
        if (this.mappedColorsGrid == null) {
            return;
        }
        HashMap<String, Integer> dyeCounts = new HashMap<String, Integer>();
        int feathersTotal = 0;
        int coalTotal = 0;
        for (int y = 0; y < this.mappedColorsGrid.length; ++y) {
            for (int x = 0; x < this.mappedColorsGrid.length; ++x) {
                ArtMapPalette.MappedColor color = this.mappedColorsGrid[x][y];
                if (color == null) continue;
                String string = color.baseEntry.displayName;
                dyeCounts.put(string, dyeCounts.getOrDefault(string, 0) + 1);
                if (color.shade == ArtMapPalette.ShadeLevel.LIGHTENED) {
                    ++feathersTotal;
                    continue;
                }
                if (color.shade == ArtMapPalette.ShadeLevel.DARKENED_1) {
                    ++coalTotal;
                    continue;
                }
                if (color.shade != ArtMapPalette.ShadeLevel.DARKENED_2) continue;
                coalTotal += 2;
            }
        }
        ArrayList<Map.Entry<String, Integer>> sortedDyes = new ArrayList<>(dyeCounts.entrySet());
        sortedDyes.sort((a, b) -> Integer.compare((Integer)b.getValue(), (Integer)a.getValue()));
        HashMap<String, String> nameToId = new HashMap<String, String>();
        for (ArtMapPalette.PaletteEntry paletteEntry : this.palette.getBaseEntries()) {
            nameToId.put(paletteEntry.displayName, paletteEntry.itemId);
        }
        for (Map.Entry entry : sortedDyes) {
            String itemId = nameToId.getOrDefault(entry.getKey(), "");
            this.materialsList.add(new MaterialEntry((String)entry.getKey(), itemId, (Integer)entry.getValue(), -1));
        }
        if (feathersTotal > 0) {
            this.materialsList.add(new MaterialEntry("Feather", "minecraft:feather", feathersTotal, -11141121));
        }
        if (coalTotal > 0) {
            this.materialsList.add(new MaterialEntry("Coal/Charcoal", "minecraft:coal", coalTotal, -5592406));
        }
        if (MaterialHighlighter.isActive()) {
            MaterialHighlighter.clear();
            for (MaterialEntry materialEntry : this.materialsList) {
                if (materialEntry.itemId == null || materialEntry.itemId.isEmpty()) continue;
                MaterialHighlighter.addNeededItem(materialEntry.itemId);
            }
        }
    }

    private void exportMaterials() {
        if (this.selectedImageIndex < 0 || this.selectedImageIndex >= this.imageFiles.size() || this.materialsList.isEmpty()) {
            return;
        }

        String imageName = this.imageFiles.get(this.selectedImageIndex).getName();
        String baseName = imageName.replaceFirst("(?i)\\.(png|jpe?g)$", "").replaceAll("[^a-zA-Z0-9._-]", "_");
        if (baseName.isBlank()) {
            baseName = "painting";
        }

        StringBuilder text = new StringBuilder("Materials for ").append(imageName).append('\n');
        StringBuilder csv = new StringBuilder("item_name,item_id,count,stacks,remainder\n");
        for (MaterialEntry entry : this.materialsList) {
            int stacks = entry.count / 64;
            int remainder = entry.count % 64;
            text.append(entry.name).append(": ").append(entry.count)
                .append(" (").append(stacks).append(" stacks + ").append(remainder).append(")\n");
            csv.append(csvCell(entry.name)).append(',').append(csvCell(entry.itemId)).append(',')
                .append(entry.count).append(',').append(stacks).append(',').append(remainder).append('\n');
        }

        try {
            Path directory = Minecraft.getInstance().gameDirectory.toPath().resolve("config/autopalette/exports");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(baseName + "-materials.txt"), text, StandardCharsets.UTF_8);
            Files.writeString(directory.resolve(baseName + "-materials.csv"), csv, StandardCharsets.UTF_8);
            Minecraft.getInstance().keyboardHandler.setClipboard(text.toString());
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("Materials exported to config/autopalette/exports and copied to clipboard."));
            }
        } catch (IOException e) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("Could not export materials: " + e.getMessage()));
            }
        }
    }

    private static String csvCell(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void updateTabVisibility() {
        boolean showMatScroll;
        boolean showSettings;
        this.ditheringButton.visible = showSettings = !this.showMaterialsTab;
        this.ditheringButton.active = showSettings;
        this.smoothCamButton.visible = showSettings;
        this.smoothCamButton.active = showSettings;
        if (this.autoVerifyButton != null) {
            this.autoVerifyButton.visible = showSettings;
            this.autoVerifyButton.active = showSettings;
        }
        this.orderButton.visible = showSettings;
        this.orderButton.active = showSettings;
        this.delayMinusButton.visible = showSettings;
        this.delayMinusButton.active = showSettings;
        this.delayPlusButton.visible = showSettings;
        this.delayPlusButton.active = showSettings;
        this.maxColorsButton.visible = showSettings;
        this.maxColorsButton.active = showSettings;
        this.dyesOnlyButton.visible = showSettings;
        this.dyesOnlyButton.active = showSettings;
        if (this.autoSaveButton != null) {
            this.autoSaveButton.visible = showSettings;
            this.autoSaveButton.active = showSettings;
        }
        if (this.autoSaveTextField != null) {
            this.autoSaveTextField.visible = showSettings;
            this.autoSaveTextField.setEditable(showSettings);
        }
        if (this.highlightButton != null) {
            this.highlightButton.visible = this.showMaterialsTab;
            this.highlightButton.active = this.showMaterialsTab;
        }
        if (this.exportMaterialsButton != null) {
            this.exportMaterialsButton.visible = this.showMaterialsTab;
            this.exportMaterialsButton.active = this.showMaterialsTab && !this.materialsList.isEmpty();
        }
        this.matScrollUpButton.visible = showMatScroll = this.showMaterialsTab && this.materialsList.size() > 8;
        this.matScrollUpButton.active = showMatScroll;
        this.matScrollDownButton.visible = showMatScroll;
        this.matScrollDownButton.active = showMatScroll;
        this.settingsTabButton.active = this.showMaterialsTab;
        this.materialsTabButton.active = !this.showMaterialsTab;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.showMaterialsTab && mouseX >= 145.0 && mouseX <= 295.0 && mouseY >= 60.0 && mouseY <= 180.0) {
            if (verticalAmount > 0.0) {
                if (this.materialScrollOffset > 0) {
                    --this.materialScrollOffset;
                }
            } else if (verticalAmount < 0.0 && this.materialScrollOffset < this.materialsList.size() - 8) {
                ++this.materialScrollOffset;
            }
            this.updateTabVisibility();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    public void onClose() {
        super.onClose();
        if (this.previewTexture != null) {
            this.previewTexture.close();
            this.previewTexture = null;
            this.previewTextureId = null;
        }
    }

    public static class MaterialEntry {
        public final String name;
        public final String itemId;
        public final int count;
        public final int color;

        public MaterialEntry(String name, String itemId, int count, int color) {
            this.name = name;
            this.itemId = itemId;
            this.count = count;
            this.color = color;
        }
    }
}
