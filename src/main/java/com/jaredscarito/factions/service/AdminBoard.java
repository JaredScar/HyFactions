package com.jaredscarito.factions.service;

import java.util.List;

public final class AdminBoard {
    private AdminBoard() {
    }

    public record Row(String id, String name, String color, String summary, boolean system) {
    }

    public record Detail(
            String id,
            String name,
            String color,
            boolean system,
            String stats,
            String flags,
            String about,
            List<String> members,
            boolean open,
            boolean peaceful,
            boolean permanent,
            boolean locked,
            String memberStat,
            String powerStat,
            String landStat,
            String bankStat,
            String boostStat,
            boolean exposed
    ) {
    }

    public record Snapshot(boolean bypass, String here, List<Row> factions, Detail detail) {
    }
}
