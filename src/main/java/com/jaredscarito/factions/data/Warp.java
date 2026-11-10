package com.jaredscarito.factions.data;

public final class Warp {
    public String name;
    public String password = "";
    public String world;
    public double x;
    public double y;
    public double z;
    public float pitch;
    public float yaw;
    public float roll;

    public Warp copy() {
        Warp copy = new Warp();
        copy.name = name;
        copy.password = password;
        copy.world = world;
        copy.x = x;
        copy.y = y;
        copy.z = z;
        copy.pitch = pitch;
        copy.yaw = yaw;
        copy.roll = roll;
        return copy;
    }
}
