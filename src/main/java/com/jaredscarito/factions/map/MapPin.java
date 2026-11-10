package com.jaredscarito.factions.map;

public final class MapPin {
    public final String id;
    public final String name;
    public final String color;
    public final double x;
    public final double y;
    public final double z;

    public MapPin(String id, String name, String color, double x, double y, double z) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
