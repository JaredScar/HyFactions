package com.jaredscarito.factions.data;

import com.hypixel.hytale.math.vector.Rotation3f;
import org.joml.Vector3d;

public final class Spot {
    public String world;
    public double x;
    public double y;
    public double z;
    public float pitch;
    public float yaw;
    public float roll;

    public static Spot of(String world, Vector3d position, Rotation3f rotation) {
        Spot spot = new Spot();
        spot.world = world;
        spot.x = position.x;
        spot.y = position.y;
        spot.z = position.z;
        spot.pitch = rotation.pitch();
        spot.yaw = rotation.yaw();
        spot.roll = rotation.roll();
        return spot;
    }
}
