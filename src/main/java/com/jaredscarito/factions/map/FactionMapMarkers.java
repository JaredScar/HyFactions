package com.jaredscarito.factions.map;

import com.jaredscarito.factions.service.FactionService;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;

public final class FactionMapMarkers implements WorldMapManager.MarkerProvider {
    private final FactionService factions;

    public FactionMapMarkers(FactionService factions) {
        this.factions = factions;
    }

    @Override
    public void update(World world, Player player, MarkersCollector collector) {
        if (world == null || player == null || player.getPlayerRef() == null) {
            return;
        }
        for (MapPin pin : factions.mapPins(player.getPlayerRef().getUuid(), world.getName())) {
            MapMarker marker = new MapMarkerBuilder(pin.id, "Home.png", new Transform(pin.x, pin.y, pin.z))
                    .withName(Message.raw(pin.name).color(pin.color))
                    .build();
            collector.addIgnoreViewDistance(marker);
        }
    }
}

