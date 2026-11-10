package com.jaredscarito.factions.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.jaredscarito.factions.service.FactionService;
import com.jaredscarito.factions.text.Texts;
import com.jaredscarito.factions.ui.ManagePage;

final class AdminCommand extends AbstractPlayerCommand {
    private final FactionService factions;

    AdminCommand(FactionService factions) {
        super("admin", "Open faction management");
        requirePermission("factions.admin");
        this.factions = factions;
        addSubCommand(new AdminBypassCommand(factions));
        addSubCommand(new AdminUnclaimCommand(factions));
        addSubCommand(new AdminDisbandCommand(factions));
        addSubCommand(new AdminSafezoneCommand(factions));
        addSubCommand(new AdminWarzoneCommand(factions));
        addSubCommand(new AdminGiveCommand(factions));
        addSubCommand(new AdminPowerCommand(factions));
        addSubCommand(new AdminPermanentCommand(factions));
        addSubCommand(new AdminLockCommand(factions));
        addSubCommand(new AdminClaimCommand(factions));
        addSubCommand(new AdminReloadCommand(factions));
        addSubCommand(new AdminPowerBoostCommand(factions));
        addSubCommand(new AdminFactionBoostCommand(factions));
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
        Player entity = store.getComponent(ref, Player.getComponentType());
        if (entity == null) {
            player.sendMessage(Texts.err("Faction management could not open."));
            return;
        }
        entity.getPageManager().openCustomPage(ref, store, new ManagePage(player, factions));
    }
}
