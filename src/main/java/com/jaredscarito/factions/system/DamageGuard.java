package com.jaredscarito.factions.system;

import com.jaredscarito.factions.service.FactionService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class DamageGuard extends EntityEventSystem<EntityStore, DamageBlockEvent> {
    private final FactionService factions;

    public DamageGuard(FactionService factions) {
        super(DamageBlockEvent.class);
        this.factions = factions;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer, DamageBlockEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (ClaimGuard.deny(factions, chunk, index, store, event.getTargetBlock(), null)) {
            event.setCancelled(true);
        }
    }
}
