package com.example.routeplugin;

import org.bukkit.Location;
import org.bukkit.Particle;

import java.util.ArrayList;
import java.util.List;

public class Route {
    private final String name;
    private Particle particle;
    private final List<Location> points;

    public Route(String name, Particle particle) {
        this.name = name;
        this.particle = particle;
        this.points = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public Particle getParticle() {
        return particle;
    }

    public void setParticle(Particle particle) {
        this.particle = particle;
    }

    public void addPoint(Location loc) {
        points.add(loc.clone());
    }

    public List<Location> getPoints() {
        return points;
    }

    public Location getPoint(int index) {
        return points.get(index);
    }

    public int size() {
        return points.size();
    }
}
