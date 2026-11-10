package com.jaredscarito.factions.command;

import com.jaredscarito.factions.service.FactionService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

final class MoneyCommand extends AbstractPlayerCommand {
    private final FactionService factions;

    MoneyCommand(FactionService factions) {
        super("money", "Show your balance and the faction bank");
        requireNoPermission();
        this.factions = factions;
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
        Replies.reply(player, factions.money(player));
    }
}
