package com.jaredscarito.factions.system;

import com.jaredscarito.factions.service.FactionService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class CombatGuard extends EntityEventSystem<EntityStore, Damage> {
    private final FactionService factions;

    public CombatGuard(FactionService factions) {
        super(Damage.class);
        this.factions = factions;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        DamageModule module = DamageModule.get();
        return module == null ? null : module.getFilterDamageGroup();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer, Damage event) {
        if (event.isCancelled()) {
            return;
        }
        PlayerRef victim = chunk.getComponent(index, PlayerRef.getComponentType());
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (victim == null || transform == null) {
            return;
        }
        String world = store.getExternalData().getWorld().getName();
        int x = (int) Math.floor(transform.getPosition().x);
        int z = (int) Math.floor(transform.getPosition().z);
        PlayerRef attacker = attacker(store, event);
        if (attacker != null) {
            if (!factions.allowPlayerDamage(attacker.getUuid(), victim.getUuid(), world, x, z)) {
                event.setCancelled(true);
                factions.warn(attacker, "You cannot fight here.");
                return;
            }
            if (store.getExternalData().getWorld().getWorldConfig().isPvpEnabled()) {
                factions.cancelHome(victim.getUuid());
            }
            return;
        }
        DamageCause cause = DamageCause.getAssetMap().getAsset(event.getDamageCauseIndex());
        String causeId = cause == null || cause.getId() == null ? "" : cause.getId();
        if (!factions.allowHazardDamage(victim.getUuid(), world, x, z, causeId)) {
            event.setCancelled(true);
        }
    }

    private static PlayerRef attacker(Store<EntityStore> store, Damage event) {
        if (!(event.getSource() instanceof Damage.EntitySource source)) {
            return null;
        }
        Ref<EntityStore> ref = source.getRef();
        if (ref == null) {
            return null;
        }
        return store.getComponent(ref, PlayerRef.getComponentType());
    }
}
