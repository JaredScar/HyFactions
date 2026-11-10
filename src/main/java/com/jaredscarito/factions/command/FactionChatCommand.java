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

public final class FactionChatCommand extends AbstractPlayerCommand {
    private final FactionService factions;
    private final RequiredArg<String> messageArg;

    public FactionChatCommand(FactionService factions) {
        super("fc", "Send a message to your faction");
        requireNoPermission();
        addAliases("fchat");
        this.factions = factions;
        this.messageArg = withRequiredArg("message", "Message for your faction", ArgTypes.GREEDY_STRING);
        addUsageVariant(new FactionChatToggle(factions));
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
        Replies.reply(player, factions.say(player, context.get(messageArg)));
    }
}
