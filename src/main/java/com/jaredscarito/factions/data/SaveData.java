package com.jaredscarito.factions.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SaveData {
    public Map<String, Faction> factions = new LinkedHashMap<>();
    public Map<String, String> claims = new LinkedHashMap<>();
    public Map<String, Invite> invites = new LinkedHashMap<>();
    public Map<String, Boolean> factionChat = new LinkedHashMap<>();
    public Map<String, Profile> profiles = new LinkedHashMap<>();
    public Map<String, Plot> plots = new LinkedHashMap<>();
    public List<String> bypassIds = new ArrayList<>();

    public void normalize() {
        if (factions == null) {
            factions = new LinkedHashMap<>();
        }
        if (claims == null) {
            claims = new LinkedHashMap<>();
        }
        if (invites == null) {
            invites = new LinkedHashMap<>();
        }
        if (factionChat == null) {
            factionChat = new LinkedHashMap<>();
        }
        if (profiles == null) {
            profiles = new LinkedHashMap<>();
        }
        if (plots == null) {
            plots = new LinkedHashMap<>();
        }
        if (bypassIds == null) {
            bypassIds = new ArrayList<>();
        }
        for (Faction faction : factions.values()) {
            if (faction.members == null) {
                faction.members = new LinkedHashMap<>();
            }
            if (faction.relations == null) {
                faction.relations = new LinkedHashMap<>();
            }
            if (faction.allyRequests == null) {
                faction.allyRequests = new ArrayList<>();
            }
            if (faction.truceRequests == null) {
                faction.truceRequests = new ArrayList<>();
            }
            if (faction.bans == null) {
                faction.bans = new ArrayList<>();
            }
            if (faction.banNames == null) {
                faction.banNames = new LinkedHashMap<>();
            }
            if (faction.warps == null) {
                faction.warps = new ArrayList<>();
            }
            if (faction.description == null) {
                faction.description = "";
            }
            if (faction.tag == null) {
                faction.tag = "";
            }
            if (faction.title == null) {
                faction.title = "";
            }
            if (faction.announcement == null) {
                faction.announcement = "";
            }
            if (faction.kind == null) {
                faction.kind = "";
            }
            if (faction.color == null || faction.color.isBlank()) {
                faction.color = "#FFAA00";
            }
        }
        for (Plot plot : plots.values()) {
            if (plot.access == null) {
                plot.access = new ArrayList<>();
            }
            if (plot.accessNames == null) {
                plot.accessNames = new ArrayList<>();
            }
        }
        for (Map.Entry<String, Boolean> entry : new ArrayList<>(factionChat.entrySet())) {
            if (!Boolean.TRUE.equals(entry.getValue())) {
                continue;
            }
            Profile profile = profiles.computeIfAbsent(entry.getKey(), key -> new Profile());
            if (profile.chatMode == null || profile.chatMode.isBlank() || "PUBLIC".equals(profile.chatMode)) {
                profile.chatMode = "FACTION";
            }
        }
        factionChat.clear();
    }
}
