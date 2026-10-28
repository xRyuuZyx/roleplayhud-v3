package id.roleplay.hud;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class GangManager {
    public record Invite(String gang, long expiresMs) {}

    private final RoleplayHUD plugin;
    private final File file;
    private final Map<String, Gang> gangs = new LinkedHashMap<>();
    private final Map<UUID, String> index = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();

    public GangManager(RoleplayHUD plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gangs.yml");
        load();
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("gangs");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            Gang.Type type;
            try { type = Gang.Type.valueOf(s.getString("type", "GANG")); }
            catch (IllegalArgumentException e) { type = Gang.Type.GANG; }
            Gang g = new Gang(s.getString("name", key), type, UUID.fromString(s.getString("leader")));
            g.bank = s.getDouble("bank");
            g.members.clear();
            for (String m : s.getStringList("members")) g.members.add(UUID.fromString(m));
            if (g.members.isEmpty()) g.members.add(g.leader);
            register(g);
        }
    }

    private void register(Gang g) {
        gangs.put(g.name.toLowerCase(), g);
        for (UUID id : g.members) index.put(id, g.name.toLowerCase());
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Gang g : gangs.values()) {
            String p = "gangs." + g.name.toLowerCase();
            y.set(p + ".name", g.name);
            y.set(p + ".type", g.type.name());
            y.set(p + ".leader", g.leader.toString());
            y.set(p + ".bank", g.bank);
            y.set(p + ".members", g.members.stream().map(UUID::toString).toList());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Gagal menyimpan gangs.yml: " + e.getMessage());
        }
    }

    public Gang byPlayer(UUID id) {
        String key = index.get(id);
        return key == null ? null : gangs.get(key);
    }

    public Gang byName(String name) { return gangs.get(name.toLowerCase()); }

    public Gang create(String name, Gang.Type type, UUID leader) {
        Gang g = new Gang(name, type, leader);
        register(g);
        save();
        return g;
    }

    public void add(Gang g, UUID id) {
        g.members.add(id);
        index.put(id, g.name.toLowerCase());
        save();
    }

    public void remove(Gang g, UUID id) {
        g.members.remove(id);
        index.remove(id);
        save();
    }

    /** Ganti nama gang (anggota, kas, dan pemimpin dipindahkan ke gang baru). */
    public Gang rename(Gang g, String newName) {
        Gang ng = new Gang(newName, g.type, g.leader);
        ng.members.clear();
        ng.members.addAll(g.members);
        ng.bank = g.bank;
        for (UUID id : new ArrayList<>(g.members)) index.remove(id);
        gangs.remove(g.name.toLowerCase());
        register(ng);
        save();
        return ng;
    }

    public void disband(Gang g) {
        for (UUID id : new ArrayList<>(g.members)) index.remove(id);
        gangs.remove(g.name.toLowerCase());
        save();
    }

    public void invite(UUID target, Gang g, long expireSeconds) {
        invites.put(target, new Invite(g.name, System.currentTimeMillis() + expireSeconds * 1000));
    }

    public Invite takeInvite(UUID target) { return invites.remove(target); }

    public void broadcast(Gang g, String miniMessage) {
        for (UUID id : g.members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.send(p, miniMessage);
        }
    }
}
