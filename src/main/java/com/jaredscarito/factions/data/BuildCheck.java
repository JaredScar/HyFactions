package com.jaredscarito.factions.data;

public final class BuildCheck {
    public final boolean allowed;
    public final String ownerName;

    private BuildCheck(boolean allowed, String ownerName) {
        this.allowed = allowed;
        this.ownerName = ownerName;
    }

    public static BuildCheck allow() {
        return new BuildCheck(true, "");
    }

    public static BuildCheck deny(String ownerName) {
        return new BuildCheck(false, ownerName);
    }
}
