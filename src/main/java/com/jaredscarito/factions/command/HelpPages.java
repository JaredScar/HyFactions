package com.jaredscarito.factions.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.jaredscarito.factions.service.FactionService;
import com.jaredscarito.factions.ui.FactionHelpPage;

final class HelpPages {
    private HelpPages() {
    }

    static void open(FactionService factions, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player) {
        Player entity = store.getComponent(ref, Player.getComponentType());
        if (entity == null) {
            factions.help(player);
            return;
        }
        entity.getPageManager().openCustomPage(ref, store, new FactionHelpPage(player, factions));
    }
}
