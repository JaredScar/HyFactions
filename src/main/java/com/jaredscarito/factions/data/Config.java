package com.jaredscarito.factions.data;

public final class Config {
    public double maxPower = 10;
    public double minPower = 0;
    public double powerLossOnDeath = 2;
    public double powerRegenPerMinute = 0.5;
    public int offlineDecayGraceHours = 24;
    public double offlineDecayPerHour = 1;
    public double claimCost = 10;
    public double startingBalance = 250;
    public int raidStartHour = -1;
    public int raidEndHour = -1;
    public int homeWarmupSeconds = 3;
    public int inviteMinutes = 5;
    public int minNameLength = 3;
    public int maxNameLength = 16;
    public boolean friendlyFire = false;
    public boolean allyPvp = false;
    public boolean trucePvp = false;
    public boolean enemyPvp = true;
    public boolean neutralPvpInClaims = false;
    public boolean neutralPvpInWilderness = true;
    public boolean allyBuild = false;
    public boolean allyContainer = false;
    public boolean allyDoor = true;
    public boolean allyButton = true;
    public boolean truceBuild = false;
    public boolean truceContainer = false;
    public boolean truceDoor = true;
    public boolean truceButton = true;
    public boolean neutralBuild = false;
    public boolean neutralContainer = false;
    public boolean neutralDoor = false;
    public boolean neutralButton = true;
    public boolean enemyBuild = false;
    public boolean enemyContainer = false;
    public boolean enemyDoor = false;
    public boolean enemyButton = false;
    public boolean unclaimRefund = true;
    public boolean protectOfflineFactions = false;
    public boolean allyEnter = true;
    public boolean truceEnter = true;
    public boolean neutralEnter = true;
    public boolean enemyEnter = true;
    public int stuckCooldownSeconds = 30;
    public String note = "Power is per player. A faction can hold as many chunks as its members' power, rounded down. Land past that can be overclaimed by enemies and neutrals. Permanent, peaceful, ally, and truce land cannot. protectOfflineFactions blocks overclaim and explosion damage while every member is offline. claimCost is paid by the claiming player and refunded on unclaim when unclaimRefund is true. Power boosts are separate from power and are not changed by death or regen. raidStartHour and raidEndHour use the server clock; -1 means raids are always open. Player explosion and fire damage in claims is blocked while raids are closed. Block damage from explosions cannot be blocked. Enemy entry is allowed unless a faction sets /f entry deny. Grant factions.admin for /f admin.";

    public static Config defaults() {
        return new Config();
    }

    public void applyDefaults() {
        if (maxPower <= 0) {
            maxPower = 10;
        }
        if (minPower < 0 || minPower > maxPower) {
            minPower = 0;
        }
        if (powerLossOnDeath < 0) {
            powerLossOnDeath = 2;
        }
        if (powerRegenPerMinute < 0) {
            powerRegenPerMinute = 0.5;
        }
        if (offlineDecayGraceHours < 0) {
            offlineDecayGraceHours = 24;
        }
        if (offlineDecayPerHour < 0) {
            offlineDecayPerHour = 1;
        }
        if (claimCost < 0) {
            claimCost = 10;
        }
        if (startingBalance < 0) {
            startingBalance = 250;
        }
        if (raidStartHour < -1 || raidStartHour > 23) {
            raidStartHour = -1;
        }
        if (raidEndHour < -1 || raidEndHour > 23) {
            raidEndHour = -1;
        }
        if (homeWarmupSeconds < 0) {
            homeWarmupSeconds = 3;
        }
        if (inviteMinutes <= 0) {
            inviteMinutes = 5;
        }
        if (minNameLength <= 0) {
            minNameLength = 3;
        }
        if (maxNameLength < minNameLength) {
            maxNameLength = 16;
        }
        if (stuckCooldownSeconds <= 0) {
            stuckCooldownSeconds = 30;
        }
        if (note == null || note.isBlank()) {
            note = defaults().note;
        }
    }
}
