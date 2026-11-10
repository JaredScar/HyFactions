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

final class ClaimSquareCommand extends AbstractPlayerCommand {
    private final FactionService factions;
    private final RequiredArg<Integer> radiusArg;

    ClaimSquareCommand(FactionService factions) {
        super("claimsquare", "Claim a square around you");
        requireNoPermission();
        this.factions = factions;
        this.radiusArg = withRequiredArg("radius", "Radius in chunks, up to 7", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
        Replies.reply(player, factions.claimSquare(player, world, context.get(radiusArg)));
    }
}
