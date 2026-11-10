package com.jaredscarito.factions.text;

import com.hypixel.hytale.server.core.Message;

public final class Texts {
    private Texts() {}

    public static Message ok(String text) {
        return Message.raw(text).color("#55FF55");
    }

    public static Message err(String text) {
        return Message.raw(text).color("#FF5555");
    }

    public static Message info(String text) {
        return Message.raw(text).color("#AAAAAA");
    }

    public static Message text(String text, String hex) {
        return Message.raw(text).color(hex);
    }
}
