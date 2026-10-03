/* Port changes for Minecraft 26.2: Copyright (c) 2026 NeonDev.
   MIT terms: LICENSE-NeonDev-MIT; original work: Apache 2.0. */
package com.example.autopalette.client.painter;

import com.example.autopalette.client.palette.ArtMapPalette;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class AutoPainter {
    public static final AutoPainter INSTANCE = new AutoPainter();
    private final Minecraft client = Minecraft.getInstance();
    private boolean active = false;
    private int delayTicks = 2;
    private boolean smoothRotation = true;
    private float maxRotationSpeed = 30.0f;
    private String paintOrder = "Color-Optimized";
    private boolean autoVerify = false;
    private boolean autoSave = false;
    private String saveName = "";
    private final Queue<ActionStep> stepQueue = new LinkedList<ActionStep>();
    private int tickCounter = 0;
    private int totalPixelsToDraw = 0;
    private int drawnPixelsCount = 0;
    private int currentPass = 1;
    private String statusMessage = "Idle";
    private String missingItemName = null;
    private ArtMapPalette.MappedColor[][] targetColors = new ArtMapPalette.MappedColor[128][128];
    private ItemFrame activeCanvas = null;
    private final ArtMapPalette palette = new ArtMapPalette();
    private static float[][][] eastRotations = null;
    private static float[][][] westRotations = null;
    private static float[][][] northRotations = null;
    private static float[][][] southRotations = null;

    private AutoPainter() {
    }

    public boolean isActive() {
        return this.active;
    }

    public void startPainting(ArtMapPalette.MappedColor[][] colors, int delay, boolean smooth, float rotSpeed, String order, boolean verify, boolean autoSave, String saveName) {
        if (this.client.player == null || this.client.level == null) {
            return;
        }
        this.activeCanvas = this.findEaselCanvas();
        if (this.activeCanvas == null) {
            this.client.player.sendSystemMessage((Component)Component.literal((String)"\u00a7cCould not find ArtMap canvas! Please sit on the easel."));
            return;
        }
        this.targetColors = colors;
        this.delayTicks = delay;
        this.smoothRotation = smooth;
        this.maxRotationSpeed = rotSpeed;
        this.paintOrder = order;
        this.autoVerify = verify;
        this.autoSave = autoSave;
        this.saveName = saveName;
        this.stepQueue.clear();
        this.tickCounter = 0;
        this.currentPass = 1;
        this.missingItemName = null;
        List<PixelDrawAction> actions = this.buildPaintActions();
        if (actions.isEmpty()) {
            this.client.player.sendSystemMessage((Component)Component.literal((String)"\u00a7aDrawing completed! No changes needed."));
            return;
        }
        this.totalPixelsToDraw = actions.size();
        this.drawnPixelsCount = 0;
        this.statusMessage = "Queuing actions...";
        this.buildStepQueue(actions);
        this.active = true;
        this.statusMessage = "Painting...";
        this.client.player.sendSystemMessage((Component)Component.literal((String)("\u00a7aStarting ArtMap Auto-Draw (" + this.totalPixelsToDraw + " pixels)...")));
    }

    public void stopPainting() {
        this.active = false;
        this.stepQueue.clear();
        this.statusMessage = "Stopped";
    }

    public int getProgressPercent() {
        if (this.totalPixelsToDraw == 0) {
            return 0;
        }
        return this.drawnPixelsCount * 100 / this.totalPixelsToDraw;
    }

    public String getStatusText() {
        if (this.missingItemName != null) {
            return "\u00a7cMissing: " + this.missingItemName;
        }
        return this.active ? "Painting: " + this.drawnPixelsCount + " / " + this.totalPixelsToDraw + " (" + this.getProgressPercent() + "%)" : "Idle";
    }

    public void clientTick() {
        if (!this.active) {
            return;
        }
        if (this.client.player == null || this.client.level == null || this.activeCanvas == null || !this.activeCanvas.isAlive()) {
            this.stopPainting();
            return;
        }
        if (this.tickCounter > 0) {
            --this.tickCounter;
            return;
        }
        if (this.stepQueue.isEmpty()) {
            this.stopPainting();
            this.onPaintingFinished();
            return;
        }
        while (!this.stepQueue.isEmpty()) {
            ActionStep step = this.stepQueue.peek();
            if (step.type == ActionStep.Type.WAIT_TICKS) {
                this.tickCounter = step.ticks;
                this.stepQueue.poll();
                return;
            }
            if (step.type == ActionStep.Type.CHECK_PASS) {
                this.stepQueue.poll();
                List<PixelDrawAction> remaining = this.buildPaintActions();
                if (remaining.isEmpty() || this.currentPass >= 5) {
                    this.statusMessage = "Finished";
                    this.active = false;
                    this.onPaintingFinished();
                } else {
                    ++this.currentPass;
                    if (this.client.player != null) {
                        this.client.player.sendSystemMessage((Component)Component.literal((String)("\u00a7aStarting pass " + this.currentPass + " (" + remaining.size() + " pixels remaining)...")));
                    }
                    this.totalPixelsToDraw = remaining.size();
                    this.drawnPixelsCount = 0;
                    this.buildStepQueue(remaining);
                }
                return;
            }
            if (step.type == ActionStep.Type.EQUIP_ITEM) {
                int delay = this.tryEquipItem(step.itemId);
                if (delay == -1) {
                    this.missingItemName = this.getDisplayNameFor(step.itemId);
                    this.client.player.sendSystemMessage((Component)Component.literal((String)("\u00a7cStopping: Missing " + this.missingItemName + "!")));
                    this.stopPainting();
                    return;
                }
                this.stepQueue.poll();
                this.tickCounter = delay;
                return;
            }
            if (step.type == ActionStep.Type.ROTATE_TO) {
                if (this.smoothRotation) {
                    if (!this.smoothRotateTowards(step.targetYaw, step.targetPitch)) {
                        return;
                    }
                } else {
                    this.client.player.setYRot((float)step.targetYaw);
                    this.client.player.setXRot((float)step.targetPitch);
                    this.client.player.connection.send((Packet)new ServerboundMovePlayerPacket.Rot((float)step.targetYaw, (float)step.targetPitch, this.client.player.onGround(), this.client.player.horizontalCollision));
                }
                this.stepQueue.poll();
                continue;
            }
            if (step.type == ActionStep.Type.CLICK_CANVAS) {
                this.client.gameMode.attack((Player)this.client.player, (Entity)this.activeCanvas);
                ++this.drawnPixelsCount;
                this.stepQueue.poll();
                this.tickCounter = Math.max(0, this.delayTicks);
                return;
            }
            if (step.type != ActionStep.Type.RIGHT_CLICK_CANVAS) continue;
            this.client.gameMode.interact((Player)this.client.player, (Entity)this.activeCanvas, new net.minecraft.world.phys.EntityHitResult(this.activeCanvas), InteractionHand.MAIN_HAND);
            ++this.drawnPixelsCount;
            this.stepQueue.poll();
            this.tickCounter = Math.max(0, this.delayTicks);
            return;
        }
    }

    private int tryEquipItem(String itemId) {
        if (this.client.player == null || this.client.gameMode == null) {
            return -1;
        }
        int invSlot = this.findItemInInventory(itemId);
        if (invSlot == -1) {
            return -1;
        }
        int currentSlot = this.client.player.getInventory().getSelectedSlot();
        if (invSlot >= 0 && invSlot <= 8) {
            if (currentSlot != invSlot) {
                this.client.player.getInventory().setSelectedSlot(invSlot);
                this.client.player.connection.send((Packet)new ServerboundSetCarriedItemPacket(invSlot));
                return 2;
            }
            return 1;
        }
        int targetHotbarSlot = 0;
        int screenHandlerSlot = invSlot;
        this.client.gameMode.handleContainerInput(this.client.player.inventoryMenu.containerId, screenHandlerSlot, targetHotbarSlot, ContainerInput.SWAP, (Player)this.client.player);
        if (currentSlot != targetHotbarSlot) {
            this.client.player.getInventory().setSelectedSlot(targetHotbarSlot);
            this.client.player.connection.send((Packet)new ServerboundSetCarriedItemPacket(targetHotbarSlot));
        }
        return 5;
    }

    private int findItemInInventory(String itemId) {
        Identifier itemKey;
        ItemStack stack;
        if (this.client.player == null) {
            return -1;
        }
        Identifier targetId = Identifier.parse((String)itemId);
        for (int i = 0; i < 36; ++i) {
            Identifier itemKey2;
            ItemStack stack2 = this.client.player.getInventory().getItem(i);
            if (stack2.isEmpty() || !(itemKey2 = BuiltInRegistries.ITEM.getKey(stack2.getItem())).equals((Object)targetId)) continue;
            return i;
        }
        if (itemId.equals("minecraft:coal") || itemId.equals("minecraft:charcoal")) {
            Identifier fallbackId = Identifier.parse((String)(itemId.equals("minecraft:coal") ? "minecraft:charcoal" : "minecraft:coal"));
            for (int i = 0; i < 36; ++i) {
                stack = this.client.player.getInventory().getItem(i);
                if (stack.isEmpty() || !(itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem())).equals((Object)fallbackId)) continue;
                return i;
            }
        }
        if (itemId.equals("minecraft:white_dye") || itemId.equals("minecraft:bone_meal")) {
            Identifier fallbackId = Identifier.parse((String)(itemId.equals("minecraft:white_dye") ? "minecraft:bone_meal" : "minecraft:white_dye"));
            for (int i = 0; i < 36; ++i) {
                stack = this.client.player.getInventory().getItem(i);
                if (stack.isEmpty() || !(itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem())).equals((Object)fallbackId)) continue;
                return i;
            }
        }
        return -1;
    }

    private String getDisplayNameFor(String itemId) {
        Identifier targetId = Identifier.parse((String)itemId);
        return ((Item)BuiltInRegistries.ITEM.getValue(targetId)).getName(new ItemStack((Item)BuiltInRegistries.ITEM.getValue(targetId))).getString();
    }

    private boolean smoothRotateTowards(double targetYaw, double targetPitch) {
        boolean pitchAligned;
        if (this.client.player == null) {
            return true;
        }
        float yaw = this.client.player.getYRot();
        float pitch = this.client.player.getXRot();
        double yawDiff = Mth.wrapDegrees((double)(targetYaw - (double)yaw));
        double pitchDiff = targetPitch - (double)pitch;
        boolean yawAligned = Math.abs(yawDiff) < 1.0;
        boolean bl = pitchAligned = Math.abs(pitchDiff) < 1.0;
        if (yawAligned && pitchAligned) {
            this.client.player.setYRot((float)targetYaw);
            this.client.player.setXRot((float)targetPitch);
            this.client.player.connection.send((Packet)new ServerboundMovePlayerPacket.Rot((float)targetYaw, (float)targetPitch, this.client.player.onGround(), this.client.player.horizontalCollision));
            return true;
        }
        float stepYaw = (float)((double)yaw + Math.signum(yawDiff) * Math.min((double)this.maxRotationSpeed, Math.abs(yawDiff)));
        float stepPitch = (float)((double)pitch + Math.signum(pitchDiff) * Math.min((double)this.maxRotationSpeed, Math.abs(pitchDiff)));
        this.client.player.setYRot(stepYaw);
        this.client.player.setXRot(stepPitch);
        this.client.player.connection.send((Packet)new ServerboundMovePlayerPacket.Rot(stepYaw, stepPitch, this.client.player.onGround(), this.client.player.horizontalCollision));
        return false;
    }

    private ItemFrame findEaselCanvas() {
        if (this.client.level == null || this.client.player == null) {
            return null;
        }
        List<ItemFrame> frames = this.client.level.getEntitiesOfClass(ItemFrame.class, this.client.player.getBoundingBox().inflate(3.0), entity -> entity.getItem().getItem() == Items.FILLED_MAP);
        if (frames.isEmpty()) {
            return null;
        }
        ItemFrame closest = null;
        double minDist = Double.MAX_VALUE;
        for (ItemFrame frame : frames) {
            double dist = frame.distanceToSqr((Entity)this.client.player);
            if (!(dist < minDist)) continue;
            minDist = dist;
            closest = frame;
        }
        return closest;
    }

    private int getMapColorRgb(byte colorByte) {
        ArtMapPalette.MappedColor mc = this.palette.getByByte(colorByte);
        if (mc == null) {
            return 0;
        }
        return mc.r << 16 | mc.g << 8 | mc.b;
    }

    private List<PixelDrawAction> buildPaintActions() {
        MapItemSavedData mapState;
        ArrayList<PixelDrawAction> actions = new ArrayList<PixelDrawAction>();
        if (this.activeCanvas == null) {
            return actions;
        }
        ItemStack mapStack = this.activeCanvas.getItem();
        MapId mapId = (MapId)mapStack.get(DataComponents.MAP_ID);
        byte[] currentColors = null;
        if (mapId != null && this.client.level != null && (mapState = this.client.level.getMapData(mapId)) != null) {
            currentColors = mapState.colors;
        }
        for (int y = 0; y < 128; ++y) {
            for (int x = 0; x < 128; ++x) {
                int curB;
                int bDiff;
                int curG;
                int gDiff;
                int currentRgb;
                int curR;
                int rDiff;
                double dist;
                byte currentByte;
                int colorId;
                int pixelIndex;
                ArtMapPalette.MappedColor color = this.targetColors[x][y];
                if (color == null || currentColors != null && (pixelIndex = x + y * 128) >= 0 && pixelIndex < currentColors.length && (colorId = ((currentByte = currentColors[pixelIndex]) & 0xFF) / 4) != 0 && (dist = Math.sqrt((rDiff = (curR = (currentRgb = this.getMapColorRgb(currentByte)) >> 16 & 0xFF) - color.r) * rDiff + (gDiff = (curG = currentRgb >> 8 & 0xFF) - color.g) * gDiff + (bDiff = (curB = currentRgb & 0xFF) - color.b) * bDiff)) < 8.0) continue;
                actions.add(new PixelDrawAction(x, y, color));
            }
        }
        this.sortActions(actions);
        return actions;
    }

    private void sortActions(List<PixelDrawAction> actions) {
        if ("Row by Row (L to R)".equalsIgnoreCase(this.paintOrder)) {
            actions.sort(Comparator.<PixelDrawAction>comparingInt(a -> a.y).thenComparingInt(a -> a.x));
        } else if ("Row by Row (R to L)".equalsIgnoreCase(this.paintOrder)) {
            actions.sort(Comparator.<PixelDrawAction>comparingInt(a -> a.y).thenComparing((a, b) -> Integer.compare(b.x, a.x)));
        } else if ("Column by Column".equalsIgnoreCase(this.paintOrder)) {
            actions.sort(Comparator.<PixelDrawAction>comparingInt(a -> a.x).thenComparingInt(a -> a.y));
        } else if ("Snake".equalsIgnoreCase(this.paintOrder)) {
            actions.sort((a, b) -> {
                if (a.y != b.y) {
                    return Integer.compare(a.y, b.y);
                }
                return a.y % 2 == 0 ? Integer.compare(a.x, b.x) : Integer.compare(b.x, a.x);
            });
        } else if ("Color-Optimized".equalsIgnoreCase(this.paintOrder)) {
            actions.sort(Comparator.<PixelDrawAction, String>comparing(a -> a.color.baseEntry.itemId).thenComparingInt(a -> a.y).thenComparingInt(a -> a.x));
        }
    }

    private synchronized void loadRotationsIfNeeded() {
        if (eastRotations != null) {
            return;
        }
        eastRotations = this.loadRotationAsset("/assets/autopalette/east.ser");
        westRotations = this.loadRotationAsset("/assets/autopalette/west.ser");
        northRotations = this.loadRotationAsset("/assets/autopalette/north.ser");
        southRotations = this.loadRotationAsset("/assets/autopalette/south.ser");
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private float[][][] loadRotationAsset(String resourcePath) {
        float[][][] rotations = new float[128][128][2];
        try (InputStream is = AutoPainter.class.getResourceAsStream(resourcePath);){
            if (is == null) {
                System.err.println("[AutoPalette] Rotation asset not found: " + resourcePath);
                float[][][] fArray = rotations;
                return fArray;
            }
            try (ObjectInputStream ois = new ObjectInputStream(is);){
                Map map = (Map)ois.readObject();
                Iterator<Map.Entry> iterator = map.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry entry = iterator.next();
                    int x = (Integer)((ArrayList)entry.getKey()).get(0);
                    int y = (Integer)((ArrayList)entry.getKey()).get(1);
                    float yaw = ((Float)((ArrayList)entry.getValue()).get(0)).floatValue();
                    float pitch = ((Float)((ArrayList)entry.getValue()).get(1)).floatValue();
                    if (x < 0 || x >= 128 || y < 0 || y >= 128) continue;
                    rotations[x][y][0] = yaw;
                    rotations[x][y][1] = pitch;
                }
                return rotations;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return rotations;
    }

    private void buildStepQueue(List<PixelDrawAction> actions) {
        Direction facing;
        this.stepQueue.clear();
        if (this.activeCanvas == null) {
            return;
        }
        this.loadRotationsIfNeeded();
        Direction direction = facing = this.client.player != null ? this.client.player.getDirection() : Direction.NORTH;
        float[][][] rotationsCache = facing == Direction.EAST ? eastRotations : (facing == Direction.WEST ? westRotations : (facing == Direction.NORTH ? northRotations : (facing == Direction.SOUTH ? southRotations : northRotations)));
        String lastItem = null;
        for (PixelDrawAction act : actions) {
            float yaw = rotationsCache[act.x][act.y][0];
            float pitch = rotationsCache[act.x][act.y][1];
            this.stepQueue.add(ActionStep.rotate(yaw, pitch));
            String baseDye = act.color.baseEntry.itemId;
            if (!baseDye.equals(lastItem)) {
                this.stepQueue.add(ActionStep.equip(baseDye));
                lastItem = baseDye;
            }
            this.stepQueue.add(ActionStep.click());
            if (act.color.shade == ArtMapPalette.ShadeLevel.LIGHTENED) {
                this.stepQueue.add(ActionStep.equip("minecraft:feather"));
                lastItem = "minecraft:feather";
                this.stepQueue.add(ActionStep.click());
                continue;
            }
            if (act.color.shade == ArtMapPalette.ShadeLevel.DARKENED_1) {
                this.stepQueue.add(ActionStep.equip("minecraft:coal"));
                lastItem = "minecraft:coal";
                this.stepQueue.add(ActionStep.click());
                continue;
            }
            if (act.color.shade != ArtMapPalette.ShadeLevel.DARKENED_2) continue;
            this.stepQueue.add(ActionStep.equip("minecraft:coal"));
            lastItem = "minecraft:coal";
            this.stepQueue.add(ActionStep.click());
            this.stepQueue.add(ActionStep.click());
        }
        if (this.autoVerify) {
            this.stepQueue.add(ActionStep.waitTicks(20));
            this.stepQueue.add(ActionStep.checkPass());
        }
    }

    private void onPaintingFinished() {
        if (this.client.player != null) {
            this.client.player.sendSystemMessage((Component)Component.literal((String)"\u00a7aDrawing successfully finished!"));
            if (this.autoSave && this.saveName != null && !this.saveName.trim().isEmpty()) {
                this.client.player.connection.sendCommand("artmap save " + this.saveName.trim());
            }
        }
    }

    public static class ActionStep {
        public final Type type;
        public final String itemId;
        public final double targetYaw;
        public final double targetPitch;
        public final int ticks;

        public ActionStep(Type type, String itemId, double targetYaw, double targetPitch, int ticks) {
            this.type = type;
            this.itemId = itemId;
            this.targetYaw = targetYaw;
            this.targetPitch = targetPitch;
            this.ticks = ticks;
        }

        public static ActionStep equip(String itemId) {
            return new ActionStep(Type.EQUIP_ITEM, itemId, 0.0, 0.0, 0);
        }

        public static ActionStep rotate(double yaw, double pitch) {
            return new ActionStep(Type.ROTATE_TO, null, yaw, pitch, 0);
        }

        public static ActionStep click() {
            return new ActionStep(Type.CLICK_CANVAS, null, 0.0, 0.0, 0);
        }

        public static ActionStep rightClick() {
            return new ActionStep(Type.RIGHT_CLICK_CANVAS, null, 0.0, 0.0, 0);
        }

        public static ActionStep waitTicks(int ticks) {
            return new ActionStep(Type.WAIT_TICKS, null, 0.0, 0.0, ticks);
        }

        public static ActionStep checkPass() {
            return new ActionStep(Type.CHECK_PASS, null, 0.0, 0.0, 0);
        }

        public static enum Type {
            EQUIP_ITEM,
            ROTATE_TO,
            CLICK_CANVAS,
            RIGHT_CLICK_CANVAS,
            WAIT_TICKS,
            CHECK_PASS;

        }
    }

    public static class PixelDrawAction {
        public final int x;
        public final int y;
        public final ArtMapPalette.MappedColor color;

        public PixelDrawAction(int x, int y, ArtMapPalette.MappedColor color) {
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }
}
