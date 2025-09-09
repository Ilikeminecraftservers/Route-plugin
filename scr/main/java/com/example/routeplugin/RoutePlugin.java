package com.example.routeplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.List;

public class RoutePlugin extends JavaPlugin {

    private double particleSpacing;
    private Particle particleType;
    private int lineFollowTicks;
    private int maxPoints;
    private boolean autoLineFollowOnEdit;

    private Route currentRoute;
    private boolean lineFollowActive = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
    }

    private void loadConfigValues() {
        particleSpacing = getConfig().getDouble("particle-spacing", 0.5);
        String particleName = getConfig().getString("particle-type", "VILLAGER_HAPPY").toUpperCase();
        try {
            particleType = Particle.valueOf(particleName);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Ungültiger Particle-Type in config.yml. Standard VILLAGER_HAPPY wird genutzt.");
            particleType = Particle.VILLAGER_HAPPY;
        }
        lineFollowTicks = getConfig().getInt("linefollow-ticks", 10);
        maxPoints = getConfig().getInt("max-points", 1000);
        autoLineFollowOnEdit = getConfig().getBoolean("auto-linefollow-on-edit", false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Nur Spieler können diesen Befehl ausführen.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage("Nutze /route help für Befehle.");
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "create":
                String routeName = args.length >= 2 ? args[1] : getConfig().getString("default-route-name", "NeueRoute");
                currentRoute = new Route(routeName);
                player.sendMessage("Route " + routeName + " wurde erstellt.");
                break;

            case "edit":
                if (args.length < 2) {
                    player.sendMessage("Bitte einen Unterbefehl angeben.");
                    return true;
                }
                handleEditCommand(player, args);
                break;

            default:
                player.sendMessage("Unbekannter Befehl.");
                break;
        }
        return true;
    }

    private void handleEditCommand(Player player, String[] args) {
        switch (args[1].toLowerCase()) {

            case "linefollowstart":
                if (!checkRouteSelected(player)) return;
                startLineFollow(player);
                break;

            case "linefollowpause":
                lineFollowActive = false;
                player.sendMessage("Line-Follow pausiert.");
                break;

            case "linefollowunpause":
                if (!checkRouteSelected(player)) return;
                lineFollowActive = true;
                player.sendMessage("Line-Follow fortgesetzt.");
                break;

            case "point":
                if (args.length >= 3 && args[2].equalsIgnoreCase("add")) {
                    if (!checkRouteSelected(player)) return;
                    addPoint(player);
                }
                break;

            case "deselect":
                currentRoute = null;
                lineFollowActive = false;
                player.sendMessage("Route-Bearbeitung gestoppt.");
                break;

            case "spacing":
                if (args.length < 3) {
                    player.sendMessage("Bitte Abstandswert angeben.");
                    return;
                }
                try {
                    particleSpacing = Double.parseDouble(args[2]);
                    player.sendMessage("Partikel-Abstand auf " + particleSpacing + " gesetzt.");
                } catch (NumberFormatException e) {
                    player.sendMessage("Ungültiger Wert.");
                }
                break;

            default:
                player.sendMessage("Unbekannter Edit-Befehl.");
                break;
        }
    }

    private boolean checkRouteSelected(Player player) {
        if (currentRoute == null) {
            player.sendMessage("Keine Route ausgewählt.");
            return false;
        }
        return true;
    }

    private void startLineFollow(Player player) {
        lineFollowActive = true;
        player.sendMessage("Line-Follow gestartet. Folge den Partikeln!");
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!lineFollowActive || currentRoute == null) return;
            addPoint(player);
        }, 0L, lineFollowTicks);
    }

    private void addPoint(Player player) {
        if (currentRoute.getPoints().size() >= maxPoints) {
            player.sendMessage("Maximale Punkteanzahl erreicht.");
            return;
        }
        currentRoute.addPoint(player.getLocation());
        spawnParticles(currentRoute);
        player.sendMessage("Point added.");
    }

    private void spawnParticles(Route route) {
        List<Location> points = route.getPoints();
        if (points.size() < 2) return;

        for (int i = 0; i < points.size() - 1; i++) {
            Location start = points.get(i);
            Location end = points.get(i + 1);
            double distance = start.distance(end);
            Vector direction = end.toVector().subtract(start.toVector()).normalize();

            for (double d = 0; d < distance; d += particleSpacing) {
                Location particleLoc = start.clone().add(direction.clone().multiply(d));
                start.getWorld().spawnParticle(particleType, particleLoc, 1);
            }
        }
    }
}
         
