package com.jaredscarito.factions.system;

import com.jaredscarito.factions.data.BuildCheck;
import com.jaredscarito.factions.service.FactionService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

final class ClaimGuard {
    private ClaimGuard() {}

    static boolean deny(FactionService factions, ArchetypeChunk<EntityStore> chunk, int index, Store<EntityStore> store, Vector3i block, String useBlockId) {
        if (block == null) {
            return false;
        }
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        if (player == null) {
            return false;
        }
        String world = store.getExternalData().getWorld().getName();
        BuildCheck check = factions.checkAccess(player.getUuid(), world, block.x, block.z, useBlockId);
        if (check.allowed) {
            return false;
        }
        factions.warnDenied(player, check.ownerName);
        return true;
    }
}
