package com.jaredscarito.factions.data;

public final class Home {
    public String world;
    public double x;
    public double y;
    public double z;
    public float pitch;
    public float yaw;
    public float roll;

    public Home copy() {
        Home copy = new Home();
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
