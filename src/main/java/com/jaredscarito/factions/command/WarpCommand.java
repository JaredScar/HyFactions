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

final class WarpCommand extends AbstractPlayerCommand {
    private final FactionService factions;
    private final RequiredArg<String> nameArg;
    private final RequiredArg<String> passwordArg;

    WarpCommand(FactionService factions) {
        super("warp", "Teleport to a faction warp");
        requireNoPermission();
        this.factions = factions;
        this.nameArg = withRequiredArg("name", "Warp name", ArgTypes.STRING);
        this.passwordArg = withRequiredArg("password", "Warp password", ArgTypes.STRING);
        addUsageVariant(new WarpOpenCommand(factions));
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
        Replies.reply(player, factions.warp(player, context.get(nameArg), context.get(passwordArg)));
    }
}
