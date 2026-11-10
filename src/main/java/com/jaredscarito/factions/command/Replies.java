package com.jaredscarito.factions.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

final class Replies {
    private Replies() {}

    static void reply(PlayerRef player, Message message) {
        if (message != null) {
            player.sendMessage(message);
        }
    }
}
