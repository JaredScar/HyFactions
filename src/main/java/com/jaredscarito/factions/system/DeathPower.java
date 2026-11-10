package com.jaredscarito.factions.system;

import com.jaredscarito.factions.service.FactionService;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class DeathPower extends DeathSystems.OnDeathSystem {
    private final FactionService factions;

    public DeathPower(FactionService factions) {
        this.factions = factions;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void onComponentAdded(Ref<EntityStore> ref, DeathComponent component, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        if (player != null) {
            factions.onDeath(player);
        }
    }
}
