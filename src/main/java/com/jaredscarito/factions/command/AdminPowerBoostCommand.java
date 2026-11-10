package com.jaredscarito.factions.command;

import com.jaredscarito.factions.service.FactionService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

final class AdminPowerBoostCommand extends AbstractPlayerCommand {
    private final FactionService factions;
    private final RequiredArg<String> nameArg;
    private final RequiredArg<Double> amountArg;

    AdminPowerBoostCommand(FactionService factions) {
        super("powerboost", "Set a player's power boost");
        requirePermission("factions.admin");
        this.factions = factions;
        this.nameArg = withRequiredArg("player", "Player name", ArgTypes.STRING);
        this.amountArg = withRequiredArg("amount", "Boost amount", ArgTypes.DOUBLE);
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
        Replies.reply(player, factions.adminPowerBoost(context.get(nameArg), context.get(amountArg)));
    }
}
