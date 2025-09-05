package com.example.routeplugin;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RoutePlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private final Map<String, Route> routes = new HashMap<>();
    private final Map<UUID, String> editingRoute = new HashMap<>();
    private final Map<UUID, Boolean> lineFollowActive = new HashMap<>();
    private final Map<UUID, Boolean> followPaused = new HashMap<>();
    private final Map<UUID, Integer> followIndex = new HashMap<>();
    private final Map<UUID, String> followingRoute = new HashMap<>();

    private double lineFollowDistance;
    private double lineParticleSpacing;

    private File routesFile;
    private FileConfiguration routesConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lineFollowDistance = getConfig().getDouble("linefollow-point-distance", 2.0);
        lineParticleSpacing = getConfig().getDouble("line-particle-spacing", 0.5);

        setupRoutesFile();
        loadRoutes();

        getCommand("route").setExecutor(this);
        getCommand("route").setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        saveRoutes();
        routes.clear();
    }

    private void setupRoutesFile() {
        routesFile = new File(getDataFolder(), "routes.yml");
        if (!routesFile.exists()) {
            try {
                routesFile.getParentFile().mkdirs();
                routesFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Konnte routes.yml nicht erstellen!");
            }
        }
        routesConfig = YamlConfiguration.loadConfiguration(routesFile);
    }

    private void saveRoutes() {
        routesConfig.set("routes", null); // clear
        for (Route r : routes.values()) {
            String base = "routes." + r.getName();
            routesConfig.set(base + ".particle", r.getParticle().name());

            List<String> serialized = new ArrayList<>();
            for (Location loc : r.getPoints()) {
                serialized.add(serializeLocation(loc));
            }
            routesConfig.set(base + ".points", serialized);
        }
        try {
            routesConfig.save(routesFile);
        } catch (IOException e) {
            getLogger().severe("Fehler beim Speichern der routes.yml");
        }
    }

    private void loadRoutes() {
        if (!routesConfig.contains("routes")) return;
        for (String key : routesConfig.getConfigurationSection("routes").getKeys(false)) {
            String particleName = routesConfig.getString("routes." + key + ".particle", "FLAME");
            Particle particle;
            try {
                particle = Particle.valueOf(particleName.toUpperCase());
            } catch (IllegalArgumentException e) {
                particle = Particle.FLAME;
            }
            Route r = new Route(key, particle);

            List<String> serialized = routesConfig.getStringList("routes." + key + ".points");
            for (String s : serialized) {
                Location loc = deserializeLocation(s);
                if (loc != null) r.addPoint(loc);
            }
            routes.put(key, r);
        }
        getLogger().info("Geladene Routen: " + routes.size());
    }

    private String serializeLocation(Location loc) {
        return loc.getWorld().getName() + ";" +
                loc.getX() + ";" +
                loc.getY() + ";" +
                loc.getZ() + ";" +
                loc.getYaw() + ";" +
                loc.getPitch();
    }

    private Location deserializeLocation(String s) {
        try {
            String[] parts = s.split(";");
            World world = Bukkit.getWorld(parts[0]);
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = Float.parseFloat(parts[4]);
            float pitch = Float.parseFloat(parts[5]);
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Nur Spieler können diesen Befehl verwenden.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage("§cVerwendung: /route <create|follow|edit|setparticle>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                if (args.length < 2) {
                    player.sendMessage("§cVerwendung: /route create <Name>");
                    return true;
                }
                String name = args[1];
                if (routes.containsKey(name)) {
                    player.sendMessage("§cRoute existiert bereits.");
                    return true;
                }
                Particle defaultParticle = Particle.valueOf(getConfig().getString("default-particle", "FLAME"));
                Route route = new Route(name, defaultParticle);
                routes.put(name, route);
                player.sendMessage("§aRoute §e" + name + " §awurde erstellt mit Partikel §e" + defaultParticle);
                break;

            case "setparticle":
                if (args.length < 3) {
                    player.sendMessage("§cVerwendung: /route setparticle <RouteName> <Particle>");
                    return true;
                }
                if (!routes.containsKey(args[1])) {
                    player.sendMessage("§cRoute nicht gefunden.");
                    return true;
                }
                try {
                    Particle p = Particle.valueOf(args[2].toUpperCase());
                    routes.get(args[1]).setParticle(p);
                    player.sendMessage("§aPartikel für Route §e" + args[1] + " §agesetzt auf §e" + p);
                } catch (IllegalArgumentException e) {
                    player.sendMessage("§cUngültiger Partikelname.");
                }
                break;

            case "follow":
                if (args.length < 2) {
                    player.sendMessage("§cVerwendung: /route follow <start|pause|unpause|end>");
                    return true;
                }
                handleFollow(player, args);
                break;

            case "edit":
                if (args.length < 2) {
                    player.sendMessage("§cVerwendung: /route edit <select|point|linefollow...>");
                    return true;
                }
                handleEdit(player, args);
                break;

            default:
                player.sendMessage("§cUnbekannter Unterbefehl.");
        }
        return true;
    }

    private void handleFollow(Player player, String[] args) {
        switch (args[1].toLowerCase()) {
            case "start":
                if (args.length < 3) {
                    player.sendMessage("§cVerwendung: /route follow start <RouteName>");
                    return;
                }
                String routeName = args[2];
                if (!routes.containsKey(routeName)) {
                    player.sendMessage("§cRoute nicht gefunden.");
                    return;
                }
                followingRoute.put(player.getUniqueId(), routeName);
                followIndex.put(player.getUniqueId(), 0);
                followPaused.put(player.getUniqueId(), false);
                player.sendMessage("§aRoute Verfolgung gestartet: §e" + routeName);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!followingRoute.containsKey(player.getUniqueId())) {
                            cancel();
                            return;
                        }
                        if (followPaused.getOrDefault(player.getUniqueId(), false)) return;

                        Route r = routes.get(followingRoute.get(player.getUniqueId()));
                        int idx = followIndex.getOrDefault(player.getUniqueId(), 0);

                        if (idx >= r.size()) {
                            player.sendMessage("§aRoute beendet.");
                            spawnRandomFirework(player.getLocation());
                            followingRoute.remove(player.getUniqueId());
                            followIndex.remove(player.getUniqueId());
                            cancel();
                            return;
                        }

                        Location target = r.getPoint(idx);
                        player.spawnParticle(r.getParticle(), target, 5, 0, 0, 0, 0);
                        followIndex.put(player.getUniqueId(), idx + 1);

                        if (idx % 50 == 0 && idx > 0) {
                            player.sendMessage("§eFortschritt: " + idx + " Punkte verfolgt.");
                        }
                    }
                }.runTaskTimer(this, 0, 20);
                break;

            case "pause":
                followPaused.put(player.getUniqueId(), true);
                player.sendMessage("§eRouten Verfolgung pausiert.");
                break;

            case "unpause":
                followPaused.put(player.getUniqueId(), false);
                player.sendMessage("§aRouten Verfolgung fortgesetzt.");
                break;

            case "end":
                followingRoute.remove(player.getUniqueId());
                followIndex.remove(player.getUniqueId());
                player.sendMessage("§cRouten Verfolgung beendet.");
                break;
        }
    }

    private void handleEdit(Player player, String[] args) {
        switch (args[1].toLowerCase()) {
            case "select":
                if (args.length < 3) {
                    player.sendMessage("§cVerwendung: /route edit select <RouteName>");
                    return;
                }
                if (!routes.containsKey(args[2])) {
                    player.sendMessage("§cRoute nicht gefunden.");
                    return;
                }
                editingRoute.put(player.getUniqueId(), args[2]);
                player.sendMessage("§aRoute §e" + args[2] + " §awird bearbeitet.");
                break;

            case "deselect":
                editingRoute.remove(player.getUniqueId());
                lineFollowActive.remove(player.getUniqueId());
                player.sendMessage("§cRoutenbearbeitung gestoppt.");
                break;

            case "point":
                if (args.length > 2 && args[2].equalsIgnoreCase("add")) {
                    String r = editingRoute.get(player.getUniqueId());
                    if (r == null) {
                        player.sendMessage("§cKeine Route ausgewählt.");
                        return;
                    }
                    routes.get(r).addPoint(player.getLocation());
                    player.sendMessage("§aPunkt zur Route hinzugefügt.");
                }
                break;

            case "linefollowstart":
                String r1 = editingRoute.get(player.getUniqueId());
                if (r1 == null) {
                    player.sendMessage("§cKeine Route ausgewählt.");
                    return;
                }
                lineFollowActive.put(player.getUniqueId(), true);
                new BukkitRunnable() {
                    Location last = player.getLocation();
                    @Override
                    public void run() {
                        if (!lineFollowActive.getOrDefault(player.getUniqueId(), false)) {
                            cancel();
                            return;
                        }
                        Location current = player.getLocation();
                        if (last.distance(current) >= lineFollowDistance) {
                            routes.get(r1).addPoint(current.clone());
                            last = current.clone();
                            player.sendMessage("§aPunkt hinzugefügt (LineFollow).");
                        }
                    }
                }.runTaskTimer(this, 0, 20);
                player.sendMessage("§aLineFollow gestartet.");
                break;

            case "linefollowpause":
                lineFollowActive.put(player.getUniqueId(), false);
                player.sendMessage("§eLineFollow pausiert.");
                break;

            case "linefollowunpause":
                if (editingRoute.containsKey(player.getUniqueId())) {
                    lineFollowActive.put(player.getUniqueId(), true);
                    player.sendMessage("§aLineFollow fortgesetzt.");
                }
                break;
        }
    }

    private void spawnRandomFirework(Location loc) {
        Firework fw = loc.getWorld().spawn(loc, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        Random rand = new Random();

        FireworkEffect effect = FireworkEffect.builder()
                .with(FireworkEffect.Type.values()[rand.nextInt(FireworkEffect.Type.values().length)])
                .withColor(Color.fromRGB(rand.nextInt(255), rand.nextInt(255), rand.nextInt(255)))
                .withFade(Color.fromRGB(rand.nextInt(255), rand.nextInt(255), rand.nextInt(255)))
                .flicker(rand.nextBoolean())
                .trail(rand.nextBoolean())
                .build();

        meta.addEffect(effect);
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "setparticle", "follow", "edit");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("follow")) {
            return Arrays.asList("start", "pause", "unpause", "end");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            return Arrays.asList("select", "deselect", "point", "linefollowstart", "linefollowpause", "linefollowunpause");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setparticle")) {
            return new ArrayList<>(routes.keySet());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setparticle")) {
            return Arrays.stream(Particle.values()).map(Enum::name).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
          }package com.example.routeplugin;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RoutePlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private final Map<String, Route> routes = new HashMap<>();
    private final Map<UUID, String> editingRoute = new HashMap<>();
    private final Map<UUID, Boolean> lineFollowActive = new HashMap<>();
    private final Map<UUID, Boolean> followPaused = new HashMap<>();
    private final Map<UUID, Integer> followIndex = new HashMap<>();
    private final Map<UUID, String> followingRoute = new HashMap<>();

    private double lineFollowDistance;
    private double lineParticleSpacing;

    private File routesFile;
    private FileConfiguration routesConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lineFollowDistance = getConfig().getDouble("linefollow-point-distance", 2.0);
        lineParticleSpacing = getConfig().getDouble("line-particle-spacing", 0.5);

        setupRoutesFile();
        loadRoutes();

        getCommand("route").setExecutor(this);
        getCommand("route").setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        saveRoutes();
        routes.clear();
    }

    private void setupRoutesFile() {
        routesFile = new File(getDataFolder(), "routes.yml");
        if (!routesFile.exists()) {
            try {
                routesFile.getParentFile().mkdirs();
                routesFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Konnte routes.yml nicht erstellen!");
            }
        }
        routesConfig = YamlConfiguration.loadConfiguration(routesFile);
    }

    private void saveRoutes() {
        routesConfig.set("routes", null); // clear
        for (Route r : routes.values()) {
            String base = "routes." + r.getName();
            routesConfig.set(base + ".particle", r.getParticle().name());

            List<String> serialized = new ArrayList<>();
            for (Location loc : r.getPoints()) {
                serialized.add(serializeLocation(loc));
            }
            routesConfig.set(base + ".points", serialized);
        }
        try {
            routesConfig.save(routesFile);
        } catch (IOException e) {
            getLogger().severe("Fehler beim Speichern der routes.yml");
        }
    }

    private void loadRoutes() {
        if (!routesConfig.contains("routes")) return;
        for (String key : routesConfig.getConfigurationSection("routes").getKeys(false)) {
            String particleName = routesConfig.getString("routes." + key + ".particle", "FLAME");
            Particle particle;
            try {
                particle = Particle.valueOf(particleName.toUpperCase());
            } catch (IllegalArgumentException e) {
                particle = Particle.FLAME;
            }
            Route r = new Route(key, particle);

            List<String> serialized = routesConfig.getStringList("routes." + key + ".points");
            for (String s : serialized) {
                Location loc = deserializeLocation(s);
                if (loc != null) r.addPoint(loc);
            }
            routes.put(key, r);
        }
        getLogger().info("Geladene Routen: " + routes.size());
    }

    private String serializeLocation(Location loc) {
        return loc.getWorld().getName() + ";" +
                loc.getX() + ";" +
                loc.getY() + ";" +
                loc.getZ() + ";" +
                loc.getYaw() + ";" +
                loc.getPitch();
    }

    private Location deserializeLocation(String s) {
        try {
            String[] parts = s.split(";");
            World world = Bukkit.getWorld(parts[0]);
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = Float.parseFloat(parts[4]);
            float pitch = Float.parseFloat(parts[5]);
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Nur Spieler können diesen Befehl verwenden.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage("§cVerwendung: /route <create|follow|edit|setparticle>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                if (args.length < 2) {
                    player.sendMessage("§cVerwendung: /route create <Name>");
                    return true;
                }
                String name = args[1];
                if (routes.containsKey(name)) {
                    player.sendMessage("§cRoute existiert bereits.");
                    return true;
                }
                Particle defaultParticle = Particle.valueOf(getConfig().getString("default-particle", "FLAME"));
                Route route = new Route(name, defaultParticle);
                routes.put(name, route);
                player.sendMessage("§aRoute §e" + name + " §awurde erstellt mit Partikel §e" + defaultParticle);
                break;

            case "setparticle":
                if (args.length < 3) {
                    player.sendMessage("§cVerwendung: /route setparticle <RouteName> <Particle>");
                    return true;
                }
                if (!routes.containsKey(args[1])) {
                    player.sendMessage("§cRoute nicht gefunden.");
                    return true;
                }
                try {
                    Particle p = Particle.valueOf(args[2].toUpperCase());
                    routes.get(args[1]).setParticle(p);
                    player.sendMessage("§aPartikel für Route §e" + args[1] + " §agesetzt auf §e" + p);
                } catch (IllegalArgumentException e) {
                    player.sendMessage("§cUngültiger Partikelname.");
                }
                break;

            case "follow":
                if (args.length < 2) {
                    player.sendMessage("§cVerwendung: /route follow <start|pause|unpause|end>");
                    return true;
                }
                handleFollow(player, args);
                break;

            case "edit":
                if (args.length < 2) {
                    player.sendMessage("§cVerwendung: /route edit <select|point|linefollow...>");
                    return true;
                }
                handleEdit(player, args);
                break;

            default:
                player.sendMessage("§cUnbekannter Unterbefehl.");
        }
        return true;
    }

    private void handleFollow(Player player, String[] args) {
        switch (args[1].toLowerCase()) {
            case "start":
                if (args.length < 3) {
                    player.sendMessage("§cVerwendung: /route follow start <RouteName>");
                    return;
                }
                String routeName = args[2];
                if (!routes.containsKey(routeName)) {
                    player.sendMessage("§cRoute nicht gefunden.");
                    return;
                }
                followingRoute.put(player.getUniqueId(), routeName);
                followIndex.put(player.getUniqueId(), 0);
                followPaused.put(player.getUniqueId(), false);
                player.sendMessage("§aRoute Verfolgung gestartet: §e" + routeName);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!followingRoute.containsKey(player.getUniqueId())) {
                            cancel();
                            return;
                        }
                        if (followPaused.getOrDefault(player.getUniqueId(), false)) return;

                        Route r = routes.get(followingRoute.get(player.getUniqueId()));
                        int idx = followIndex.getOrDefault(player.getUniqueId(), 0);

                        if (idx >= r.size()) {
                            player.sendMessage("§aRoute beendet.");
                            spawnRandomFirework(player.getLocation());
                            followingRoute.remove(player.getUniqueId());
                            followIndex.remove(player.getUniqueId());
                            cancel();
                            return;
                        }

                        Location target = r.getPoint(idx);
                        player.spawnParticle(r.getParticle(), target, 5, 0, 0, 0, 0);
                        followIndex.put(player.getUniqueId(), idx + 1);

                        if (idx % 50 == 0 && idx > 0) {
                            player.sendMessage("§eFortschritt: " + idx + " Punkte verfolgt.");
                        }
                    }
                }.runTaskTimer(this, 0, 20);
                break;

            case "pause":
                followPaused.put(player.getUniqueId(), true);
                player.sendMessage("§eRouten Verfolgung pausiert.");
                break;

            case "unpause":
                followPaused.put(player.getUniqueId(), false);
                player.sendMessage("§aRouten Verfolgung fortgesetzt.");
                break;

            case "end":
                followingRoute.remove(player.getUniqueId());
                followIndex.remove(player.getUniqueId());
                player.sendMessage("§cRouten Verfolgung beendet.");
                break;
        }
    }

    private void handleEdit(Player player, String[] args) {
        switch (args[1].toLowerCase()) {
            case "select":
                if (args.length < 3) {
                    player.sendMessage("§cVerwendung: /route edit select <RouteName>");
                    return;
                }
                if (!routes.containsKey(args[2])) {
                    player.sendMessage("§cRoute nicht gefunden.");
                    return;
                }
                editingRoute.put(player.getUniqueId(), args[2]);
                player.sendMessage("§aRoute §e" + args[2] + " §awird bearbeitet.");
                break;

            case "deselect":
                editingRoute.remove(player.getUniqueId());
                lineFollowActive.remove(player.getUniqueId());
                player.sendMessage("§cRoutenbearbeitung gestoppt.");
                break;

            case "point":
                if (args.length > 2 && args[2].equalsIgnoreCase("add")) {
                    String r = editingRoute.get(player.getUniqueId());
                    if (r == null) {
                        player.sendMessage("§cKeine Route ausgewählt.");
                        return;
                    }
                    routes.get(r).addPoint(player.getLocation());
                    player.sendMessage("§aPunkt zur Route hinzugefügt.");
                }
                break;

            case "linefollowstart":
                String r1 = editingRoute.get(player.getUniqueId());
                if (r1 == null) {
                    player.sendMessage("§cKeine Route ausgewählt.");
                    return;
                }
                lineFollowActive.put(player.getUniqueId(), true);
                new BukkitRunnable() {
                    Location last = player.getLocation();
                    @Override
                    public void run() {
                        if (!lineFollowActive.getOrDefault(player.getUniqueId(), false)) {
                            cancel();
                            return;
                        }
                        Location current = player.getLocation();
                        if (last.distance(current) >= lineFollowDistance) {
                            routes.get(r1).addPoint(current.clone());
                            last = current.clone();
                            player.sendMessage("§aPunkt hinzugefügt (LineFollow).");
                        }
                    }
                }.runTaskTimer(this, 0, 20);
                player.sendMessage("§aLineFollow gestartet.");
                break;

            case "linefollowpause":
                lineFollowActive.put(player.getUniqueId(), false);
                player.sendMessage("§eLineFollow pausiert.");
                break;

            case "linefollowunpause":
                if (editingRoute.containsKey(player.getUniqueId())) {
                    lineFollowActive.put(player.getUniqueId(), true);
                    player.sendMessage("§aLineFollow fortgesetzt.");
                }
                break;
        }
    }

    private void spawnRandomFirework(Location loc) {
        Firework fw = loc.getWorld().spawn(loc, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        Random rand = new Random();

        FireworkEffect effect = FireworkEffect.builder()
                .with(FireworkEffect.Type.values()[rand.nextInt(FireworkEffect.Type.values().length)])
                .withColor(Color.fromRGB(rand.nextInt(255), rand.nextInt(255), rand.nextInt(255)))
                .withFade(Color.fromRGB(rand.nextInt(255), rand.nextInt(255), rand.nextInt(255)))
                .flicker(rand.nextBoolean())
                .trail(rand.nextBoolean())
                .build();

        meta.addEffect(effect);
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "setparticle", "follow", "edit");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("follow")) {
            return Arrays.asList("start", "pause", "unpause", "end");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            return Arrays.asList("select", "deselect", "point", "linefollowstart", "linefollowpause", "linefollowunpause");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setparticle")) {
            return new ArrayList<>(routes.keySet());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setparticle")) {
            return Arrays.stream(Particle.values()).map(Enum::name).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
                                           }
