package com.example.routeplugin;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class Route {

    private final String name;
    private final List<Location> points = new ArrayList<>();

    public Route(String name) {
        this.name = name;
    }

    /**
     * Fügt einen Punkt zur Route hinzu.
     * @param loc Location des Punktes
     */
    public void addPoint(Location loc) {
        points.add(loc.clone());
    }

    /**
     * Gibt alle Punkte der Route zurück.
     * @return Liste von Locations
     */
    public List<Location> getPoints() {
        return points;
    }

    /**
     * Gibt den Namen der Route zurück.
     * @return Name
     */
    public String getName() {
        return name;
    }

    /**
     * Entfernt alle Punkte der Route.
     */
    public void clearPoints() {
        points.clear();
    }

    /**
     * Anzahl der Punkte
     * @return Punktzahl
     */
    public int size() {
        return points.size();
    }
}
