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

final class WithdrawCommand extends AbstractPlayerCommand {
    private final FactionService factions;
    private final RequiredArg<Integer> amountArg;

    WithdrawCommand(FactionService factions) {
        super("withdraw", "Move money from the faction bank to your balance");
        requireNoPermission();
        this.factions = factions;
        this.amountArg = withRequiredArg("amount", "Amount to withdraw", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
        Replies.reply(player, factions.withdraw(player, context.get(amountArg)));
    }
}
