package com.jaredscarito.factions.system;

import com.jaredscarito.factions.service.FactionService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class UseGuard extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    private final FactionService factions;

    public UseGuard(FactionService factions) {
        super(UseBlockEvent.Pre.class);
        this.factions = factions;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer, UseBlockEvent.Pre event) {
        if (event.isCancelled()) {
            return;
        }
        String blockId = event.getBlockType() == null ? "" : event.getBlockType().getId();
        if (ClaimGuard.deny(factions, chunk, index, store, event.getTargetBlock(), blockId)) {
            event.setCancelled(true);
        }
    }
}
