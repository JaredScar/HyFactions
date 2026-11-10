package com.jaredscarito.factions.map;

import com.jaredscarito.factions.service.FactionService;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.palette.BitFieldArr;
import com.hypixel.hytale.server.core.universe.world.map.WorldMap;
import com.hypixel.hytale.server.core.universe.world.worldmap.IWorldMap;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapSettings;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class FactionClaimMap implements IWorldMap {
    private final IWorldMap delegate;
    private final FactionService factions;

    public FactionClaimMap(IWorldMap delegate, FactionService factions) {
        this.delegate = delegate;
        this.factions = factions;
    }

    @Override
    public WorldMapSettings getWorldMapSettings() {
        return delegate.getWorldMapSettings();
    }

    @Override
    public CompletableFuture<WorldMap> generate(World world, int imageX, int imageZ, LongSet chunks) {
        return delegate.generate(world, imageX, imageZ, chunks).thenApply(map -> {
            tint(world, map);
            return map;
        });
    }

    @Override
    public CompletableFuture<Map<String, MapMarker>> generatePointsOfInterest(World world) {
        return delegate.generatePointsOfInterest(world);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    private void tint(World world, WorldMap map) {
        if (world == null || map == null || map.getChunks() == null) {
            return;
        }
        String worldName = world.getName();
        for (Long2ObjectMap.Entry<MapImage> entry : map.getChunks().long2ObjectEntrySet()) {
            MapImage image = entry.getValue();
            if (image == null) {
                continue;
            }
            int chunkX = ChunkUtil.xOfChunkIndex(entry.getLongKey());
            int chunkZ = ChunkUtil.zOfChunkIndex(entry.getLongKey());
            int color = factions.claimTint(worldName, chunkX, chunkZ);
            int north = factions.claimTint(worldName, chunkX, chunkZ - 1);
            int south = factions.claimTint(worldName, chunkX, chunkZ + 1);
            int west = factions.claimTint(worldName, chunkX - 1, chunkZ);
            int east = factions.claimTint(worldName, chunkX + 1, chunkZ);
            if (color == 0 && north == 0 && south == 0 && west == 0 && east == 0) {
                continue;
            }
            try {
                MapImage tinted = color == 0
                        ? paint(image, 0, OPEN_EDGE, north != 0, south != 0, west != 0, east != 0, 4, 48, false)
                        : paint(image, color, color, north != color, south != color, west != color, east != color, 6, 90, true);
                if (tinted != null) {
                    entry.setValue(tinted);
                }
            } catch (RuntimeException exception) {
                // Leave the terrain tile alone if this image cannot be recolored.
            }
        }
    }

    private static final int OPEN_EDGE = 0xE7E1D2;

    private static MapImage paint(MapImage image, int fill, int border, boolean north, boolean south, boolean west, boolean east, int thickness, int borderWeight, boolean darkenBorder) {
        if (image.width <= 0 || image.height <= 0 || image.palette == null || image.packedIndices == null || image.bitsPerIndex <= 0) {
            return null;
        }
        int count = image.width * image.height;
        BitFieldArr source = new BitFieldArr(image.bitsPerIndex, count);
        source.set(image.packedIndices);
        int[] pixels = new int[count];
        int band = Math.max(2, Math.min(thickness, Math.min(image.width, image.height) / 8));
        for (int index = 0; index < count; index++) {
            int paletteIndex = source.get(index);
            int pixel = paletteIndex >= 0 && paletteIndex < image.palette.length ? image.palette[paletteIndex] : 0;
            int x = index % image.width;
            int z = index / image.width;
            int edge = 0;
            if (north) {
                edge = Math.max(edge, band - z);
            }
            if (south) {
                edge = Math.max(edge, band - (image.height - 1 - z));
            }
            if (west) {
                edge = Math.max(edge, band - x);
            }
            if (east) {
                edge = Math.max(edge, band - (image.width - 1 - x));
            }
            if (edge > 0) {
                pixels[index] = mix(pixel, border, borderWeight, darkenBorder && edge > band / 2);
            } else if (fill != 0) {
                pixels[index] = mix(pixel, fill, 62, false);
            } else {
                pixels[index] = pixel;
            }
        }
        Map<Integer, Integer> colorToIndex = new LinkedHashMap<>();
        List<Integer> palette = new ArrayList<>();
        for (int pixel : pixels) {
            if (!colorToIndex.containsKey(pixel)) {
                colorToIndex.put(pixel, palette.size());
                palette.add(pixel);
            }
        }
        int bits = bitsFor(palette.size());
        BitFieldArr packed = new BitFieldArr(bits, count);
        for (int index = 0; index < count; index++) {
            packed.set(index, colorToIndex.get(pixels[index]));
        }
        int[] paletteArray = new int[palette.size()];
        for (int index = 0; index < palette.size(); index++) {
            paletteArray[index] = palette.get(index);
        }
        return new MapImage(image.width, image.height, paletteArray, (byte) bits, packed.get());
    }

    private static int bitsFor(int colors) {
        if (colors <= 16) {
            return 4;
        }
        if (colors <= 256) {
            return 8;
        }
        if (colors <= 4096) {
            return 12;
        }
        return 16;
    }

    private static int mix(int pixel, int rgb, int weight, boolean darken) {
        int keep = 100 - weight;
        int red = (((pixel >>> 24) & 255) * keep + ((rgb >>> 16) & 255) * weight) / 100;
        int green = (((pixel >>> 16) & 255) * keep + ((rgb >>> 8) & 255) * weight) / 100;
        int blue = (((pixel >>> 8) & 255) * keep + (rgb & 255) * weight) / 100;
        if (darken) {
            red = red * 3 / 4;
            green = green * 3 / 4;
            blue = blue * 3 / 4;
        }
        return (red << 24) | (green << 16) | (blue << 8) | (pixel & 255);
    }
}

