package com.example.autopalette.client.painter;

public final class CanvasLayout {
    public static final int MAP_SIZE = 128;

    private CanvasLayout() {
    }

    public static int mapCoordinate(int pixel, int resolution) {
        if (resolution != 32 && resolution != 64 && resolution != 128) {
            throw new IllegalArgumentException("Unsupported canvas resolution: " + resolution);
        }
        if (pixel < 0 || pixel >= resolution) {
            throw new IndexOutOfBoundsException(pixel);
        }
        int scale = MAP_SIZE / resolution;
        return pixel * scale + scale / 2;
    }
}
