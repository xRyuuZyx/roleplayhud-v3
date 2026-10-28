package id.roleplay.hud;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class HudManager {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final RoleplayHUD plugin;
    private final NexoBridge bridge;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Set<UUID> disabled = new HashSet<>();
    private NexoHud nexoHud;
    private boolean off;
    private BukkitTask task;

    public HudManager(RoleplayHUD plugin) {
        this.plugin = plugin;
        this.bridge = new NexoBridge(plugin);
        decideMode();
        start();
    }

    private void decideMode() {
        nexoHud = null;
        off = false;
        String mode = plugin.getConfig().getString("hud.mode", "nexo");
        if (mode.equalsIgnoreCase("off") || Bukkit.getPluginManager().getPlugin("UIStudio") != null) {
            off = true;
            plugin.getLogger().info("HUD RoleplayHUD dimatikan (mode off / UIStudio terpasang). Sistem gang tetap aktif.");
            return;
        }
        if (mode.equalsIgnoreCase("nexo")) {
            if (bridge.available()) {
                nexoHud = new NexoHud(plugin, this, bridge);
                plugin.getLogger().info("Mode HUD: NEXO (glyph + boss bar + action bar)");
            } else {
                plugin.getLogger().warning("Mode 'nexo' butuh plugin Nexo dan PlaceholderAPI. Memakai mode scoreboard.");
            }
        }
    }

    private void start() {
        if (off) return;
        long interval = Math.max(5L, plugin.getConfig().getLong("update-interval-ticks", 20));
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) update(p);
        }, 20L, interval);
    }

    public void reload() {
        if (task != null) task.cancel();
        resetAll();
        bridge.clearCache();
        decideMode();
        start();
    }

    public void shutdown() {
        if (task != null) task.cancel();
        resetAll();
    }

    private void resetAll() {
        boards.clear();
        if (nexoHud != null) nexoHud.hideAll();
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) p.setScoreboard(main);
    }

    public void remove(UUID id) {
        boards.remove(id);
        if (nexoHud != null) nexoHud.forget(id);
    }

    /** @return true kalau HUD sekarang aktif */
    public boolean toggle(Player p) {
        if (off) return false;
        UUID id = p.getUniqueId();
        if (disabled.remove(id)) return true;
        disabled.add(id);
        boards.remove(id);
        if (nexoHud != null) nexoHud.hide(p);
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        return false;
    }

    private static String entry(int i) { return "§" + Integer.toHexString(i) + "§r"; }

    public void update(Player p) {
        if (off) return;
        if (disabled.contains(p.getUniqueId())) return;
        if (nexoHud != null) { nexoHud.update(p); return; }
        updateScoreboard(p);
    }

    private void updateScoreboard(Player p) {
        UUID id = p.getUniqueId();
        List<String> lines = plugin.getConfig().getStringList("hud.lines");
        if (lines.size() > 15) lines = lines.subList(0, 15);
        Component title = MM.deserialize(apply(plugin.getConfig().getString("hud.title", "HUD"), p));

        Scoreboard sb = boards.get(id);
        if (sb == null) {
            sb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective o = sb.registerNewObjective("hud", Criteria.DUMMY, title);
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            o.numberFormat(NumberFormat.blank());
            boards.put(id, sb);
        }
        if (p.getScoreboard() != sb) p.setScoreboard(sb);

        Objective obj = sb.getObjective("hud");
        if (obj == null) return;
        obj.displayName(title);

        for (int i = 0; i < lines.size(); i++) {
            String e = entry(i);
            Team t = sb.getTeam("l" + i);
            if (t == null) {
                t = sb.registerNewTeam("l" + i);
                t.addEntry(e);
            }
            t.prefix(MM.deserialize(apply(lines.get(i), p)));
            obj.getScore(e).setScore(lines.size() - i);
        }
    }

    /** Ambil nama job dari plugin RoleplayJobs (kalau terpasang). */
    private String job(Player p) {
        try {
            Plugin rj = Bukkit.getPluginManager().getPlugin("RoleplayJobs");
            if (rj == null || !rj.isEnabled()) return "-";
            Object jm = rj.getClass().getMethod("jobs").invoke(rj);
            Object job = jm.getClass().getMethod("getJob", UUID.class).invoke(jm, p.getUniqueId());
            if (job == null) return "Pengangguran";
            return String.valueOf(job.getClass().getMethod("display").invoke(job));
        } catch (Exception e) {
            return "-";
        }
    }

    public String apply(String text, Player p) {
        Gang g = plugin.gangs().byPlayer(p.getUniqueId());
        String tz = plugin.getConfig().getString("timezone", "Asia/Makassar");
        String time;
        try {
            time = ZonedDateTime.now(ZoneId.of(tz)).format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception ex) {
            time = "--:--";
        }
        double bal = plugin.economy().getBalance(p);
        AttributeInstance armor = p.getAttribute(Attribute.ARMOR);
        return text
                .replace("%player%", MM.escapeTags(p.getName()))
                .replace("%money%", String.format(Locale.US, "%,.0f", bal))
                .replace("%job%", job(p))
                .replace("%gang%", g == null ? "-" : MM.escapeTags(g.name))
                .replace("%tipe%", g == null ? "Gang/Keluarga" : g.type.display())
                .replace("%role%", g == null ? "-" : (g.leader.equals(p.getUniqueId()) ? "Pemimpin" : "Anggota"))
                .replace("%members%", g == null ? "-" : String.valueOf(g.members.size()))
                .replace("%health%", String.valueOf((int) Math.ceil(p.getHealth())))
                .replace("%armor%", String.valueOf(armor == null ? 0 : (int) armor.getValue()))
                .replace("%food%", String.valueOf(p.getFoodLevel()))
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%ping%", String.valueOf(p.getPing()))
                .replace("%time%", time);
    }
}
