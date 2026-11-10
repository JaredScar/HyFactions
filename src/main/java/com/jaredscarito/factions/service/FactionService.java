package com.jaredscarito.factions.service;

import com.jaredscarito.factions.data.BuildCheck;
import com.jaredscarito.factions.data.Config;
import com.jaredscarito.factions.data.Faction;
import com.jaredscarito.factions.data.Home;
import com.jaredscarito.factions.data.Invite;
import com.jaredscarito.factions.data.Member;
import com.jaredscarito.factions.data.Plot;
import com.jaredscarito.factions.data.Profile;
import com.jaredscarito.factions.data.SaveData;
import com.jaredscarito.factions.data.Spot;
import com.jaredscarito.factions.data.Warp;
import com.jaredscarito.factions.map.FactionClaimMap;
import com.jaredscarito.factions.map.FactionMapMarkers;
import com.jaredscarito.factions.map.MapPin;
import com.jaredscarito.factions.text.Texts;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.IWorldMap;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;
import org.joml.Vector3d;

/**
 * HyFactions: roster, chunk claims, power, relations, and an internal bank.
 */
public final class FactionService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String[] COLORS = {
        "#55FFFF", "#FFAA00", "#FF55FF", "#55FF55", "#FF8844", "#AAAAFF", "#FFFF55", "#FF5577"
    };
    private static final long DENY_COOLDOWN_MS = 1500L;

    private final JavaPlugin plugin;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "factions-home");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<UUID, ScheduledFuture<?>> pendingHomes = new ConcurrentHashMap<>();
    private final Set<UUID> bypass = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastDeny = new ConcurrentHashMap<>();
    private final Map<UUID, String> territory = new ConcurrentHashMap<>();
    private final Map<UUID, String> autoClaimErrors = new ConcurrentHashMap<>();
    private final Map<UUID, Spot> safeSpots = new ConcurrentHashMap<>();
    private final Map<UUID, Long> stuckAt = new ConcurrentHashMap<>();
    private final Set<String> mapRefresh = ConcurrentHashMap.newKeySet();
    private final Map<String, List<MapPin>> mapAnchors = new HashMap<>();
    private boolean mapAnchorsDirty = true;

    private SaveData data = new SaveData();
    private Config config = Config.defaults();
    private boolean loaded;

    public FactionService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void load() {
        Path directory = plugin.getDataDirectory();
        try {
            Files.createDirectories(directory);
            readConfig(directory);

            Path factionsPath = directory.resolve("factions.json");
            if (Files.exists(factionsPath)) {
                try (Reader reader = Files.newBufferedReader(factionsPath, StandardCharsets.UTF_8)) {
                    SaveData read = GSON.fromJson(reader, SaveData.class);
                    if (read != null) {
                        data = read;
                    }
                }
            }
            data.normalize();
            loaded = true;
            bypass.clear();
            if (data.bypassIds != null) {
                for (String id : data.bypassIds) {
                    try {
                        bypass.add(UUID.fromString(id));
                    } catch (IllegalArgumentException exception) {
                        // Ignore a bad id left in the save.
                    }
                }
            }
            if (ensureMemberProfiles()) {
                save();
            }
            scheduler.scheduleAtFixedRate(this::tickPowerSafe, 60, 60, TimeUnit.SECONDS);
            plugin.getLogger().atInfo().log("HyFactions data: %s (%d factions, %d claims)", directory, data.factions.size(), data.claims.size());
            for (PlayerRef online : Universe.get().getPlayers()) {
                remember(online);
            }
            installWorldMaps();
            scheduler.schedule(this::installWorldMaps, 2, TimeUnit.SECONDS);
            scheduler.schedule(this::installWorldMaps, 15, TimeUnit.SECONDS);
        } catch (IOException exception) {
            plugin.getLogger().atSevere().withCause(exception).log("Failed to load faction data from %s", directory);
        }
    }

    public void shutdown() {
        for (ScheduledFuture<?> pending : pendingHomes.values()) {
            pending.cancel(false);
        }
        pendingHomes.clear();
        scheduler.shutdownNow();
        save();
    }

    public synchronized void remember(PlayerRef player) {
        if (!loaded || player == null) {
            return;
        }
        Profile profile = ensureProfile(player.getUuid(), player.getUsername());
        profile.lastSeen = System.currentTimeMillis();
        Faction faction = factionOf(player.getUuid());
        if (faction == null) {
            return;
        }
        Member member = faction.members.get(player.getUuid().toString());
        if (member != null && !player.getUsername().equals(member.name)) {
            member.name = player.getUsername();
            save();
        }
    }

    public void installWorldMaps() {
        try {
            Map<String, World> worlds = Universe.get().getWorlds();
            if (worlds == null) {
                return;
            }
            for (World world : worlds.values()) {
                if (world != null) {
                    world.execute(() -> installWorldMap(world));
                }
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().atSevere().withCause(exception).log("Failed to install the faction map");
        }
    }

    private void installWorldMap(World world) {
        WorldMapManager manager = world.getWorldMapManager();
        if (manager == null || !manager.isWorldMapEnabled()) {
            return;
        }
        IWorldMap current = manager.getGenerator();
        if (current != null && !(current instanceof FactionClaimMap)) {
            manager.setGenerator(new FactionClaimMap(current, this));
            manager.clearImages();
        }
        if (manager.getMarkerProviders() != null && !manager.getMarkerProviders().containsKey("factions")) {
            manager.addMarkerProvider("factions", new FactionMapMarkers(this));
        }
    }

    private void touchMap(String worldName) {
        mapAnchorsDirty = true;
        if (worldName == null || worldName.isEmpty() || !mapRefresh.add(worldName)) {
            return;
        }
        scheduler.schedule(() -> {
            mapRefresh.remove(worldName);
            World world = Universe.get().getWorld(worldName);
            if (world == null) {
                return;
            }
            world.execute(() -> {
                WorldMapManager manager = world.getWorldMapManager();
                if (manager != null) {
                    manager.clearImages();
                }
            });
        }, 400, TimeUnit.MILLISECONDS);
    }

    private static String worldOfClaim(String key) {
        if (key == null) {
            return null;
        }
        int cut = key.indexOf('\u001f');
        return cut < 0 ? key : key.substring(0, cut);
    }

    public synchronized String claimOwnerId(String world, int chunkX, int chunkZ) {
        if (!loaded || world == null) {
            return null;
        }
        return data.claims.get(claimKey(world, chunkX, chunkZ));
    }

    public synchronized int claimTint(String world, int chunkX, int chunkZ) {
        String ownerId = claimOwnerId(world, chunkX, chunkZ);
        Faction faction = ownerId == null ? null : data.factions.get(ownerId);
        if (faction == null) {
            return 0;
        }
        return parseColor(faction.color);
    }

    public synchronized List<MapPin> mapPins(UUID viewer, String worldName) {
        List<MapPin> pins = new ArrayList<>();
        if (!loaded || worldName == null) {
            return pins;
        }
        if (mapAnchorsDirty) {
            rebuildMapAnchors();
        }
        List<MapPin> anchors = mapAnchors.get(worldName);
        if (anchors == null) {
            return pins;
        }
        Faction mine = viewer == null ? null : factionOf(viewer);
        for (MapPin anchor : anchors) {
            Faction faction = data.factions.get(anchor.id.substring("factions-".length()));
            String color = faction == null ? anchor.color : pinColor(mine, faction);
            pins.add(new MapPin(anchor.id, anchor.name, color, anchor.x, anchor.y, anchor.z));
        }
        return pins;
    }

    private void rebuildMapAnchors() {
        mapAnchors.clear();
        Map<String, long[]> sums = new HashMap<>();
        for (Map.Entry<String, String> entry : data.claims.entrySet()) {
            String[] parts = entry.getKey().split("\u001f");
            if (parts.length != 3) {
                continue;
            }
            try {
                int chunkX = Integer.parseInt(parts[1]);
                int chunkZ = Integer.parseInt(parts[2]);
                long[] sum = sums.computeIfAbsent(entry.getValue() + "\u001f" + parts[0], key -> new long[3]);
                sum[0] += chunkX;
                sum[1] += chunkZ;
                sum[2]++;
            } catch (NumberFormatException exception) {
                // Skip a damaged claim key.
            }
        }
        for (Faction faction : data.factions.values()) {
            if (faction.home != null && faction.home.world != null) {
                addAnchor(faction, faction.home.world, faction.home.x, faction.home.y, faction.home.z);
            }
            for (Map.Entry<String, long[]> entry : sums.entrySet()) {
                String prefix = faction.id + "\u001f";
                if (!entry.getKey().startsWith(prefix) || entry.getValue()[2] <= 0) {
                    continue;
                }
                String worldName = entry.getKey().substring(prefix.length());
                if (faction.home != null && worldName.equals(faction.home.world)) {
                    continue;
                }
                double chunks = entry.getValue()[2];
                addAnchor(faction, worldName, entry.getValue()[0] / chunks * 32.0 + 16.0, 80.0, entry.getValue()[1] / chunks * 32.0 + 16.0);
            }
        }
        mapAnchorsDirty = false;
    }

    private void addAnchor(Faction faction, String worldName, double x, double y, double z) {
        mapAnchors.computeIfAbsent(worldName, key -> new ArrayList<>())
                .add(new MapPin("factions-" + faction.id, faction.name, faction.color == null ? "#FFAA00" : faction.color, x, y, z));
    }

    private String pinColor(Faction viewer, Faction faction) {
        if (faction != null && "SAFEZONE".equals(faction.kind)) {
            return "#55FFFF";
        }
        if (faction != null && "WARZONE".equals(faction.kind)) {
            return "#FF5555";
        }
        return switch (relationTo(viewer, faction)) {
            case "MEMBER" -> "#55FF55";
            case "ALLY" -> "#00AA00";
            case "TRUCE" -> "#FFFF55";
            case "ENEMY" -> "#FF5555";
            default -> faction == null || faction.color == null ? "#FFAA00" : faction.color;
        };
    }

    private static int parseColor(String hex) {
        if (hex == null || hex.isBlank()) {
            return 0xFFAA00;
        }
        String raw = hex.charAt(0) == '#' ? hex.substring(1) : hex;
        try {
            return Integer.parseInt(raw, 16) & 0xFFFFFF;
        } catch (NumberFormatException exception) {
            return 0xFFAA00;
        }
    }

    public synchronized void onChat(PlayerChatEvent event) {
        if (event.isCancelled()) {
            return;
        }
        PlayerRef sender = event.getSender();
        String content = event.getContent();
        boolean forcedPublic = content.startsWith("!");
        if (forcedPublic) {
            event.setContent(content.substring(1).stripLeading());
        }
        Faction faction = factionOf(sender.getUuid());
        if (faction == null || isSystem(faction)) {
            return;
        }
        Profile profile = ensureProfile(sender.getUuid(), sender.getUsername());
        String mode = forcedPublic ? "PUBLIC" : profile.chatMode;
        String username = sender.getUsername();
        String tag = chatTag(faction);
        if ("FACTION".equals(mode)) {
            event.setTargets(online(faction));
            event.setFormatter((viewer, message) -> factionLine(tag, relationColor(faction, viewer), username, message));
            return;
        }
        if ("ALLY".equals(mode) || "TRUCE".equals(mode)) {
            String wanted = mode;
            List<PlayerRef> targets = new ArrayList<>(online(faction));
            for (Map.Entry<String, String> relation : faction.relations.entrySet()) {
                if (!wanted.equals(relation.getValue())) {
                    continue;
                }
                Faction other = data.factions.get(relation.getKey());
                if (other != null) {
                    targets.addAll(online(other));
                }
            }
            event.setTargets(targets);
            event.setFormatter((viewer, message) -> ("TRUCE".equals(wanted) ? truceLine(tag, relationColor(faction, viewer), username, message) : allyLine(tag, relationColor(faction, viewer), username, message)));
            return;
        }
        event.setFormatter((viewer, message) -> publicLine(tag, relationColor(faction, viewer), username, message));
    }

    public synchronized BuildCheck checkAccess(UUID playerId, String world, int blockX, int blockZ, String useBlockId) {
        if (bypass.contains(playerId)) {
            return BuildCheck.allow();
        }
        Faction owner = factionAt(world, blockX, blockZ);
        if (owner == null) {
            return BuildCheck.allow();
        }
        if ("SAFEZONE".equals(owner.kind) || "WARZONE".equals(owner.kind)) {
            return BuildCheck.deny(owner.name);
        }
        String key = claimKey(world, ChunkUtil.chunkCoordinate(blockX), ChunkUtil.chunkCoordinate(blockZ));
        Plot plot = data.plots.get(key);
        if (plot != null && (playerId.toString().equals(plot.ownerId) || (plot.access != null && plot.access.contains(playerId.toString())))) {
            return BuildCheck.allow();
        }
        Faction mine = factionOf(playerId);
        String relation = relationTo(mine, owner);
        String action = actionFor(useBlockId);
        if ("MEMBER".equals(relation)) {
            Member member = mine.members.get(playerId.toString());
            if (member != null && "RECRUIT".equals(member.rank) && "CONTAINER".equals(action)) {
                return BuildCheck.deny(owner.name);
            }
            return BuildCheck.allow();
        }
        if (permitted(relation, action)) {
            return BuildCheck.allow();
        }
        return BuildCheck.deny(owner.name);
    }

    public synchronized boolean allowPlayerDamage(UUID attacker, UUID victim, String world, int blockX, int blockZ) {
        if (attacker.equals(victim) || bypass.contains(attacker)) {
            return true;
        }
        Faction attackerFaction = factionOf(attacker);
        Faction victimFaction = factionOf(victim);
        if ((attackerFaction != null && attackerFaction.peaceful) || (victimFaction != null && victimFaction.peaceful)) {
            return false;
        }
        Faction claim = factionAt(world, blockX, blockZ);
        if (claim != null && "SAFEZONE".equals(claim.kind)) {
            return false;
        }
        if (claim != null && "WARZONE".equals(claim.kind)) {
            return true;
        }
        if (attackerFaction != null && victimFaction != null && attackerFaction.id.equals(victimFaction.id)) {
            return config.friendlyFire;
        }
        String relation = relationTo(attackerFaction, victimFaction);
        if ("ALLY".equals(relation)) {
            return config.allyPvp;
        }
        if ("TRUCE".equals(relation)) {
            return config.trucePvp;
        }
        if ("ENEMY".equals(relation)) {
            return config.enemyPvp;
        }
        if (claim != null) {
            return config.neutralPvpInClaims;
        }
        return config.neutralPvpInWilderness;
    }

    public synchronized boolean allowHazardDamage(UUID victim, String world, int blockX, int blockZ, String causeId) {
        if (causeId == null) {
            return true;
        }
        String cause = causeId.toLowerCase(Locale.ROOT);
        boolean hazard = cause.contains("explos") || cause.contains("fire") || cause.contains("burn") || cause.contains("creeper");
        if (!hazard) {
            return true;
        }
        Faction claim = factionAt(world, blockX, blockZ);
        if (claim == null) {
            return true;
        }
        if ("SAFEZONE".equals(claim.kind)) {
            return false;
        }
        if ("WARZONE".equals(claim.kind)) {
            return true;
        }
        if (config.protectOfflineFactions && !isOnline(claim)) {
            return false;
        }
        return raidOpen();
    }

    public synchronized void onDeath(PlayerRef player) {
        if (config.powerLossOnDeath <= 0) {
            return;
        }
        Profile profile = ensureProfile(player.getUuid(), player.getUsername());
        profile.power = round(Math.max(config.minPower, profile.power - config.powerLossOnDeath));
        profile.lastSeen = System.currentTimeMillis();
        save();
        Faction faction = factionOf(player.getUuid());
        String factionPower = faction == null || isSystem(faction) ? "" : " Faction power is now " + format(power(faction)) + ".";
        player.sendMessage(Texts.err("You lost " + format(config.powerLossOnDeath) + " power. You have " + format(profile.power) + "." + factionPower));
    }

    public synchronized void onMove(CommandBuffer<EntityStore> buffer, PlayerRef player, String world, double x, double z) {
        if (!loaded) {
            return;
        }
        Profile profile = ensureProfile(player.getUuid(), player.getUsername());
        profile.lastSeen = System.currentTimeMillis();
        Transform transform = player.getTransform();
        Vector3d position = transform.getPosition();
        Rotation3f rotation = transform.getRotation();
        int chunkX = ChunkUtil.chunkCoordinate(position.x);
        int chunkZ = ChunkUtil.chunkCoordinate(position.z);
        String key = world + ":" + chunkX + ":" + chunkZ;
        String previous = territory.get(player.getUuid());
        Faction owner = data.factions.get(data.claims.get(claimKey(world, chunkX, chunkZ)));
        if (canEnter(player, owner)) {
            safeSpots.put(player.getUuid(), Spot.of(world, position, rotation));
        }
        if (previous == null || previous.equals(key)) {
            if (previous == null) {
                applyTag(buffer, player);
            }
            territory.put(player.getUuid(), key);
            return;
        }
        if (!canEnter(player, owner)) {
            warn(player, "You cannot enter " + owner.name + ".");
            Spot spot = safeSpots.get(player.getUuid());
            if (spot != null) {
                teleport(buffer, player, spot.world, spot.x, spot.y, spot.z, spot.pitch, spot.yaw, spot.roll);
            }
            return;
        }
        String leftOwner = ownerAt(previous);
        String enteredOwner = owner == null ? null : owner.id;
        territory.put(player.getUuid(), key);
        applyTag(buffer, player);
        if (leftOwner == null ? enteredOwner != null : !leftOwner.equals(enteredOwner)) {
            if (owner == null) {
                player.sendMessage(Texts.info("Now entering Wilderness"));
            } else {
                player.sendMessage(Message.join(Texts.info("Now entering "), Texts.text(chatTag(owner), relationColor(owner, player))));
                if (owner.announcement != null && !owner.announcement.isBlank()) {
                    player.sendMessage(Texts.info(owner.announcement));
                }
            }
        }
        if (profile.autoClaim) {
            String result = tryClaim(player, world, chunkX, chunkZ);
            if (result == null) {
                autoClaimErrors.remove(player.getUuid());
                save();
                player.sendMessage(Texts.ok("Claimed chunk " + chunkX + ", " + chunkZ + " for " + factionOf(player.getUuid()).name + "."));
            } else if (!result.isEmpty() && !result.equals(autoClaimErrors.put(player.getUuid(), result))) {
                player.sendMessage(Texts.err(result));
            }
        }
    }

    public void cancelHome(UUID playerId) {
        ScheduledFuture<?> pending = pendingHomes.remove(playerId);
        if (pending != null) {
            pending.cancel(false);
        }
    }

    public void warn(PlayerRef player, String message) {
        long now = System.currentTimeMillis();
        Long previous = lastDeny.get(player.getUuid());
        if (previous != null && now - previous < DENY_COOLDOWN_MS) {
            return;
        }
        lastDeny.put(player.getUuid(), now);
        player.sendMessage(Texts.err(message));
    }

    public void warnDenied(PlayerRef player, String ownerName) {
        warn(player, "This land belongs to " + ownerName + ".");
    }

    public synchronized Message help(PlayerRef player) {
        player.sendMessage(Texts.info("HyFactions — a claim is one 32x32 chunk, full height. Power limits how much land you can hold."));
        player.sendMessage(Texts.info("/f create <name>   /f disband --confirm   /f open   /f info [name]   /f list   /f desc <text>"));
        player.sendMessage(Texts.info("/f invite <player>   /f join [name]   /f deny   /f leave   /f kick <player>   /f ban <player>   /f unban <player>"));
        player.sendMessage(Texts.info("/f promote <player>   /f demote <player>   /f leader <player>   ranks: recruit, member, officer, co-leader, leader"));
        player.sendMessage(Texts.info("/f claim   /f unclaim   /f unclaimall --confirm   /f autoclaim   /f claimline <count>   /f claimsquare <radius>   /f claimcircle <radius>   /f claimfill   /f map"));
        player.sendMessage(Texts.info("/f owner <member>   /f access <player>   /f entry <allow|deny>   /f tag <tag>   /f title <text>   /f announce <text>"));
        player.sendMessage(Texts.info("/f sethome   /f home   /f setwarp <name> [password]   /f delwarp <name>   /f warp <name> [password]   /f warps   /f stuck"));
        player.sendMessage(Texts.info("/f power   /f money   /f deposit <amount>   /f withdraw <amount>   /f peaceful"));
        player.sendMessage(Texts.info("/f chat [public|faction|ally|truce]   /fc <message>   /f ally <faction>   /f truce <faction>   /f enemy <faction>   /f neutral <faction>"));
        player.sendMessage(Texts.info("Chat tags use relation colors: green for your faction, dark green for allies, yellow for truces, red for enemies."));
        player.sendMessage(Texts.info("The world map colors every claim and places a marker on each faction. /f map shows the chunks around you."));
        player.sendMessage(Texts.info("Land past your power can be taken by enemies and neutrals. Allies, truces, peaceful, and permanent land cannot. Unclaiming refunds the claim cost."));
        player.sendMessage(Texts.info("Death costs power. Boosts are separate and do not regen away. Start a public line with !"));
        player.sendMessage(Texts.info("Explosion and fire damage to players in claims follows raid hours. Explosions can still break blocks."));
        if (player.hasPermission("factions.admin")) {
            player.sendMessage(Texts.info("/f admin bypass   unclaim   disband   safezone   warzone   claim <faction>   give   power   powerboost   factionboost   permanent   lock   reload"));
        }
        return null;
    }

    public synchronized Message create(PlayerRef player, World world, String rawName) {
        if (factionOf(player.getUuid()) != null) {
            return Texts.err("You are already in a faction. Leave it before founding another.");
        }
        String name = rawName == null ? "" : rawName.trim();
        if (name.length() < config.minNameLength || name.length() > config.maxNameLength || !name.matches("[A-Za-z0-9_]+")) {
            return Texts.err("Names are " + config.minNameLength + "-" + config.maxNameLength + " letters, numbers, or underscores.");
        }
        String id = name.toLowerCase(Locale.ROOT);
        if (id.equals("safezone") || id.equals("warzone")) {
            return Texts.err("That name is reserved.");
        }
        if (data.factions.containsKey(id)) {
            return Texts.err("A faction named " + name + " already exists.");
        }
        Faction faction = new Faction();
        faction.id = id;
        faction.name = name;
        faction.color = COLORS[Math.floorMod(name.hashCode(), COLORS.length)];
        Member leader = new Member();
        leader.name = player.getUsername();
        leader.rank = "LEADER";
        faction.members.put(player.getUuid().toString(), leader);
        ensureProfile(player.getUuid(), player.getUsername());
        data.factions.put(id, faction);
        save();
        world.sendMessage(Texts.text(player.getUsername() + " founded the faction " + name + ".", faction.color));
        return Texts.ok("You founded " + name + ". Claim the chunk you are standing in with /f claim.");
    }

    public synchronized Message disband(PlayerRef player, World world) {
        Faction faction = factionOf(player.getUuid());
        if (faction == null) {
            return Texts.err("You are not in a faction.");
        }
        if (!isLeader(faction, player.getUuid())) {
            return Texts.err("Only the faction leader can disband it.");
        }
        if (faction.permanent) {
            return Texts.err(faction.name + " is permanent. Staff can remove it with /f admin disband.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        String name = faction.name;
        removeFaction(faction);
        world.sendMessage(Texts.err(name + " has been disbanded."));
        return Texts.ok("You disbanded " + name + " and released its claims.");
    }

    public synchronized Message invite(PlayerRef player, PlayerRef target) {
        Faction faction = factionOf(player.getUuid());
        if (!isOfficer(faction, player.getUuid())) {
            return Texts.err("Only a leader or officer can invite players.");
        }
        if (target.getUuid().equals(player.getUuid())) {
            return Texts.err("You are already in your faction.");
        }
        if (factionOf(target.getUuid()) != null) {
            return Texts.err(target.getUsername() + " is already in a faction.");
        }
        if (isBanned(faction, target.getUuid())) {
            return Texts.err(target.getUsername() + " is banned from " + faction.name + ".");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        Invite invite = new Invite();
        invite.factionId = faction.id;
        invite.expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(config.inviteMinutes);
        data.invites.put(target.getUuid().toString(), invite);
        save();
        target.sendMessage(Texts.ok(player.getUsername() + " invited you to " + faction.name + ". /f join to accept, /f deny to decline."));
        return Texts.ok("Invited " + target.getUsername() + " to " + faction.name + ".");
    }

    public synchronized Message join(PlayerRef player, World world, String requestedName) {
        if (factionOf(player.getUuid()) != null) {
            return Texts.err("Leave your current faction before joining another.");
        }
        Invite invite = data.invites.get(player.getUuid().toString());
        Faction faction = null;
        if (invite != null && invite.expiresAt < System.currentTimeMillis()) {
            data.invites.remove(player.getUuid().toString());
            invite = null;
            save();
        }
        if (invite != null) {
            faction = data.factions.get(invite.factionId);
            if (faction == null) {
                data.invites.remove(player.getUuid().toString());
                save();
                return Texts.err("That faction no longer exists.");
            }
            if (requestedName != null && !faction.name.equalsIgnoreCase(requestedName.trim())) {
                return Texts.err("Your invite is for " + faction.name + ", not " + requestedName.trim() + ".");
            }
        } else if (requestedName != null && !requestedName.isBlank()) {
            faction = findByName(requestedName);
            if (faction == null || !faction.open) {
                return Texts.err("You have no faction invite.");
            }
        } else {
            return Texts.err("You have no faction invite.");
        }
        if (isBanned(faction, player.getUuid())) {
            return Texts.err("You are banned from " + faction.name + ".");
        }
        Member member = new Member();
        member.name = player.getUsername();
        member.rank = "RECRUIT";
        faction.members.put(player.getUuid().toString(), member);
        data.invites.remove(player.getUuid().toString());
        ensureProfile(player.getUuid(), player.getUsername());
        save();
        tell(faction, Texts.ok(player.getUsername() + " joined " + faction.name + "."), player.getUuid());
        String announcement = faction.announcement == null || faction.announcement.isBlank() ? "" : " " + faction.announcement;
        return Texts.ok("You joined " + faction.name + " as a recruit. Power " + format(power(faction)) + ", land " + used(faction) + "." + announcement);
    }

    public synchronized Message deny(PlayerRef player) {
        Invite invite = data.invites.remove(player.getUuid().toString());
        if (invite == null) {
            return Texts.err("You have no faction invite.");
        }
        save();
        Faction faction = data.factions.get(invite.factionId);
        if (faction != null) {
            tell(faction, Texts.info(player.getUsername() + " declined the invite to " + faction.name + "."));
        }
        return Texts.ok("Invite declined.");
    }

    public synchronized Message leave(PlayerRef player, World world) {
        Faction faction = factionOf(player.getUuid());
        if (faction == null) {
            return Texts.err("You are not in a faction.");
        }
        Member self = faction.members.get(player.getUuid().toString());
        if ("LEADER".equals(self.rank)) {
            if (faction.permanent) {
                return Texts.err(faction.name + " is permanent. Transfer leadership before leaving.");
            }
            if (faction.members.size() == 1) {
                String name = faction.name;
                removeFaction(faction);
                world.sendMessage(Texts.info(name + " was disbanded because its leader left."));
                return Texts.ok("You left and disbanded " + name + ".");
            }
            return Texts.err("Transfer leadership with /f leader <player> before leaving, or /f disband --confirm.");
        }
        faction.members.remove(player.getUuid().toString());
        clearChat(player.getUuid().toString());
        save();
        tell(faction, Texts.info(player.getUsername() + " left " + faction.name + "."));
        return Texts.ok("You left " + faction.name + ".");
    }

    public synchronized Message kick(PlayerRef player, String rawName) {
        Faction faction = factionOf(player.getUuid());
        if (faction == null) {
            return Texts.err("You are not in a faction.");
        }
        if (!isOfficer(faction, player.getUuid())) {
            return Texts.err("Only a leader, co-leader, or officer can kick members.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        Found victim = findMember(faction, rawName);
        if (victim.error != null) {
            return victim.error;
        }
        if (victim.id.equals(player.getUuid().toString())) {
            return Texts.err("You cannot kick yourself. Use /f leave.");
        }
        Member actor = faction.members.get(player.getUuid().toString());
        if (rankValue(actor.rank) <= rankValue(victim.member.rank)) {
            return Texts.err("You can only kick a lower rank.");
        }
        faction.members.remove(victim.id);
        clearChat(victim.id);
        save();
        PlayerRef online = Universe.get().getPlayer(UUID.fromString(victim.id));
        if (online != null) {
            online.sendMessage(Texts.err("You were kicked from " + faction.name + "."));
        }
        tell(faction, Texts.info(victim.member.name + " was kicked from " + faction.name + "."), player.getUuid());
        return Texts.ok("Kicked " + victim.member.name + ".");
    }

    public synchronized Message promote(PlayerRef player, String rawName) {
        Faction faction = factionOf(player.getUuid());
        if (faction == null) {
            return Texts.err("You are not in a faction.");
        }
        if (!isColeader(faction, player.getUuid())) {
            return Texts.err("Only a leader or co-leader can promote members.");
        }
        Found victim = findMember(faction, rawName);
        if (victim.error != null) {
            return victim.error;
        }
        String nextRank = switch (victim.member.rank) {
            case "RECRUIT" -> "MEMBER";
            case "MEMBER" -> "OFFICER";
            case "OFFICER" -> "COLEADER";
            default -> null;
        };
        if (nextRank == null) {
            return Texts.err(victim.member.name + " cannot be promoted. Use /f leader to hand over the faction.");
        }
        if ("COLEADER".equals(nextRank) && !isLeader(faction, player.getUuid())) {
            return Texts.err("Only the leader can promote a co-leader.");
        }
        victim.member.rank = nextRank;
        save();
        tell(faction, Texts.ok(victim.member.name + " is now " + rankLabel(nextRank) + " of " + faction.name + "."), player.getUuid());
        return Texts.ok("Promoted " + victim.member.name + " to " + rankLabel(nextRank) + ".");
    }

    public synchronized Message demote(PlayerRef player, String rawName) {
        Faction faction = factionOf(player.getUuid());
        if (faction == null) {
            return Texts.err("You are not in a faction.");
        }
        if (!isColeader(faction, player.getUuid())) {
            return Texts.err("Only a leader or co-leader can demote members.");
        }
        Found victim = findMember(faction, rawName);
        if (victim.error != null) {
            return victim.error;
        }
        String nextRank = switch (victim.member.rank) {
            case "COLEADER" -> "OFFICER";
            case "OFFICER" -> "MEMBER";
            case "MEMBER" -> "RECRUIT";
            default -> null;
        };
        if (nextRank == null) {
            return Texts.err(victim.member.name + " is already a recruit.");
        }
        if ("COLEADER".equals(victim.member.rank) && !isLeader(faction, player.getUuid())) {
            return Texts.err("Only the leader can demote a co-leader.");
        }
        victim.member.rank = nextRank;
        save();
        tell(faction, Texts.info(victim.member.name + " is now " + rankLabel(nextRank) + " of " + faction.name + "."), player.getUuid());
        return Texts.ok("Demoted " + victim.member.name + " to " + rankLabel(nextRank) + ".");
    }

    public synchronized Message transfer(PlayerRef player, String rawName) {
        Faction faction = factionOf(player.getUuid());
        if (faction == null) {
            return Texts.err("You are not in a faction.");
        }
        if (!isLeader(faction, player.getUuid())) {
            return Texts.err("Only the leader can transfer the faction.");
        }
        Found next = findMember(faction, rawName);
        if (next.error != null) {
            return next.error;
        }
        if (next.id.equals(player.getUuid().toString())) {
            return Texts.err("You are already the leader.");
        }
        faction.members.get(player.getUuid().toString()).rank = "OFFICER";
        next.member.rank = "LEADER";
        save();
        tell(faction, Texts.ok(next.member.name + " now leads " + faction.name + "."), player.getUuid());
        return Texts.ok("You transferred leadership to " + next.member.name + ".");
    }

    public synchronized Message claim(PlayerRef player, World world) {
        int chunkX = chunkX(player);
        int chunkZ = chunkZ(player);
        String result = tryClaim(player, world.getName(), chunkX, chunkZ);
        if (result == null) {
            save();
            Faction faction = factionOf(player.getUuid());
            return Texts.ok("Claimed chunk " + chunkX + ", " + chunkZ + ". Land " + used(faction) + ", power " + format(power(faction)) + ".");
        }
        if (result.isEmpty()) {
            return Texts.info("Your faction already owns this chunk.");
        }
        return Texts.err(result);
    }

    public synchronized Message unclaim(PlayerRef player, World world) {
        Faction faction = factionOf(player.getUuid());
        if (!isOfficer(faction, player.getUuid())) {
            return Texts.err("Only a leader or officer can unclaim land.");
        }
        int chunkX = chunkX(player);
        int chunkZ = chunkZ(player);
        String key = claimKey(world.getName(), chunkX, chunkZ);
        if (!faction.id.equals(data.claims.get(key))) {
            return Texts.err("Your faction does not own this chunk.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        data.claims.remove(key);
        Plot plot = data.plots.remove(key);
        int refundChunks = plot == null || plot.paid ? 1 : 0;
        refund(player, refundChunks);
        touchMap(world.getName());
        save();
        String money = refundChunks > 0 && config.unclaimRefund && config.claimCost > 0 ? " Refunded " + format(config.claimCost) + "." : "";
        return Texts.ok("Released chunk " + chunkX + ", " + chunkZ + "." + money + " Land " + used(faction) + ", power " + format(power(faction)) + ".");
    }

    public synchronized Message unclaimAll(PlayerRef player) {
        Faction faction = factionOf(player.getUuid());
        if (!isColeader(faction, player.getUuid())) {
            return Texts.err("Only a leader or co-leader can unclaim all land.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        int removed = 0;
        int refundChunks = 0;
        var iterator = data.claims.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (faction.id.equals(entry.getValue())) {
                Plot plot = data.plots.remove(entry.getKey());
                if (plot == null || plot.paid) {
                    refundChunks++;
                }
                touchMap(worldOfClaim(entry.getKey()));
                iterator.remove();
                removed++;
            }
        }
        refund(player, refundChunks);
        save();
        double refunded = config.unclaimRefund ? refundChunks * config.claimCost : 0;
        String money = refunded > 0 ? " Refunded " + format(refunded) + "." : "";
        return Texts.ok("Released " + removed + " chunks." + money + " Power " + format(power(faction)) + ".");
    }

    public synchronized Message autoClaim(PlayerRef player, World world) {
        Faction faction = factionOf(player.getUuid());
        if (!isOfficer(faction, player.getUuid())) {
            return Texts.err("Only a leader or officer can claim land.");
        }
        Profile profile = ensureProfile(player.getUuid(), player.getUsername());
        profile.autoClaim = !profile.autoClaim;
        save();
        if (!profile.autoClaim) {
            autoClaimErrors.remove(player.getUuid());
            return Texts.ok("Autoclaim off.");
        }
        String result = tryClaim(player, world.getName(), chunkX(player), chunkZ(player));
        if (result == null) {
            save();
        }
        String now = result == null ? " This chunk is claimed." : (result.isEmpty() ? "" : " " + result);
        return Texts.ok("Autoclaim on. Walk the border to claim." + now);
    }

    public synchronized Message claimLine(PlayerRef player, World world, int count) {
        return claimShape(player, world, Math.max(1, Math.min(count, 32)), 0, false);
    }

    public synchronized Message claimSquare(PlayerRef player, World world, int radius) {
        return claimShape(player, world, 0, Math.max(0, Math.min(radius, 7)), false);
    }

    public synchronized Message claimCircle(PlayerRef player, World world, int radius) {
        return claimShape(player, world, 0, Math.max(0, Math.min(radius, 7)), true);
    }

    public synchronized Message claimFill(PlayerRef player, World world) {
        Faction faction = factionOf(player.getUuid());
        if (!isOfficer(faction, player.getUuid())) {
            return Texts.err("Only a leader or officer can claim land.");
        }
        int originX = chunkX(player);
        int originZ = chunkZ(player);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        HashSet<Long> seen = new HashSet<>();
        List<int[]> claimable = new ArrayList<>();
        queue.add(new int[] {originX, originZ});
        while (!queue.isEmpty()) {
            int[] current = queue.removeFirst();
            long packed = (((long) current[0]) << 32) ^ (current[1] & 0xffffffffL);
            if (!seen.add(packed)) {
                continue;
            }
            String existing = data.claims.get(claimKey(world.getName(), current[0], current[1]));
            if (existing != null && !faction.id.equals(existing)) {
                continue;
            }
            claimable.add(current);
            if (claimable.size() > 64) {
                return Texts.err("That area is not enclosed. Fill stops at 64 chunks.");
            }
            queue.add(new int[] {current[0] + 1, current[1]});
            queue.add(new int[] {current[0] - 1, current[1]});
            queue.add(new int[] {current[0], current[1] + 1});
            queue.add(new int[] {current[0], current[1] - 1});
        }
        int claimed = 0;
        String failure = null;
        for (int[] chunk : claimable) {
            String result = tryClaim(player, world.getName(), chunk[0], chunk[1]);
            if (result == null) {
                claimed++;
            } else if (!result.isEmpty()) {
                failure = result;
                break;
            }
        }
        if (claimed > 0) {
            save();
        }
        if (failure != null) {
            return Texts.err("Claimed " + claimed + " chunks. " + failure);
        }
        return Texts.ok("Filled " + claimed + " chunks. Land " + used(faction) + ", power " + format(power(faction)) + ".");
    }

    public synchronized Message map(PlayerRef player, World world) {
        int originX = chunkX(player);
        int originZ = chunkZ(player);
        String here = data.claims.get(claimKey(world.getName(), originX, originZ));
        Faction hereFaction = here == null ? null : data.factions.get(here);
        player.sendMessage(Texts.info("Chunk " + originX + ", " + originZ + " · " + (hereFaction == null ? "Wilderness" : hereFaction.name) + " · north is up"));
        Faction mine = factionOf(player.getUuid());
        for (int dz = -3; dz <= 3; dz++) {
            Message row = Message.empty();
            for (int dx = -3; dx <= 3; dx++) {
                boolean center = dx == 0 && dz == 0;
                String ownerId = data.claims.get(claimKey(world.getName(), originX + dx, originZ + dz));
                String symbol = ".";
                String color = "#555555";
                if (ownerId != null) {
                    Faction owner = data.factions.get(ownerId);
                    if (owner != null && "SAFEZONE".equals(owner.kind)) {
                        symbol = "S";
                        color = "#55FFFF";
                    } else if (owner != null && "WARZONE".equals(owner.kind)) {
                        symbol = "W";
                        color = "#FF5555";
                    } else if (mine != null && mine.id.equals(ownerId)) {
                        symbol = "+";
                        color = mine.color;
                    } else if (mine != null && "ALLY".equals(mine.relations.get(ownerId))) {
                        symbol = "A";
                        color = "#55FF55";
                    } else if (mine != null && "TRUCE".equals(mine.relations.get(ownerId))) {
                        symbol = "T";
                        color = "#FFFF55";
                    } else if (mine != null && "ENEMY".equals(mine.relations.get(ownerId))) {
                        symbol = "E";
                        color = "#FF5555";
                    } else {
                        symbol = "#";
                        color = owner == null ? "#FFAA00" : owner.color;
                    }
                }
                row.insert(Message.raw(symbol).color(color).bold(center));
                row.insert(Message.raw(" "));
            }
            player.sendMessage(row);
        }
        player.sendMessage(Texts.info("+ yours   A ally   T truce   E enemy   S safe   W war   # other   . wild   bold cell is you"));
        player.sendMessage(Texts.info("The world map colors claimed land and marks each faction."));
        return null;
    }

    public synchronized Message setHome(PlayerRef player, World world) {
        Faction faction = factionOf(player.getUuid());
        if (!isOfficer(faction, player.getUuid())) {
            return Texts.err("Only a leader or officer can set the faction home.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        Transform transform = player.getTransform();
        Vector3d position = transform.getPosition();
        Rotation3f rotation = transform.getRotation();
        Home home = new Home();
        home.world = world.getName();
        home.x = position.x;
        home.y = position.y;
        home.z = position.z;
        home.pitch = rotation.pitch();
        home.yaw = rotation.yaw();
        home.roll = rotation.roll();
        faction.home = home;
        save();
        return Texts.ok("Faction home set. Members can return with /f home.");
    }

    public synchronized Message home(PlayerRef player, World world) {
        Faction faction = factionOf(player.getUuid());
        if (faction == null) {
            return Texts.err("You are not in a faction.");
        }
        if (faction.home == null) {
            return Texts.err("Your faction has no home. An officer can set one with /f sethome.");
        }
        Vector3d start = new Vector3d(player.getTransform().getPosition());
        UUID playerId = player.getUuid();
        String expectedWorld = world.getName();
        int seconds = Math.max(0, config.homeWarmupSeconds);
        schedule(playerId, () -> finishHome(playerId, expectedWorld, start), seconds);
        if (seconds <= 0) {
            return null;
        }
        return Texts.info("Teleporting to the faction home in " + seconds + " seconds. Move to cancel.");
    }

    public synchronized Message toggleChat(PlayerRef player) {
        return setChatMode(player, null);
    }

    public synchronized Message setChatMode(PlayerRef player, String requested) {
        Faction faction = factionOf(player.getUuid());
        if (faction == null) {
            return Texts.err("You are not in a faction.");
        }
        Profile profile = ensureProfile(player.getUuid(), player.getUsername());
        String mode;
        if (requested == null || requested.isBlank()) {
            mode = switch (profile.chatMode) {
                case "FACTION" -> "ALLY";
                case "ALLY" -> "TRUCE";
                case "TRUCE" -> "PUBLIC";
                default -> "FACTION";
            };
        } else {
            mode = requested.trim().toUpperCase(Locale.ROOT);
            if (!mode.equals("PUBLIC") && !mode.equals("FACTION") && !mode.equals("ALLY") && !mode.equals("TRUCE")) {
                return Texts.err("Use public, faction, ally, or truce.");
            }
        }
        profile.chatMode = mode;
        save();
        if ("FACTION".equals(mode)) {
            return Texts.ok("Faction chat on. Messages go to " + faction.name + ". /f chat again for ally chat. Start a line with ! for public.");
        }
        if ("ALLY".equals(mode)) {
            return Texts.ok("Ally chat on. Messages go to " + faction.name + " and its allies.");
        }
        if ("TRUCE".equals(mode)) {
            return Texts.ok("Truce chat on. Messages go to " + faction.name + " and its truces.");
        }
        return Texts.ok("Public chat on. Your tag color follows each viewer's relation.");
    }

    public synchronized Message say(PlayerRef player, String text) {
        Faction faction = factionOf(player.getUuid());
        if (faction == null) {
            return Texts.err("You are not in a faction.");
        }
        if (text == null || text.isBlank()) {
            return Texts.err("Usage: /fc <message>");
        }
        Message line = factionLine(faction.name, faction.color, player.getUsername(), text.trim());
        for (PlayerRef member : online(faction)) {
            member.sendMessage(line);
        }
        return null;
    }

    public synchronized Message info(PlayerRef player, String rawName) {
        Faction faction;
        if (rawName == null || rawName.isBlank()) {
            faction = factionOf(player.getUuid());
            if (faction == null) {
                return Texts.err("You are not in a faction. /f info <name> looks one up.");
            }
        } else {
            faction = findByName(rawName);
            if (faction == null) {
                return Texts.err("No faction named " + rawName.trim() + ".");
            }
        }
        player.sendMessage(Texts.text(chatTag(faction), faction.color).bold(true));
        if (faction.title != null && !faction.title.isBlank()) {
            player.sendMessage(Texts.text(faction.title, faction.color));
        }
        if (faction.description != null && !faction.description.isBlank()) {
            player.sendMessage(Texts.info(faction.description));
        }
        if (faction.announcement != null && !faction.announcement.isBlank()) {
            player.sendMessage(Texts.info(faction.announcement));
        }
        if (isSystem(faction)) {
            player.sendMessage(Texts.info(used(faction) + " chunks. This land cannot be overclaimed."));
            return null;
        }
        double totalPower = power(faction);
        int land = used(faction);
        boolean shielded = faction.permanent || faction.peaceful || (config.protectOfflineFactions && !isOnline(faction));
        String vulnerable = !shielded && land > totalPower ? " · vulnerable to overclaim" : "";
        player.sendMessage(Texts.info("Power " + format(totalPower) + statusSuffix(faction) + " · land " + land + vulnerable + " · home " + (faction.home == null ? "not set" : "set in " + faction.home.world)));
        player.sendMessage(Texts.info("Bank " + format(faction.bank) + " · warps " + faction.warps.size() + (faction.open ? " · open" : " · invite only")));
        Faction mine = factionOf(player.getUuid());
        if (mine != null && mine.id.equals(faction.id)) {
            Profile profile = ensureProfile(player.getUuid(), player.getUsername());
            player.sendMessage(Texts.info("You: power " + format(profile.power) + "/" + format(config.maxPower) + " + " + format(profile.powerBoost) + " boost, balance " + format(profile.balance)));
        }
        player.sendMessage(Texts.info("Leader: " + namesWithRank(faction, "LEADER")));
        String coleaders = namesWithRank(faction, "COLEADER");
        if (!coleaders.isEmpty()) {
            player.sendMessage(Texts.info("Co-leaders: " + coleaders));
        }
        String officers = namesWithRank(faction, "OFFICER");
        if (!officers.isEmpty()) {
            player.sendMessage(Texts.info("Officers: " + officers));
        }
        String members = namesWithRank(faction, "MEMBER");
        if (!members.isEmpty()) {
            player.sendMessage(Texts.info("Members: " + members));
        }
        String recruits = namesWithRank(faction, "RECRUIT");
        if (!recruits.isEmpty()) {
            player.sendMessage(Texts.info("Recruits: " + recruits));
        }
        String allies = relationNames(faction, "ALLY");
        String truces = relationNames(faction, "TRUCE");
        String enemies = relationNames(faction, "ENEMY");
        if (!allies.isEmpty()) {
            player.sendMessage(Texts.text("Allies: " + allies, "#55FF55"));
        }
        if (!truces.isEmpty()) {
            player.sendMessage(Texts.text("Truces: " + truces, "#FFFF55"));
        }
        if (!enemies.isEmpty()) {
            player.sendMessage(Texts.text("Enemies: " + enemies, "#FF5555"));
        }
        if (!faction.allyRequests.isEmpty()) {
            player.sendMessage(Texts.info("Alliance requests: " + requestNames(faction.allyRequests)));
        }
        if (!faction.truceRequests.isEmpty()) {
            player.sendMessage(Texts.info("Truce requests: " + requestNames(faction.truceRequests)));
        }
        return null;
    }

    public synchronized Message list(PlayerRef player) {
        if (data.factions.isEmpty()) {
            return Texts.info("No factions yet. /f create <name> founds one.");
        }
        List<Faction> factions = new ArrayList<>(data.factions.values());
        factions.sort(Comparator.comparing(faction -> faction.name.toLowerCase(Locale.ROOT)));
        for (Faction faction : factions) {
            String detail = isSystem(faction)
                    ? used(faction) + " chunks"
                    : faction.members.size() + " members, power " + format(power(faction)) + ", land " + used(faction) + statusSuffix(faction);
            player.sendMessage(Texts.text(faction.name + " — " + detail, faction.color));
        }
        return null;
    }

    public synchronized Message describe(PlayerRef player, String text) {
        Faction faction = factionOf(player.getUuid());
        if (!isOfficer(faction, player.getUuid())) {
            return Texts.err("Only a leader or officer can set the description.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        String description = text == null ? "" : text.trim();
        if (description.length() > 120) {
            return Texts.err("Keep the description to 120 characters.");
        }
        faction.description = description;
        save();
        return Texts.ok("Description updated.");
    }

    public synchronized Message ally(PlayerRef player, String rawName) {
        Faction faction = factionOf(player.getUuid());
        if (!isColeader(faction, player.getUuid())) {
            return Texts.err("Only a leader or co-leader can set relations.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        Faction other = findByName(rawName);
        if (other == null) {
            return Texts.err("No faction named " + rawName.trim() + ".");
        }
        if (other.id.equals(faction.id) || isSystem(other) || isSystem(faction)) {
            return Texts.err("You cannot ally that faction.");
        }
        if ("ALLY".equals(faction.relations.get(other.id))) {
            return Texts.info(other.name + " is already an ally.");
        }
        if (faction.allyRequests.remove(other.id)) {
            link(faction, other, "ALLY");
            save();
            tell(faction, Texts.ok(faction.name + " and " + other.name + " are now allies."));
            tell(other, Texts.ok(faction.name + " and " + other.name + " are now allies."));
            return null;
        }
        if (other.allyRequests.contains(faction.id)) {
            return Texts.info("You already asked " + other.name + " for an alliance.");
        }
        other.allyRequests.add(faction.id);
        save();
        tell(other, Texts.ok(faction.name + " wants an alliance. A leader or officer can accept with /f ally " + faction.name + "."));
        return Texts.ok("Asked " + other.name + " for an alliance.");
    }

    public synchronized Message truce(PlayerRef player, String rawName) {
        Faction faction = factionOf(player.getUuid());
        if (!isColeader(faction, player.getUuid())) {
            return Texts.err("Only a leader or co-leader can set relations.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        Faction other = findByName(rawName);
        if (other == null) {
            return Texts.err("No faction named " + rawName.trim() + ".");
        }
        if (other.id.equals(faction.id) || isSystem(other) || isSystem(faction)) {
            return Texts.err("You cannot truce that faction.");
        }
        if ("TRUCE".equals(faction.relations.get(other.id))) {
            return Texts.info(other.name + " is already under truce.");
        }
        if ("ALLY".equals(faction.relations.get(other.id))) {
            return Texts.info(other.name + " is an ally. /f neutral " + other.name + " first.");
        }
        if (faction.truceRequests.remove(other.id)) {
            link(faction, other, "TRUCE");
            save();
            tell(faction, Texts.ok(faction.name + " and " + other.name + " are now under truce."));
            tell(other, Texts.ok(faction.name + " and " + other.name + " are now under truce."));
            return null;
        }
        if (other.truceRequests.contains(faction.id)) {
            return Texts.info("You already asked " + other.name + " for a truce.");
        }
        other.truceRequests.add(faction.id);
        save();
        tell(other, Texts.ok(faction.name + " wants a truce. A leader or officer can accept with /f truce " + faction.name + "."));
        return Texts.ok("Asked " + other.name + " for a truce.");
    }

    public synchronized Message enemy(PlayerRef player, String rawName) {
        Faction faction = factionOf(player.getUuid());
        if (!isColeader(faction, player.getUuid())) {
            return Texts.err("Only a leader or co-leader can set relations.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        Faction other = findByName(rawName);
        if (other == null) {
            return Texts.err("No faction named " + rawName.trim() + ".");
        }
        if (other.id.equals(faction.id) || isSystem(other) || isSystem(faction)) {
            return Texts.err("You cannot mark that faction as an enemy.");
        }
        if (faction.peaceful || other.peaceful) {
            return Texts.err("A peaceful faction cannot be an enemy.");
        }
        link(faction, other, "ENEMY");
        save();
        tell(faction, Texts.err(other.name + " is now an enemy of " + faction.name + "."));
        tell(other, Texts.err(faction.name + " marked " + other.name + " as an enemy."));
        return null;
    }

    public synchronized Message neutral(PlayerRef player, String rawName) {
        Faction faction = factionOf(player.getUuid());
        if (!isColeader(faction, player.getUuid())) {
            return Texts.err("Only a leader or co-leader can set relations.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        Faction other = findByName(rawName);
        if (other == null) {
            return Texts.err("No faction named " + rawName.trim() + ".");
        }
        if (other.id.equals(faction.id) || isSystem(other) || isSystem(faction)) {
            return Texts.err("That relation cannot be changed.");
        }
        boolean changed = faction.relations.remove(other.id) != null || other.relations.remove(faction.id) != null;
        changed |= faction.allyRequests.remove(other.id);
        changed |= other.allyRequests.remove(faction.id);
        changed |= faction.truceRequests.remove(other.id);
        changed |= other.truceRequests.remove(faction.id);
        if (!changed) {
            return Texts.info("You are already neutral with " + other.name + ".");
        }
        save();
        tell(faction, Texts.info(faction.name + " and " + other.name + " are neutral."));
        tell(other, Texts.info(faction.name + " and " + other.name + " are neutral."));
        return null;
    }

    public synchronized Message adminBypass(PlayerRef player) {
        boolean enabled;
        if (bypass.remove(player.getUuid())) {
            enabled = false;
        } else {
            bypass.add(player.getUuid());
            enabled = true;
        }
        data.bypassIds.clear();
        for (UUID id : bypass) {
            data.bypassIds.add(id.toString());
        }
        save();
        if (!enabled) {
            return Texts.info("Claim bypass off. Other factions' land is protected from you again.");
        }
        return Texts.ok("Claim bypass on. It stays on until you toggle it off.");
    }

    public synchronized Message adminDisband(World world, String rawName) {
        Faction faction = findByName(rawName);
        if (faction == null) {
            return Texts.err("No faction named " + rawName.trim() + ".");
        }
        String name = faction.name;
        removeFaction(faction);
        world.sendMessage(Texts.err(name + " was disbanded by staff."));
        return Texts.ok("Disbanded " + name + ".");
    }

    public synchronized Message adminUnclaim(PlayerRef player, World world) {
        int chunkX = chunkX(player);
        int chunkZ = chunkZ(player);
        String removed = data.claims.remove(claimKey(world.getName(), chunkX, chunkZ));
        if (removed == null) {
            return Texts.err("This chunk is wilderness.");
        }
        data.plots.remove(claimKey(world.getName(), chunkX, chunkZ));
        touchMap(world.getName());
        save();
        Faction owner = data.factions.get(removed);
        return Texts.ok("Unclaimed chunk " + chunkX + ", " + chunkZ + " from " + (owner == null ? removed : owner.name) + ".");
    }

    public synchronized Message adminZone(PlayerRef player, World world, String kind) {
        Faction zone = ensureZone(kind);
        int chunkX = chunkX(player);
        int chunkZ = chunkZ(player);
        String key = claimKey(world.getName(), chunkX, chunkZ);
        String existing = data.claims.get(key);
        if (zone.id.equals(existing)) {
            return Texts.info("This chunk is already " + zone.name + ".");
        }
        data.claims.put(key, zone.id);
        touchMap(world.getName());
        save();
        String previous = "";
        if (existing != null) {
            Faction owner = data.factions.get(existing);
            previous = " Replaced " + (owner == null ? "a claim" : owner.name) + ".";
        }
        world.sendMessage(Texts.text(zone.name + " now covers chunk " + chunkX + ", " + chunkZ + ".", zone.color));
        return Texts.ok("Marked chunk " + chunkX + ", " + chunkZ + " as " + zone.name + "." + previous);
    }

    public synchronized Message money(PlayerRef player) {
        Profile profile = ensureProfile(player.getUuid(), player.getUsername());
        Faction faction = factionOf(player.getUuid());
        if (faction == null || isSystem(faction)) {
            return Texts.info("Balance: " + format(profile.balance) + ".");
        }
        return Texts.info("Balance: " + format(profile.balance) + ". " + faction.name + " bank: " + format(faction.bank) + ".");
    }

    public synchronized Message deposit(PlayerRef player, int amount) {
        if (amount <= 0) {
            return Texts.err("Deposit a positive amount.");
        }
        Faction faction = factionOf(player.getUuid());
        if (faction == null) {
            return Texts.err("You are not in a faction.");
        }
        Profile profile = ensureProfile(player.getUuid(), player.getUsername());
        if (profile.balance + 0.001 < amount) {
            return Texts.err("You only have " + format(profile.balance) + ".");
        }
        profile.balance = round(profile.balance - amount);
        faction.bank = round(faction.bank + amount);
        save();
        return Texts.ok("Deposited " + amount + ". Balance " + format(profile.balance) + ", bank " + format(faction.bank) + ".");
    }

    public synchronized Message withdraw(PlayerRef player, int amount) {
        if (amount <= 0) {
            return Texts.err("Withdraw a positive amount.");
        }
        Faction faction = factionOf(player.getUuid());
        if (!isColeader(faction, player.getUuid())) {
            return Texts.err("Only a leader or co-leader can withdraw from the bank.");
        }
        if (faction.bank + 0.001 < amount) {
            return Texts.err("The bank only has " + format(faction.bank) + ".");
        }
        Profile profile = ensureProfile(player.getUuid(), player.getUsername());
        faction.bank = round(faction.bank - amount);
        profile.balance = round(profile.balance + amount);
        save();
        tell(faction, Texts.info(player.getUsername() + " withdrew " + amount + " from the bank."), player.getUuid());
        return Texts.ok("Withdrew " + amount + ". Balance " + format(profile.balance) + ", bank " + format(faction.bank) + ".");
    }

    public synchronized Message power(PlayerRef player) {
        Profile profile = ensureProfile(player.getUuid(), player.getUsername());
        Faction faction = factionOf(player.getUuid());
        String line = "Your power is " + format(profile.power) + "/" + format(config.maxPower) + ".";
        if (faction != null && !isSystem(faction)) {
            double total = power(faction);
            int land = used(faction);
            line += " " + faction.name + " has " + format(total) + " power and " + land + " land.";
            if (land > total) {
                line += " The extra land can be overclaimed.";
            }
        }
        return Texts.info(line);
    }

    public synchronized Message adminGive(String rawName, int amount) {
        if (amount == 0) {
            return Texts.err("Amount cannot be 0.");
        }
        String id = resolvePlayerId(rawName);
        if (id == null) {
            return Texts.err("No player named " + rawName.trim() + ".");
        }
        if (id.isEmpty()) {
            return Texts.err("More than one player is named " + rawName.trim() + ".");
        }
        Profile profile = ensureProfile(UUID.fromString(id), rawName.trim());
        profile.balance = round(Math.max(0, profile.balance + amount));
        save();
        PlayerRef online = Universe.get().getPlayer(UUID.fromString(id));
        if (online != null) {
            online.sendMessage(Texts.ok("Your balance is now " + format(profile.balance) + "."));
        }
        return Texts.ok(profile.name + " now has " + format(profile.balance) + ".");
    }

    public synchronized Message adminSetPower(String rawName, double amount) {
        String id = resolvePlayerId(rawName);
        if (id == null) {
            return Texts.err("No player named " + rawName.trim() + ".");
        }
        if (id.isEmpty()) {
            return Texts.err("More than one player is named " + rawName.trim() + ".");
        }
        Profile profile = ensureProfile(UUID.fromString(id), rawName.trim());
        profile.power = round(Math.max(config.minPower, Math.min(config.maxPower, amount)));
        profile.lastPowerTick = System.currentTimeMillis();
        save();
        PlayerRef online = Universe.get().getPlayer(UUID.fromString(id));
        if (online != null) {
            online.sendMessage(Texts.info("Your power is now " + format(profile.power) + "."));
        }
        return Texts.ok(profile.name + " now has " + format(profile.power) + " power.");
    }

    public synchronized Message owner(PlayerRef player, World world, String rawName) {
        Faction faction = factionOf(player.getUuid());
        if (faction == null) {
            return Texts.err("You are not in a faction.");
        }
        String key = claimKey(world.getName(), chunkX(player), chunkZ(player));
        if (!faction.id.equals(data.claims.get(key))) {
            return Texts.err("Your faction does not own this chunk.");
        }
        Plot plot = data.plots.computeIfAbsent(key, ignored -> new Plot());
        if (rawName == null || rawName.isBlank()) {
            String name = plot.ownerName == null ? "the faction" : plot.ownerName;
            String access = plot.accessNames == null || plot.accessNames.isEmpty() ? "none" : String.join(", ", plot.accessNames);
            return Texts.info("Chunk owner: " + name + ". Access: " + access + ".");
        }
        if (!isColeader(faction, player.getUuid()) && !player.getUuid().toString().equals(plot.ownerId)) {
            return Texts.err("Only a co-leader or the chunk owner can change ownership.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        Found member = findMember(faction, rawName);
        if (member.error != null) {
            return member.error;
        }
        plot.ownerId = member.id;
        plot.ownerName = member.member.name;
        save();
        return Texts.ok(member.member.name + " now owns this chunk.");
    }

    public synchronized Message access(PlayerRef player, World world, String rawName) {
        Faction faction = factionOf(player.getUuid());
        if (faction == null) {
            return Texts.err("You are not in a faction.");
        }
        String key = claimKey(world.getName(), chunkX(player), chunkZ(player));
        if (!faction.id.equals(data.claims.get(key))) {
            return Texts.err("Your faction does not own this chunk.");
        }
        Plot plot = data.plots.computeIfAbsent(key, ignored -> new Plot());
        if (plot.access == null) {
            plot.access = new ArrayList<>();
        }
        if (plot.accessNames == null) {
            plot.accessNames = new ArrayList<>();
        }
        if (!isColeader(faction, player.getUuid()) && !player.getUuid().toString().equals(plot.ownerId)) {
            return Texts.err("Only a co-leader or the chunk owner can grant access.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        String id = resolvePlayerId(rawName);
        if (id == null) {
            return Texts.err("No player named " + rawName.trim() + ".");
        }
        if (id.isEmpty()) {
            return Texts.err("More than one player is named " + rawName.trim() + ".");
        }
        if (plot.access.remove(id)) {
            plot.accessNames.removeIf(name -> name.equalsIgnoreCase(rawName.trim()));
            save();
            return Texts.ok("Removed chunk access for " + rawName.trim() + ".");
        }
        plot.access.add(id);
        plot.accessNames.add(rawName.trim());
        save();
        return Texts.ok(rawName.trim() + " can build in this chunk.");
    }

    public synchronized Message entry(PlayerRef player, String rawMode) {
        Faction faction = factionOf(player.getUuid());
        if (!isColeader(faction, player.getUuid())) {
            return Texts.err("Only a leader or co-leader can change entry.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        String mode = rawMode == null ? "" : rawMode.trim().toLowerCase(Locale.ROOT);
        boolean allow;
        if (mode.equals("allow") || mode.equals("on") || mode.equals("true")) {
            allow = true;
        } else if (mode.equals("deny") || mode.equals("off") || mode.equals("false")) {
            allow = false;
        } else {
            return Texts.err("Use /f entry allow or /f entry deny.");
        }
        faction.enemyEnter = allow;
        save();
        return Texts.ok(allow ? "Enemies can enter your land." : "Enemies are turned away at the border.");
    }

    public synchronized Message open(PlayerRef player) {
        Faction faction = factionOf(player.getUuid());
        if (!isColeader(faction, player.getUuid())) {
            return Texts.err("Only a leader or co-leader can open the faction.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        faction.open = !faction.open;
        save();
        return Texts.ok(faction.open ? faction.name + " is open. Anyone can join with /f join " + faction.name + "." : faction.name + " is invite only.");
    }

    public synchronized Message ban(PlayerRef player, String rawName) {
        Faction faction = factionOf(player.getUuid());
        if (!isColeader(faction, player.getUuid())) {
            return Texts.err("Only a leader or co-leader can ban players.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        String id = resolvePlayerId(rawName);
        if (id == null) {
            return Texts.err("No player named " + rawName.trim() + ".");
        }
        if (id.isEmpty()) {
            return Texts.err("More than one player is named " + rawName.trim() + ".");
        }
        Member victim = faction.members.get(id);
        if (victim != null && rankValue(faction.members.get(player.getUuid().toString()).rank) <= rankValue(victim.rank)) {
            return Texts.err("You can only ban a lower rank.");
        }
        if (victim != null) {
            faction.members.remove(id);
            clearChat(id);
            PlayerRef online = Universe.get().getPlayer(UUID.fromString(id));
            if (online != null) {
                online.sendMessage(Texts.err("You were banned from " + faction.name + "."));
            }
        }
        if (!faction.bans.contains(id)) {
            faction.bans.add(id);
        }
        faction.banNames.put(id, rawName.trim());
        save();
        return Texts.ok("Banned " + rawName.trim() + " from " + faction.name + ".");
    }

    public synchronized Message unban(PlayerRef player, String rawName) {
        Faction faction = factionOf(player.getUuid());
        if (!isColeader(faction, player.getUuid())) {
            return Texts.err("Only a leader or co-leader can unban players.");
        }
        String needle = rawName == null ? "" : rawName.trim();
        String found = null;
        for (Map.Entry<String, String> entry : faction.banNames.entrySet()) {
            if (needle.equalsIgnoreCase(entry.getValue())) {
                found = entry.getKey();
                break;
            }
        }
        if (found == null) {
            String id = resolvePlayerId(rawName);
            if (id != null && !id.isEmpty() && faction.bans.contains(id)) {
                found = id;
            }
        }
        if (found == null) {
            return Texts.err("Nobody named " + needle + " is banned.");
        }
        faction.bans.remove(found);
        faction.banNames.remove(found);
        save();
        return Texts.ok("Unbanned " + needle + ".");
    }

    public synchronized Message tag(PlayerRef player, String rawTag) {
        Faction faction = factionOf(player.getUuid());
        if (!isColeader(faction, player.getUuid())) {
            return Texts.err("Only a leader or co-leader can set the tag.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        String tag = rawTag == null ? "" : rawTag.trim();
        if (tag.length() < 2 || tag.length() > 10 || !tag.matches("[A-Za-z0-9_]+")) {
            return Texts.err("Tags are 2-10 letters, numbers, or underscores.");
        }
        faction.tag = tag;
        save();
        return Texts.ok("Chat tag set to [" + tag + "].");
    }

    public synchronized Message title(PlayerRef player, String text) {
        Faction faction = factionOf(player.getUuid());
        if (!isOfficer(faction, player.getUuid())) {
            return Texts.err("Only a leader or officer can set the title.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        String title = text == null ? "" : text.trim();
        if (title.length() > 48) {
            return Texts.err("Keep the title to 48 characters.");
        }
        faction.title = title;
        save();
        return Texts.ok("Title updated.");
    }

    public synchronized Message announce(PlayerRef player, String text) {
        Faction faction = factionOf(player.getUuid());
        if (!isColeader(faction, player.getUuid())) {
            return Texts.err("Only a leader or co-leader can set the announcement.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        String announcement = text == null ? "" : text.trim();
        if (announcement.length() > 120) {
            return Texts.err("Keep the announcement to 120 characters.");
        }
        faction.announcement = announcement;
        save();
        return Texts.ok("Announcement updated. It shows when someone enters your land.");
    }

    public synchronized Message peaceful(PlayerRef player) {
        Faction faction = factionOf(player.getUuid());
        if (!isLeader(faction, player.getUuid())) {
            return Texts.err("Only the leader can toggle peaceful.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        faction.peaceful = !faction.peaceful;
        if (faction.peaceful) {
            List<String> enemies = new ArrayList<>();
            for (Map.Entry<String, String> entry : faction.relations.entrySet()) {
                if ("ENEMY".equals(entry.getValue())) {
                    enemies.add(entry.getKey());
                }
            }
            for (String enemyId : enemies) {
                faction.relations.remove(enemyId);
                Faction other = data.factions.get(enemyId);
                if (other != null) {
                    other.relations.remove(faction.id);
                }
            }
        }
        save();
        return Texts.ok(faction.peaceful ? faction.name + " is peaceful. It cannot fight or be overclaimed." : faction.name + " is no longer peaceful.");
    }

    public synchronized Message setWarp(PlayerRef player, World world, String rawName, String password) {
        Faction faction = factionOf(player.getUuid());
        if (!isOfficer(faction, player.getUuid())) {
            return Texts.err("Only a leader or officer can set warps.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        String name = rawName == null ? "" : rawName.trim();
        if (name.length() < 2 || name.length() > 16 || !name.matches("[A-Za-z0-9_]+")) {
            return Texts.err("Warp names are 2-16 letters, numbers, or underscores.");
        }
        Warp warp = findWarp(faction, name);
        if (warp == null && faction.warps.size() >= 8) {
            return Texts.err("Your faction already has 8 warps.");
        }
        if (warp == null) {
            warp = new Warp();
            warp.name = name;
            faction.warps.add(warp);
        }
        Transform transform = player.getTransform();
        Vector3d position = transform.getPosition();
        Rotation3f rotation = transform.getRotation();
        warp.password = password == null ? "" : password.trim();
        warp.world = world.getName();
        warp.x = position.x;
        warp.y = position.y;
        warp.z = position.z;
        warp.pitch = rotation.pitch();
        warp.yaw = rotation.yaw();
        warp.roll = rotation.roll();
        save();
        return Texts.ok("Warp " + name + " set" + (warp.password.isEmpty() ? "." : " with a password."));
    }

    public synchronized Message deleteWarp(PlayerRef player, String rawName) {
        Faction faction = factionOf(player.getUuid());
        if (!isOfficer(faction, player.getUuid())) {
            return Texts.err("Only a leader or officer can delete warps.");
        }
        Message locked = ensureUnlocked(faction);
        if (locked != null) {
            return locked;
        }
        Warp warp = findWarp(faction, rawName);
        if (warp == null) {
            return Texts.err("No warp named " + rawName.trim() + ".");
        }
        faction.warps.remove(warp);
        save();
        return Texts.ok("Deleted warp " + warp.name + ".");
    }

    public synchronized Message warp(PlayerRef player, String rawName, String password) {
        Faction faction = factionOf(player.getUuid());
        if (faction == null) {
            return Texts.err("You are not in a faction.");
        }
        Warp warp = findWarp(faction, rawName);
        if (warp == null) {
            return Texts.err("No warp named " + rawName.trim() + ".");
        }
        if (warp.password != null && !warp.password.isEmpty() && !warp.password.equals(password == null ? "" : password)) {
            return Texts.err("That warp needs the right password.");
        }
        Vector3d start = new Vector3d(player.getTransform().getPosition());
        UUID playerId = player.getUuid();
        String expectedWorld = player.getTransform() == null ? "" : worldContaining(playerId) == null ? "" : worldContaining(playerId).getName();
        Warp copy = warp.copy();
        int seconds = Math.max(0, config.homeWarmupSeconds);
        schedule(playerId, () -> finishWarp(playerId, expectedWorld, start, copy), seconds);
        if (seconds <= 0) {
            return null;
        }
        return Texts.info("Warping to " + warp.name + " in " + seconds + " seconds. Move to cancel.");
    }

    public synchronized Message warps(PlayerRef player) {
        Faction faction = factionOf(player.getUuid());
        if (faction == null) {
            return Texts.err("You are not in a faction.");
        }
        if (faction.warps.isEmpty()) {
            return Texts.info("No warps yet. An officer can set one with /f setwarp <name>.");
        }
        List<String> names = new ArrayList<>();
        for (Warp warp : faction.warps) {
            names.add(warp.password == null || warp.password.isEmpty() ? warp.name : warp.name + " (locked)");
        }
        return Texts.info("Warps: " + String.join(", ", names));
    }

    public synchronized Message stuck(PlayerRef player, World world) {
        long now = System.currentTimeMillis();
        Long last = stuckAt.get(player.getUuid());
        if (last != null && now - last < config.stuckCooldownSeconds * 1000L) {
            return Texts.err("You can use /f stuck again in a moment.");
        }
        Faction here = factionAt(world.getName(), (int) Math.floor(player.getTransform().getPosition().x), (int) Math.floor(player.getTransform().getPosition().z));
        Faction mine = factionOf(player.getUuid());
        if (here == null || isSystem(here) || (mine != null && mine.id.equals(here.id))) {
            return Texts.err("You are not stuck in another faction's land.");
        }
        if (mine != null && mine.home != null) {
            stuckAt.put(player.getUuid(), now);
            Home home = mine.home;
            teleport(player, home.world, home.x, home.y, home.z, home.pitch, home.yaw, home.roll);
            return Texts.ok("Sent you to your faction home.");
        }
        int originX = chunkX(player);
        int originZ = chunkZ(player);
        double y = player.getTransform().getPosition().y;
        Rotation3f rotation = player.getTransform().getRotation();
        for (int radius = 1; radius <= 8; radius++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    if (data.claims.get(claimKey(world.getName(), originX + dx, originZ + dz)) == null) {
                        stuckAt.put(player.getUuid(), now);
                        teleport(player, world.getName(), (originX + dx) * 32.0 + 16.0, y, (originZ + dz) * 32.0 + 16.0, rotation.pitch(), rotation.yaw(), rotation.roll());
                        return Texts.ok("Moved you to nearby wilderness.");
                    }
                }
            }
        }
        return Texts.err("No wilderness is close enough.");
    }

    public synchronized Message adminPermanent(String rawName) {
        Faction faction = findByName(rawName);
        if (faction == null || isSystem(faction)) {
            return Texts.err("No faction named " + rawName.trim() + ".");
        }
        faction.permanent = !faction.permanent;
        save();
        return Texts.ok(faction.permanent ? faction.name + " is permanent." : faction.name + " is no longer permanent.");
    }

    public synchronized Message adminLock(String rawName) {
        Faction faction = findByName(rawName);
        if (faction == null || isSystem(faction)) {
            return Texts.err("No faction named " + rawName.trim() + ".");
        }
        faction.locked = !faction.locked;
        save();
        return Texts.ok(faction.locked ? faction.name + " is locked." : faction.name + " is unlocked.");
    }

    public synchronized Message adminClaimFor(PlayerRef player, World world, String rawName) {
        Faction faction = findByName(rawName);
        if (faction == null || isSystem(faction)) {
            return Texts.err("No faction named " + rawName.trim() + ".");
        }
        int chunkX = chunkX(player);
        int chunkZ = chunkZ(player);
        String key = claimKey(world.getName(), chunkX, chunkZ);
        data.claims.put(key, faction.id);
        assignPlot(key, player, false);
        touchMap(world.getName());
        save();
        return Texts.ok("Claimed chunk " + chunkX + ", " + chunkZ + " for " + faction.name + ".");
    }

    public synchronized Message reloadConfig() {
        try {
            readConfig(plugin.getDataDirectory());
            return Texts.ok("Reloaded the faction config.");
        } catch (IOException exception) {
            plugin.getLogger().atSevere().withCause(exception).log("Failed to reload faction config");
            return Texts.err("Could not reload the faction config.");
        }
    }

    public synchronized Message adminPowerBoost(String rawName, double amount) {
        String id = resolvePlayerId(rawName);
        if (id == null) {
            return Texts.err("No player named " + rawName.trim() + ".");
        }
        if (id.isEmpty()) {
            return Texts.err("More than one player is named " + rawName.trim() + ".");
        }
        Profile profile = ensureProfile(UUID.fromString(id), rawName.trim());
        profile.powerBoost = round(amount);
        save();
        return Texts.ok(profile.name + " has a power boost of " + format(profile.powerBoost) + ".");
    }

    public synchronized Message adminFactionBoost(String rawName, double amount) {
        Faction faction = findByName(rawName);
        if (faction == null || isSystem(faction)) {
            return Texts.err("No faction named " + rawName.trim() + ".");
        }
        faction.powerBoost = round(amount);
        save();
        return Texts.ok(faction.name + " has a power boost of " + format(faction.powerBoost) + ".");
    }

    public synchronized AdminBoard.Snapshot adminBoard(PlayerRef player, String worldName, String selectedId) {
        List<Faction> ordered = new ArrayList<>(data.factions.values());
        ordered.sort(Comparator.comparing((Faction faction) -> isSystem(faction))
                .thenComparing(faction -> faction.name == null ? "" : faction.name, String.CASE_INSENSITIVE_ORDER));
        List<AdminBoard.Row> rows = new ArrayList<>();
        Faction selected = null;
        for (Faction faction : ordered) {
            if (faction == null || faction.id == null) {
                continue;
            }
            int land = used(faction);
            String summary = isSystem(faction) ? land + (land == 1 ? " chunk" : " chunks") : land + " land · " + format(power(faction)) + " power";
            rows.add(new AdminBoard.Row(faction.id, faction.name, faction.color, summary, isSystem(faction)));
            if (faction.id.equals(selectedId)) {
                selected = faction;
            }
        }
        if (selected == null && !ordered.isEmpty()) {
            for (Faction faction : ordered) {
                if (faction != null && faction.id != null && !isSystem(faction)) {
                    selected = faction;
                    break;
                }
            }
            if (selected == null) {
                selected = ordered.get(0);
            }
        }
        return new AdminBoard.Snapshot(bypass.contains(player.getUuid()), hereLine(player, worldName), List.copyOf(rows), detailOf(selected));
    }

    public synchronized String adminManage(PlayerRef player, World world, String id, String action) {
        if (player == null || !player.hasPermission("factions.admin")) {
            return "You need the factions.admin permission.";
        }
        String worldName = world == null ? null : world.getName();
        return switch (action == null ? "" : action) {
            case "bypass" -> {
                adminBypass(player);
                yield bypass.contains(player.getUuid())
                        ? "Claim bypass is on. You can build in claimed land."
                        : "Claim bypass is off.";
            }
            case "reload" -> {
                try {
                    readConfig(plugin.getDataDirectory());
                    yield "Reloaded the faction config.";
                } catch (IOException exception) {
                    plugin.getLogger().atSevere().withCause(exception).log("Failed to reload faction config");
                    yield "Could not reload the faction config.";
                }
            }
            case "unclaim" -> {
                if (world == null) {
                    yield "The world is not available.";
                }
                adminUnclaim(player, world);
                yield hereLine(player, worldName);
            }
            case "safezone" -> {
                if (world == null) {
                    yield "The world is not available.";
                }
                adminZone(player, world, "SAFEZONE");
                yield hereLine(player, worldName);
            }
            case "warzone" -> {
                if (world == null) {
                    yield "The world is not available.";
                }
                adminZone(player, world, "WARZONE");
                yield hereLine(player, worldName);
            }
            case "claim" -> {
                Faction faction = data.factions.get(id);
                if (faction == null || isSystem(faction) || world == null) {
                    yield faction == null ? "Pick a faction first." : "This chunk cannot be claimed for that faction.";
                }
                adminClaimFor(player, world, faction.name);
                yield "Claimed this chunk for " + faction.name + ". " + hereLine(player, worldName);
            }
            case "disband" -> {
                Faction faction = data.factions.get(id);
                if (faction == null || isSystem(faction)) {
                    yield "That faction cannot be disbanded.";
                }
                String name = faction.name;
                removeFaction(faction);
                if (world != null) {
                    world.sendMessage(Texts.err(name + " was disbanded by staff."));
                }
                yield "Disbanded " + name + " and released its claims.";
            }
            case "lock", "permanent", "open", "peaceful", "boostup", "boostdown", "boostclear" -> toggleFaction(id, action);
            default -> "That action is not available.";
        };
    }

    private String toggleFaction(String id, String action) {
        Faction faction = data.factions.get(id);
        if (faction == null || isSystem(faction)) {
            return "Pick a player faction first.";
        }
        return switch (action) {
            case "lock" -> {
                faction.locked = !faction.locked;
                save();
                yield faction.locked ? faction.name + " is locked. Members cannot manage it." : faction.name + " is unlocked.";
            }
            case "permanent" -> {
                faction.permanent = !faction.permanent;
                save();
                yield faction.permanent ? faction.name + " is permanent." : faction.name + " can be disbanded again.";
            }
            case "open" -> {
                faction.open = !faction.open;
                save();
                yield faction.open ? faction.name + " is open. Anyone can join." : faction.name + " is invite only.";
            }
            case "peaceful" -> {
                faction.peaceful = !faction.peaceful;
                if (faction.peaceful) {
                    List<String> enemies = new ArrayList<>();
                    for (Map.Entry<String, String> entry : faction.relations.entrySet()) {
                        if ("ENEMY".equals(entry.getValue())) {
                            enemies.add(entry.getKey());
                        }
                    }
                    for (String enemyId : enemies) {
                        faction.relations.remove(enemyId);
                        Faction other = data.factions.get(enemyId);
                        if (other != null) {
                            other.relations.remove(faction.id);
                        }
                    }
                }
                save();
                yield faction.peaceful ? faction.name + " is peaceful and cannot be overclaimed." : faction.name + " can fight again.";
            }
            case "boostup" -> adjustBoost(faction, 10);
            case "boostdown" -> adjustBoost(faction, -10);
            case "boostclear" -> {
                faction.powerBoost = 0;
                save();
                yield faction.name + " has no power boost.";
            }
            default -> "That action is not available.";
        };
    }

    private String adjustBoost(Faction faction, double delta) {
        faction.powerBoost = round(faction.powerBoost + delta);
        save();
        return faction.name + " power boost is " + format(faction.powerBoost) + ".";
    }

    private AdminBoard.Detail detailOf(Faction faction) {
        if (faction == null || faction.id == null) {
            return null;
        }
        if (isSystem(faction)) {
            int land = used(faction);
            String about = faction.description == null ? "" : faction.description;
            return new AdminBoard.Detail(faction.id, faction.name, faction.color, true,
                    land + (land == 1 ? " chunk." : " chunks."),
                    "Server land\nNot a player faction.",
                    about,
                    List.of(),
                    false, false, true, true,
                    "-", "-", Integer.toString(land), "-", "-", false);
        }
        int land = used(faction);
        double total = power(faction);
        boolean exposed = land > total;
        String stats = (faction.members.size() == 1 ? "1 member" : faction.members.size() + " members")
                + ". Power " + format(total) + " for " + land + (land == 1 ? " chunk." : " chunks.")
                + " Bank " + format(faction.bank) + ". Boost " + format(faction.powerBoost) + ".";
        int allies = 0;
        int truces = 0;
        int enemies = 0;
        for (String relation : faction.relations.values()) {
            if ("ALLY".equals(relation)) {
                allies++;
            } else if ("TRUCE".equals(relation)) {
                truces++;
            } else if ("ENEMY".equals(relation)) {
                enemies++;
            }
        }
        String flags = (faction.open ? "Open" : "Invite only")
                + "   ·   " + (faction.peaceful ? "Peaceful" : "Can fight")
                + "   ·   " + (faction.permanent ? "Permanent" : "Can disband")
                + "   ·   " + (faction.locked ? "Locked" : "Unlocked")
                + (exposed ? "   ·   Land exposed" : "")
                + "\n" + allies + " allies   ·   " + truces + " truces   ·   " + enemies + " enemies";
        String about = faction.description == null ? "" : faction.description.trim();
        List<MemberLine> lines = new ArrayList<>();
        for (Map.Entry<String, Member> entry : faction.members.entrySet()) {
            Member member = entry.getValue();
            if (member == null) {
                continue;
            }
            Profile profile = data.profiles.get(entry.getKey());
            double power = profile == null || profile.power == null ? 0 : profile.power;
            String name = member.name == null || member.name.isBlank() ? "Unknown" : member.name;
            String rank = rankLabel(member.rank);
            if (!rank.isEmpty()) {
                rank = rank.substring(0, 1).toUpperCase(Locale.ROOT) + rank.substring(1);
            }
            lines.add(new MemberLine(rankValue(member.rank), name, rank + "\n" + name + "   ·   power " + format(power)));
        }
        lines.sort(Comparator.comparingInt((MemberLine line) -> line.rank).reversed().thenComparing(line -> line.name, String.CASE_INSENSITIVE_ORDER));
        List<String> members = new ArrayList<>();
        for (MemberLine line : lines) {
            members.add(line.text);
        }
        if (members.isEmpty()) {
            members.add("No members.");
        }
        return new AdminBoard.Detail(faction.id, faction.name, faction.color, false, stats, flags, about, List.copyOf(members),
                faction.open, faction.peaceful, faction.permanent, faction.locked,
                Integer.toString(faction.members.size()), format(total), Integer.toString(land), format(faction.bank), format(faction.powerBoost), exposed);
    }

    private String hereLine(PlayerRef player, String worldName) {
        int chunkX = chunkX(player);
        int chunkZ = chunkZ(player);
        String where = "wilderness";
        if (worldName != null) {
            String ownerId = data.claims.get(claimKey(worldName, chunkX, chunkZ));
            if (ownerId != null) {
                Faction owner = data.factions.get(ownerId);
                where = owner == null || owner.name == null ? ownerId : owner.name;
            }
        }
        return where + "   ·   chunk " + chunkX + ", " + chunkZ;
    }

    private record MemberLine(int rank, String name, String text) {
    }

    private void finishHome(UUID playerId, String expectedWorld, Vector3d start) {
        PlayerRef player = Universe.get().getPlayer(playerId);
        if (player == null) {
            return;
        }
        World current = worldContaining(playerId);
        if (current == null) {
            player.sendMessage(Texts.err("Teleport cancelled."));
            return;
        }
        current.execute(() -> {
            PlayerRef online = Universe.get().getPlayer(playerId);
            if (online == null) {
                return;
            }
            if (!expectedWorld.equals(current.getName()) || online.getTransform().getPosition().distanceSquared(start) > 1.0) {
                online.sendMessage(Texts.err("Teleport cancelled because you moved."));
                return;
            }
            Home home = currentHome(playerId);
            if (home == null) {
                online.sendMessage(Texts.err("Your faction home is no longer set."));
                return;
            }
            World destinationWorld = Universe.get().getWorld(home.world);
            if (destinationWorld == null) {
                online.sendMessage(Texts.err("The home world " + home.world + " is not loaded."));
                return;
            }
            Ref<EntityStore> ref = online.getReference();
            if (ref == null) {
                online.sendMessage(Texts.err("Teleport cancelled."));
                return;
            }
            Rotation3f rotation = new Rotation3f();
            rotation.setPitch(home.pitch);
            rotation.setYaw(home.yaw);
            rotation.setRoll(home.roll);
            @SuppressWarnings("unchecked")
            Store<EntityStore> store = ref.getStore();
            store.addComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(destinationWorld, new Vector3d(home.x, home.y, home.z), rotation));
            online.sendMessage(Texts.ok("Welcome home."));
        });
    }

    private synchronized Home currentHome(UUID playerId) {
        Faction faction = factionOf(playerId);
        if (faction == null || faction.home == null) {
            return null;
        }
        return faction.home.copy();
    }

    private void schedule(UUID playerId, Runnable task, int seconds) {
        ScheduledFuture<?> previous = pendingHomes.remove(playerId);
        if (previous != null) {
            previous.cancel(false);
        }
        if (seconds <= 0) {
            task.run();
            return;
        }
        pendingHomes.put(playerId, scheduler.schedule(() -> {
            pendingHomes.remove(playerId);
            task.run();
        }, seconds, TimeUnit.SECONDS));
    }

    private World worldContaining(UUID playerId) {
        for (World world : Universe.get().getWorlds().values()) {
            for (PlayerRef player : world.getPlayerRefs()) {
                if (playerId.equals(player.getUuid())) {
                    return world;
                }
            }
        }
        return null;
    }

    private void removeFaction(Faction faction) {
        data.factions.remove(faction.id);
        data.claims.entrySet().removeIf(entry -> {
            if (!faction.id.equals(entry.getValue())) {
                return false;
            }
            data.plots.remove(entry.getKey());
            touchMap(worldOfClaim(entry.getKey()));
            return true;
        });
        data.invites.entrySet().removeIf(entry -> faction.id.equals(entry.getValue().factionId));
        for (String memberId : faction.members.keySet()) {
            clearChat(memberId);
        }
        for (Faction other : data.factions.values()) {
            other.relations.remove(faction.id);
            other.allyRequests.remove(faction.id);
            other.truceRequests.remove(faction.id);
        }
        save();
    }

    private void link(Faction left, Faction right, String relation) {
        left.relations.put(right.id, relation);
        right.relations.put(left.id, relation);
        left.allyRequests.remove(right.id);
        right.allyRequests.remove(left.id);
        left.truceRequests.remove(right.id);
        right.truceRequests.remove(left.id);
    }

    private boolean isLeader(Faction faction, UUID playerId) {
        Member member = faction.members.get(playerId.toString());
        return member != null && "LEADER".equals(member.rank);
    }

    private boolean isOfficer(Faction faction, UUID playerId) {
        if (faction == null) {
            return false;
        }
        Member member = faction.members.get(playerId.toString());
        return member != null && ("LEADER".equals(member.rank) || "OFFICER".equals(member.rank) || "COLEADER".equals(member.rank));
    }

    private boolean isColeader(Faction faction, UUID playerId) {
        if (faction == null) {
            return false;
        }
        Member member = faction.members.get(playerId.toString());
        return member != null && ("LEADER".equals(member.rank) || "COLEADER".equals(member.rank));
    }

    private Faction factionOf(UUID playerId) {
        String key = playerId.toString();
        for (Faction faction : data.factions.values()) {
            if (faction.members.containsKey(key)) {
                return faction;
            }
        }
        return null;
    }

    private Faction findByName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        String id = rawName.trim().toLowerCase(Locale.ROOT);
        Faction direct = data.factions.get(id);
        if (direct != null) {
            return direct;
        }
        for (Faction faction : data.factions.values()) {
            if (faction.name.equalsIgnoreCase(rawName.trim())) {
                return faction;
            }
        }
        return null;
    }

    private List<PlayerRef> online(Faction faction) {
        List<PlayerRef> players = new ArrayList<>();
        for (String memberId : faction.members.keySet()) {
            UUID memberUuid;
            try {
                memberUuid = UUID.fromString(memberId);
            } catch (IllegalArgumentException exception) {
                continue;
            }
            PlayerRef player = Universe.get().getPlayer(memberUuid);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    private void tell(Faction faction, Message message) {
        tell(faction, message, null);
    }

    private void tell(Faction faction, Message message, UUID except) {
        for (PlayerRef player : online(faction)) {
            if (except != null && except.equals(player.getUuid())) {
                continue;
            }
            player.sendMessage(message);
        }
    }

    private Found findMember(Faction faction, String rawName) {
        String needle = rawName == null ? "" : rawName.trim();
        Found match = null;
        for (Map.Entry<String, Member> entry : faction.members.entrySet()) {
            Member member = entry.getValue();
            if (member.name == null || !member.name.equalsIgnoreCase(needle)) {
                continue;
            }
            if (match != null) {
                return new Found(null, null, Texts.err("More than one member is named " + needle + "."));
            }
            match = new Found(entry.getKey(), member, null);
        }
        if (match == null) {
            return new Found(null, null, Texts.err("Nobody in your faction is named " + needle + "."));
        }
        return match;
    }

    private int used(Faction faction) {
        int count = 0;
        for (String owner : data.claims.values()) {
            if (faction.id.equals(owner)) {
                count++;
            }
        }
        return count;
    }

    private String tryClaim(PlayerRef player, String world, int chunkX, int chunkZ) {
        Faction faction = factionOf(player.getUuid());
        if (!isOfficer(faction, player.getUuid())) {
            return "Only a leader or officer can claim land.";
        }
        if (faction.locked) {
            return faction.name + " is locked.";
        }
        ensureProfile(player.getUuid(), player.getUsername());
        String key = claimKey(world, chunkX, chunkZ);
        String existing = data.claims.get(key);
        if (faction.id.equals(existing)) {
            return "";
        }
        if (faction.peaceful && existing != null) {
            return "Peaceful factions cannot take land.";
        }
        if (existing != null) {
            Faction other = data.factions.get(existing);
            if (other != null && (isSystem(other) || other.permanent)) {
                return other.name + " cannot be overclaimed.";
            }
            if (other != null && other.peaceful) {
                return other.name + " is peaceful and cannot be overclaimed.";
            }
            if (other != null && config.protectOfflineFactions && !isOnline(other)) {
                return other.name + " is offline and shielded.";
            }
            if (other != null) {
                String relation = relationTo(faction, other);
                if ("ALLY".equals(relation) || "TRUCE".equals(relation)) {
                    return "You cannot take land from " + other.name + ".";
                }
                if (power(other) + 0.001 >= used(other)) {
                    return "This chunk belongs to " + other.name + ", and they still have enough power.";
                }
            }
        }
        if (!faction.permanent && power(faction) + 0.001 < used(faction) + 1) {
            return "Your faction needs more power. Power " + format(power(faction)) + ", land " + used(faction) + ".";
        }
        String paid = charge(player);
        if (paid != null) {
            return paid;
        }
        data.claims.put(key, faction.id);
        assignPlot(key, player, true);
        touchMap(world);
        return null;
    }

    private Message claimShape(PlayerRef player, World world, int line, int radius, boolean circle) {
        Faction faction = factionOf(player.getUuid());
        if (!isOfficer(faction, player.getUuid())) {
            return Texts.err("Only a leader or officer can claim land.");
        }
        int originX = chunkX(player);
        int originZ = chunkZ(player);
        int claimed = 0;
        String failure = null;
        if (line > 0) {
            Transform transform = player.getTransform();
            Rotation3f rotation = transform.getRotation();
            Vector3d direction = transform.getDirection(rotation.pitch(), rotation.yaw());
            int stepX = 0;
            int stepZ = 0;
            if (Math.abs(direction.x) >= Math.abs(direction.z) && Math.abs(direction.x) > 0.001) {
                stepX = direction.x >= 0 ? 1 : -1;
            } else if (Math.abs(direction.z) > 0.001) {
                stepZ = direction.z >= 0 ? 1 : -1;
            } else {
                stepZ = 1;
            }
            for (int step = 0; step < line && failure == null; step++) {
                String result = tryClaim(player, world.getName(), originX + stepX * step, originZ + stepZ * step);
                if (result == null) {
                    claimed++;
                } else if (!result.isEmpty()) {
                    failure = result;
                }
            }
        } else {
            for (int dz = -radius; dz <= radius && failure == null; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (circle && dx * dx + dz * dz > radius * radius) {
                        continue;
                    }
                    String result = tryClaim(player, world.getName(), originX + dx, originZ + dz);
                    if (result == null) {
                        claimed++;
                    } else if (!result.isEmpty()) {
                        failure = result;
                        break;
                    }
                }
            }
        }
        if (claimed > 0) {
            save();
        }
        if (claimed == 0 && failure == null) {
            return Texts.info("Those chunks are already yours.");
        }
        String summary = "Claimed " + claimed + " chunks. Land " + used(faction) + ", power " + format(power(faction)) + ".";
        if (failure != null) {
            return Texts.err(summary + " " + failure);
        }
        return Texts.ok(summary);
    }

    private String charge(PlayerRef player) {
        if (config.claimCost <= 0) {
            return null;
        }
        Profile profile = ensureProfile(player.getUuid(), player.getUsername());
        if (profile.balance + 0.001 < config.claimCost) {
            return "Claiming costs " + format(config.claimCost) + ". You have " + format(profile.balance) + ".";
        }
        profile.balance = round(profile.balance - config.claimCost);
        return null;
    }

    private Faction factionAt(String world, int blockX, int blockZ) {
        String ownerId = data.claims.get(claimKey(world, ChunkUtil.chunkCoordinate(blockX), ChunkUtil.chunkCoordinate(blockZ)));
        return ownerId == null ? null : data.factions.get(ownerId);
    }

    private String relationTo(Faction mine, Faction other) {
        if (mine == null || other == null) {
            return "NEUTRAL";
        }
        if (mine.id.equals(other.id)) {
            return "MEMBER";
        }
        String relation = mine.relations.get(other.id);
        return relation == null ? "NEUTRAL" : relation;
    }

    private static String actionFor(String useBlockId) {
        if (useBlockId == null) {
            return "BUILD";
        }
        String id = useBlockId.toLowerCase(Locale.ROOT);
        if (id.contains("door") || id.contains("gate") || id.contains("hatch") || id.contains("trapdoor")) {
            return "DOOR";
        }
        if (id.contains("button") || id.contains("lever") || id.contains("plate") || id.contains("switch")) {
            return "BUTTON";
        }
        return "CONTAINER";
    }

    private boolean permitted(String relation, String action) {
        if ("MEMBER".equals(relation)) {
            return true;
        }
        return switch (relation) {
            case "ALLY" -> switch (action) {
                case "BUILD" -> config.allyBuild;
                case "CONTAINER" -> config.allyContainer;
                case "DOOR" -> config.allyDoor;
                case "BUTTON" -> config.allyButton;
                default -> false;
            };
            case "TRUCE" -> switch (action) {
                case "BUILD" -> config.truceBuild;
                case "CONTAINER" -> config.truceContainer;
                case "DOOR" -> config.truceDoor;
                case "BUTTON" -> config.truceButton;
                default -> false;
            };
            case "NEUTRAL" -> switch (action) {
                case "BUILD" -> config.neutralBuild;
                case "CONTAINER" -> config.neutralContainer;
                case "DOOR" -> config.neutralDoor;
                case "BUTTON" -> config.neutralButton;
                default -> false;
            };
            case "ENEMY" -> switch (action) {
                case "BUILD" -> config.enemyBuild;
                case "CONTAINER" -> config.enemyContainer;
                case "DOOR" -> config.enemyDoor;
                case "BUTTON" -> config.enemyButton;
                default -> false;
            };
            default -> false;
        };
    }

    private boolean raidOpen() {
        int start = config.raidStartHour;
        int end = config.raidEndHour;
        if (start < 0 || end < 0) {
            return true;
        }
        int hour = LocalTime.now().getHour();
        if (start == end) {
            return false;
        }
        if (start < end) {
            return hour >= start && hour < end;
        }
        return hour >= start || hour < end;
    }

    private static boolean isSystem(Faction faction) {
        return faction != null && faction.kind != null && !faction.kind.isBlank();
    }

    private double power(Faction faction) {
        if (isSystem(faction)) {
            return 1_000_000;
        }
        double total = faction.powerBoost;
        for (String id : faction.members.keySet()) {
            Profile profile = data.profiles.get(id);
            total += profile == null || profile.power == null ? config.maxPower : profile.power;
            if (profile != null) {
                total += profile.powerBoost;
            }
        }
        return total;
    }

    private Faction ensureZone(String kind) {
        boolean safe = "SAFEZONE".equals(kind);
        String id = safe ? "safezone" : "warzone";
        Faction faction = data.factions.get(id);
        if (faction == null) {
            faction = new Faction();
            faction.id = id;
            faction.members = new LinkedHashMap<>();
            faction.relations = new LinkedHashMap<>();
            faction.allyRequests = new ArrayList<>();
            faction.truceRequests = new ArrayList<>();
            data.factions.put(id, faction);
        }
        faction.kind = safe ? "SAFEZONE" : "WARZONE";
        faction.name = safe ? "Safezone" : "Warzone";
        faction.color = safe ? "#55FFFF" : "#FF5555";
        faction.description = safe ? "Protected land. No building and no fighting." : "A fight pit. No building.";
        return faction;
    }

    private Profile ensureProfile(UUID playerId, String name) {
        Profile profile = data.profiles.computeIfAbsent(playerId.toString(), key -> new Profile());
        if (profile.power == null) {
            profile.power = config.maxPower;
        }
        if (profile.balance == null) {
            profile.balance = config.startingBalance;
        }
        if (profile.chatMode == null || profile.chatMode.isBlank()) {
            profile.chatMode = "PUBLIC";
        }
        if (profile.lastPowerTick == 0) {
            profile.lastPowerTick = System.currentTimeMillis();
        }
        if (name != null && !name.isBlank()) {
            profile.name = name;
        }
        return profile;
    }

    private boolean ensureMemberProfiles() {
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (Faction faction : data.factions.values()) {
            if (isSystem(faction)) {
                continue;
            }
            for (Map.Entry<String, Member> entry : faction.members.entrySet()) {
                Profile existing = data.profiles.get(entry.getKey());
                boolean missing = existing == null || existing.power == null || existing.balance == null || existing.lastSeen == 0 || existing.lastPowerTick == 0 || existing.chatMode == null;
                Profile profile = ensureProfile(UUID.fromString(entry.getKey()), entry.getValue().name);
                if (profile.lastSeen == 0) {
                    profile.lastSeen = now;
                    missing = true;
                }
                if (missing) {
                    changed = true;
                }
            }
        }
        return changed;
    }

    private void tickPowerSafe() {
        try {
            tickPower();
        } catch (RuntimeException exception) {
            plugin.getLogger().atSevere().withCause(exception).log("Power tick failed");
        }
    }

    private synchronized void tickPower() {
        if (!loaded) {
            return;
        }
        long now = System.currentTimeMillis();
        long grace = TimeUnit.HOURS.toMillis(Math.max(0, config.offlineDecayGraceHours));
        boolean changed = false;
        for (Profile profile : data.profiles.values()) {
            if (profile.power == null) {
                profile.power = config.maxPower;
                changed = true;
            }
            boolean online = profile.lastSeen > 0 && now - profile.lastSeen <= 90_000L;
            if (online) {
                profile.lastPowerTick = now;
                if (config.powerRegenPerMinute > 0 && profile.power + 0.001 < config.maxPower) {
                    profile.power = round(Math.min(config.maxPower, profile.power + config.powerRegenPerMinute));
                    changed = true;
                }
                continue;
            }
            if (profile.lastSeen <= 0 || now - profile.lastSeen <= grace || config.offlineDecayPerHour <= 0) {
                continue;
            }
            long decayFrom = Math.max(profile.lastPowerTick, profile.lastSeen + grace);
            double hours = (now - decayFrom) / 3_600_000.0;
            if (hours <= 0) {
                continue;
            }
            double next = round(Math.max(config.minPower, profile.power - hours * config.offlineDecayPerHour));
            profile.lastPowerTick = now;
            if (next != profile.power) {
                profile.power = next;
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private void clearChat(String memberId) {
        data.factionChat.remove(memberId);
        Profile profile = data.profiles.get(memberId);
        if (profile != null) {
            profile.chatMode = "PUBLIC";
            profile.autoClaim = false;
        }
    }

    private String resolvePlayerId(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        String needle = rawName.trim();
        for (PlayerRef online : Universe.get().getPlayers()) {
            if (online.getUsername().equalsIgnoreCase(needle)) {
                return online.getUuid().toString();
            }
        }
        String found = null;
        for (Map.Entry<String, Profile> entry : data.profiles.entrySet()) {
            if (entry.getValue().name != null && entry.getValue().name.equalsIgnoreCase(needle)) {
                if (found != null && !found.equals(entry.getKey())) {
                    return "";
                }
                found = entry.getKey();
            }
        }
        for (Faction faction : data.factions.values()) {
            for (Map.Entry<String, Member> entry : faction.members.entrySet()) {
                if (entry.getValue().name != null && entry.getValue().name.equalsIgnoreCase(needle)) {
                    if (found != null && !found.equals(entry.getKey())) {
                        return "";
                    }
                    found = entry.getKey();
                }
            }
        }
        return found;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String format(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.001) {
            return Long.toString(Math.round(rounded));
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    private String namesWithRank(Faction faction, String rank) {
        List<String> names = new ArrayList<>();
        for (Member member : faction.members.values()) {
            if (rank.equals(member.rank)) {
                names.add(member.name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(", ", names);
    }

    private String relationNames(Faction faction, String relation) {
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, String> entry : faction.relations.entrySet()) {
            if (!relation.equals(entry.getValue())) {
                continue;
            }
            Faction other = data.factions.get(entry.getKey());
            names.add(other == null ? entry.getKey() : other.name);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(", ", names);
    }

    private String requestNames(List<String> requests) {
        List<String> names = new ArrayList<>();
        for (String requesterId : requests) {
            Faction requester = data.factions.get(requesterId);
            names.add(requester == null ? requesterId : requester.name);
        }
        return String.join(", ", names);
    }

    private static Message factionLine(String factionName, String color, String username, String message) {
        return Message.join(
                Message.raw("[" + factionName + "] ").color(color),
                Message.raw(username).color("#FFFFFF"),
                Message.raw(": " + message).color("#DDDDDD"));
    }

    private static Message allyLine(String factionName, String color, String username, String message) {
        return Message.join(
                Message.raw("[Ally] ").color("#55FF55"),
                Message.raw("[" + factionName + "] ").color(color),
                Message.raw(username).color("#FFFFFF"),
                Message.raw(": " + message).color("#DDDDDD"));
    }

    private static Message publicLine(String factionName, String color, String username, String message) {
        return Message.join(
                Message.raw("[" + factionName + "] ").color(color),
                Message.raw(username).color("#FFFFFF"),
                Message.raw(": " + message).color("#DDDDDD"));
    }

    private static Message truceLine(String factionName, String color, String username, String message) {
        return Message.join(
                Message.raw("[Truce] ").color("#FFFF55"),
                Message.raw("[" + factionName + "] ").color(color),
                Message.raw(username).color("#FFFFFF"),
                Message.raw(": " + message).color("#DDDDDD"));
    }

    private synchronized String relationColor(Faction speaker, PlayerRef viewer) {
        Faction theirs = viewer == null ? null : factionOf(viewer.getUuid());
        return switch (relationTo(theirs, speaker)) {
            case "MEMBER" -> "#55FF55";
            case "ALLY" -> "#00AA00";
            case "TRUCE" -> "#FFFF55";
            case "ENEMY" -> "#FF5555";
            default -> "#FFFFFF";
        };
    }

    private static String chatTag(Faction faction) {
        if (faction.tag != null && !faction.tag.isBlank()) {
            return faction.tag;
        }
        return faction.name;
    }

    private static String statusSuffix(Faction faction) {
        String suffix = "";
        if (faction.permanent) {
            suffix += " permanent";
        }
        if (faction.peaceful) {
            suffix += " peaceful";
        }
        if (faction.locked) {
            suffix += " locked";
        }
        if (faction.powerBoost != 0) {
            suffix += " boost " + format(faction.powerBoost);
        }
        return suffix;
    }

    private static String rankLabel(String rank) {
        return switch (rank) {
            case "COLEADER" -> "co-leader";
            case "OFFICER" -> "officer";
            case "MEMBER" -> "member";
            case "RECRUIT" -> "recruit";
            case "LEADER" -> "leader";
            default -> rank.toLowerCase(Locale.ROOT);
        };
    }

    private static int rankValue(String rank) {
        return switch (rank) {
            case "LEADER" -> 4;
            case "COLEADER" -> 3;
            case "OFFICER" -> 2;
            case "MEMBER" -> 1;
            default -> 0;
        };
    }

    private Message ensureUnlocked(Faction faction) {
        if (faction != null && faction.locked) {
            return Texts.err(faction.name + " is locked.");
        }
        return null;
    }

    private boolean isBanned(Faction faction, UUID playerId) {
        return faction.bans != null && faction.bans.contains(playerId.toString());
    }

    private boolean canEnter(PlayerRef player, Faction owner) {
        if (owner == null || bypass.contains(player.getUuid()) || isSystem(owner)) {
            return true;
        }
        Faction mine = factionOf(player.getUuid());
        String relation = relationTo(mine, owner);
        return switch (relation) {
            case "MEMBER" -> true;
            case "ALLY" -> config.allyEnter;
            case "TRUCE" -> config.truceEnter;
            case "ENEMY" -> config.enemyEnter && (owner.enemyEnter == null || owner.enemyEnter);
            default -> config.neutralEnter;
        };
    }

    private boolean isOnline(Faction faction) {
        if (faction == null) {
            return false;
        }
        for (String id : faction.members.keySet()) {
            try {
                if (Universe.get().getPlayer(UUID.fromString(id)) != null) {
                    return true;
                }
            } catch (IllegalArgumentException exception) {
                // Skip a bad member id.
            }
        }
        return false;
    }

    private void refund(PlayerRef player, int chunks) {
        if (!config.unclaimRefund || config.claimCost <= 0 || chunks <= 0) {
            return;
        }
        Profile profile = ensureProfile(player.getUuid(), player.getUsername());
        profile.balance = round(profile.balance + chunks * config.claimCost);
    }

    private void assignPlot(String key, PlayerRef player, boolean paid) {
        Plot plot = new Plot();
        plot.ownerId = player.getUuid().toString();
        plot.ownerName = player.getUsername();
        plot.paid = paid;
        plot.access = new ArrayList<>();
        plot.accessNames = new ArrayList<>();
        data.plots.put(key, plot);
    }

    private Warp findWarp(Faction faction, String rawName) {
        if (rawName == null || faction.warps == null) {
            return null;
        }
        for (Warp warp : faction.warps) {
            if (warp.name != null && warp.name.equalsIgnoreCase(rawName.trim())) {
                return warp;
            }
        }
        return null;
    }

    private void applyTag(CommandBuffer<EntityStore> buffer, PlayerRef player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Faction faction = factionOf(player.getUuid());
        if (faction == null || isSystem(faction)) {
            buffer.tryRemoveComponent(ref, PersistentDisplayName.getComponentType());
            return;
        }
        Message name = Message.join(Message.raw("[" + chatTag(faction) + "] ").color(faction.color), Message.raw(player.getUsername()));
        buffer.putComponent(ref, PersistentDisplayName.getComponentType(), new PersistentDisplayName(name));
    }

    private void teleport(CommandBuffer<EntityStore> buffer, PlayerRef player, String worldName, double x, double y, double z, float pitch, float yaw, float roll) {
        World destination = Universe.get().getWorld(worldName);
        Ref<EntityStore> ref = player.getReference();
        if (destination == null || ref == null || !ref.isValid()) {
            player.sendMessage(Texts.err("Teleport cancelled."));
            return;
        }
        Rotation3f rotation = new Rotation3f();
        rotation.setPitch(pitch);
        rotation.setYaw(yaw);
        rotation.setRoll(roll);
        buffer.addComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(destination, new Vector3d(x, y, z), rotation));
    }

    private void teleport(PlayerRef player, String worldName, double x, double y, double z, float pitch, float yaw, float roll) {
        World destination = Universe.get().getWorld(worldName);
        if (destination == null || player.getReference() == null) {
            player.sendMessage(Texts.err("Teleport cancelled."));
            return;
        }
        Rotation3f rotation = new Rotation3f();
        rotation.setPitch(pitch);
        rotation.setYaw(yaw);
        rotation.setRoll(roll);
        @SuppressWarnings("unchecked")
        Store<EntityStore> store = player.getReference().getStore();
        store.addComponent(player.getReference(), Teleport.getComponentType(), Teleport.createForPlayer(destination, new Vector3d(x, y, z), rotation));
    }

    private void finishWarp(UUID playerId, String expectedWorld, Vector3d start, Warp warp) {
        PlayerRef player = Universe.get().getPlayer(playerId);
        if (player == null) {
            return;
        }
        World current = worldContaining(playerId);
        if (current == null) {
            player.sendMessage(Texts.err("Warp cancelled."));
            return;
        }
        current.execute(() -> {
            PlayerRef online = Universe.get().getPlayer(playerId);
            if (online == null) {
                return;
            }
            if (!expectedWorld.equals(current.getName()) || online.getTransform().getPosition().distanceSquared(start) > 1.0) {
                online.sendMessage(Texts.err("Warp cancelled because you moved."));
                return;
            }
            teleport(online, warp.world, warp.x, warp.y, warp.z, warp.pitch, warp.yaw, warp.roll);
            online.sendMessage(Texts.ok("Warped to " + warp.name + "."));
        });
    }

    private void readConfig(Path directory) throws IOException {
        Path configPath = directory.resolve("config.json");
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                JsonObject file = GSON.fromJson(reader, JsonObject.class);
                if (file != null) {
                    JsonObject defaults = GSON.toJsonTree(Config.defaults()).getAsJsonObject();
                    boolean added = false;
                    for (Map.Entry<String, JsonElement> entry : defaults.entrySet()) {
                        if (!file.has(entry.getKey())) {
                            file.add(entry.getKey(), entry.getValue());
                            added = true;
                        }
                    }
                    Config read = GSON.fromJson(file, Config.class);
                    if (read != null) {
                        config = read;
                    }
                    config.applyDefaults();
                    if (added) {
                        writeJson(configPath, config);
                    }
                }
            }
            return;
        }
        config.applyDefaults();
        writeJson(configPath, config);
    }

    private static int chunkX(PlayerRef player) {
        return ChunkUtil.chunkCoordinate(player.getTransform().getPosition().x);
    }

    private static int chunkZ(PlayerRef player) {
        return ChunkUtil.chunkCoordinate(player.getTransform().getPosition().z);
    }

    private static String claimKey(String world, int chunkX, int chunkZ) {
        return world + "\u001f" + chunkX + "\u001f" + chunkZ;
    }

    private String ownerAt(String territoryKey) {
        int zCut = territoryKey.lastIndexOf(':');
        int xCut = zCut < 0 ? -1 : territoryKey.lastIndexOf(':', zCut - 1);
        if (xCut <= 0 || zCut <= xCut) {
            return null;
        }
        try {
            String world = territoryKey.substring(0, xCut);
            int chunkX = Integer.parseInt(territoryKey.substring(xCut + 1, zCut));
            int chunkZ = Integer.parseInt(territoryKey.substring(zCut + 1));
            return data.claims.get(claimKey(world, chunkX, chunkZ));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private synchronized void save() {
        if (!loaded) {
            return;
        }
        try {
            Path directory = plugin.getDataDirectory();
            Files.createDirectories(directory);
            writeJson(directory.resolve("factions.json"), data);
        } catch (IOException exception) {
            plugin.getLogger().atSevere().withCause(exception).log("Failed to save faction data");
        }
    }

    private static void writeJson(Path path, Object value) throws IOException {
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            GSON.toJson(value, writer);
        }
        Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    private static final class Found {
        final String id;
        final Member member;
        final Message error;

        Found(String id, Member member, Message error) {
            this.id = id;
            this.member = member;
            this.error = error;
        }
    }
}
