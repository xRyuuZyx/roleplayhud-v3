package id.roleplay.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.regex.Pattern;

public class Commands implements CommandExecutor, TabCompleter {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_ ]{3,24}$");
    private final RoleplayHUD plugin;

    public Commands(RoleplayHUD plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (c.getName().equalsIgnoreCase("hud")) hud(s, a); else gang(s, a);
        return true;
    }

    // ---------- /hud ----------
    private void hud(CommandSender s, String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase() : "toggle";
        if (sub.equals("reload")) {
            if (!s.hasPermission("rphud.admin")) { plugin.send(s, "<red>Tidak punya izin."); return; }
            plugin.reloadConfig();
            plugin.hud().reload();
            plugin.send(s, "<green>Config dimuat ulang.");
            return;
        }
        if (!(s instanceof Player p)) { plugin.send(s, "<red>Hanya untuk pemain."); return; }
        boolean on = plugin.hud().toggle(p);
        plugin.send(p, on ? "<green>HUD dinyalakan." : "<yellow>HUD dimatikan.");
    }

    // ---------- /gang ----------
    private void gang(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { plugin.send(s, "<red>Hanya untuk pemain."); return; }
        GangManager gm = plugin.gangs();
        String sub = a.length > 0 ? a[0].toLowerCase() : "info";

        switch (sub) {
            case "create" -> {
                if (a.length < 3) { plugin.send(p, "<gray>Pakai: <white>/gang create <gang|keluarga> <nama (boleh lebih dari 1 kata)>"); return; }
                String gname = String.join(" ", Arrays.copyOfRange(a, 2, a.length)).replaceAll("\\s+", " ").trim();
                Gang.Type type = switch (a[1].toLowerCase()) {
                    case "gang" -> Gang.Type.GANG;
                    case "keluarga", "family" -> Gang.Type.KELUARGA;
                    default -> null;
                };
                if (type == null) { plugin.send(p, "<red>Tipe harus <white>gang</white> atau <white>keluarga</white>."); return; }
                if (!NAME.matcher(gname).matches()) { plugin.send(p, "<red>Nama 3-24 karakter (huruf, angka, spasi, _)."); return; }
                if (gm.byPlayer(p.getUniqueId()) != null) { plugin.send(p, "<red>Kamu sudah punya gang/keluarga."); return; }
                if (gm.byName(gname) != null) { plugin.send(p, "<red>Nama sudah dipakai."); return; }
                double cost = plugin.getConfig().getDouble("gang.create-cost", 0);
                if (!plugin.economy().has(p, cost)) { plugin.send(p, "<red>Uangmu kurang. Biaya: <gold>$" + (long) cost); return; }
                plugin.economy().withdrawPlayer(p, cost);
                gm.create(gname, type, p.getUniqueId());
                plugin.send(p, "<green>" + type.display() + " <yellow>" + gname + "</yellow> berhasil dibuat!");
            }
            case "rename" -> {
                Gang g = mine(p); if (g == null) return;
                if (!g.leader.equals(p.getUniqueId())) { plugin.send(p, "<red>Hanya pemimpin yang bisa mengganti nama."); return; }
                if (a.length < 2) { plugin.send(p, "<gray>Pakai: <white>/gang rename <nama baru>"); return; }
                String nn = String.join(" ", Arrays.copyOfRange(a, 1, a.length)).replaceAll("\\s+", " ").trim();
                if (!NAME.matcher(nn).matches()) { plugin.send(p, "<red>Nama 3-24 karakter (huruf, angka, spasi, _)."); return; }
                Gang other = gm.byName(nn);
                if (other != null && other != g) { plugin.send(p, "<red>Nama sudah dipakai."); return; }
                Gang ng = gm.rename(g, nn);
                gm.broadcast(ng, "<green>" + ng.type.display() + " diganti namanya menjadi <yellow>" + MM.escapeTags(ng.name) + "</yellow>.");
            }
            case "invite" -> {
                Gang g = mine(p); if (g == null) return;
                if (!g.leader.equals(p.getUniqueId())) { plugin.send(p, "<red>Hanya pemimpin yang bisa mengundang."); return; }
                if (a.length < 2) { plugin.send(p, "<gray>Pakai: <white>/gang invite <pemain>"); return; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { plugin.send(p, "<red>Pemain tidak online."); return; }
                if (gm.byPlayer(t.getUniqueId()) != null) { plugin.send(p, "<red>Pemain itu sudah punya gang/keluarga."); return; }
                if (g.members.size() >= plugin.getConfig().getInt("gang.max-members", 20)) { plugin.send(p, "<red>Anggota sudah penuh."); return; }
                gm.invite(t.getUniqueId(), g, plugin.getConfig().getLong("gang.invite-expire-seconds", 60));
                Component msg = MM.deserialize(plugin.getConfig().getString("prefix", "")
                                + "<yellow>" + MM.escapeTags(p.getName()) + "</yellow> mengundangmu ke " + g.type.display()
                                + " <gold>" + MM.escapeTags(g.name) + "</gold> ")
                        .append(MM.deserialize("<green><bold>[TERIMA]</bold></green>").clickEvent(ClickEvent.runCommand("/gang accept")));
                t.sendMessage(msg);
                plugin.send(p, "<green>Undangan dikirim ke <yellow>" + MM.escapeTags(t.getName()) + "</yellow>.");
            }
            case "accept" -> {
                GangManager.Invite inv = gm.takeInvite(p.getUniqueId());
                if (inv == null || System.currentTimeMillis() > inv.expiresMs()) { plugin.send(p, "<red>Tidak ada undangan yang berlaku."); return; }
                Gang g = gm.byName(inv.gang());
                if (g == null) { plugin.send(p, "<red>Gang/keluarga itu sudah tidak ada."); return; }
                if (gm.byPlayer(p.getUniqueId()) != null) { plugin.send(p, "<red>Kamu sudah punya gang/keluarga."); return; }
                if (g.members.size() >= plugin.getConfig().getInt("gang.max-members", 20)) { plugin.send(p, "<red>Anggota sudah penuh."); return; }
                gm.add(g, p.getUniqueId());
                gm.broadcast(g, "<green>" + MM.escapeTags(p.getName()) + " bergabung ke " + g.type.display() + "!");
            }
            case "leave" -> {
                Gang g = mine(p); if (g == null) return;
                if (g.leader.equals(p.getUniqueId())) {
                    if (g.members.size() == 1) {
                        gm.disband(g);
                        plugin.send(p, "<yellow>" + g.type.display() + " dibubarkan karena kamu pemimpin terakhir.");
                    } else {
                        plugin.send(p, "<red>Pemimpin tidak bisa keluar. Pakai <white>/gang transfer <pemain></white> atau <white>/gang disband</white>.");
                    }
                    return;
                }
                gm.remove(g, p.getUniqueId());
                plugin.send(p, "<yellow>Kamu keluar dari " + g.type.display() + ".");
                gm.broadcast(g, "<yellow>" + MM.escapeTags(p.getName()) + " keluar dari " + g.type.display() + ".");
            }
            case "kick" -> {
                Gang g = mine(p); if (g == null) return;
                if (!g.leader.equals(p.getUniqueId())) { plugin.send(p, "<red>Hanya pemimpin yang bisa mengeluarkan anggota."); return; }
                if (a.length < 2) { plugin.send(p, "<gray>Pakai: <white>/gang kick <pemain>"); return; }
                UUID target = memberByName(g, a[1]);
                if (target == null) { plugin.send(p, "<red>Anggota tidak ditemukan."); return; }
                if (target.equals(g.leader)) { plugin.send(p, "<red>Tidak bisa mengeluarkan pemimpin."); return; }
                gm.remove(g, target);
                gm.broadcast(g, "<yellow>" + MM.escapeTags(a[1]) + " dikeluarkan dari " + g.type.display() + ".");
                Player tp = Bukkit.getPlayer(target);
                if (tp != null) plugin.send(tp, "<red>Kamu dikeluarkan dari " + g.type.display() + " <white>" + MM.escapeTags(g.name) + "</white>.");
            }
            case "transfer" -> {
                Gang g = mine(p); if (g == null) return;
                if (!g.leader.equals(p.getUniqueId())) { plugin.send(p, "<red>Hanya pemimpin."); return; }
                if (a.length < 2) { plugin.send(p, "<gray>Pakai: <white>/gang transfer <pemain>"); return; }
                UUID target = memberByName(g, a[1]);
                if (target == null || target.equals(p.getUniqueId())) { plugin.send(p, "<red>Anggota tidak valid."); return; }
                g.leader = target;
                gm.save();
                gm.broadcast(g, "<gold>" + MM.escapeTags(a[1]) + " sekarang menjadi pemimpin!");
            }
            case "disband" -> {
                Gang g = mine(p); if (g == null) return;
                if (!g.leader.equals(p.getUniqueId())) { plugin.send(p, "<red>Hanya pemimpin."); return; }
                gm.broadcast(g, "<red>" + g.type.display() + " <white>" + MM.escapeTags(g.name) + "</white> dibubarkan.");
                if (g.bank > 0) plugin.economy().depositPlayer(p, g.bank);
                gm.disband(g);
            }
            case "menu" -> {
                Gang g = mine(p); if (g == null) return;
                GangMenu.open(p, g);
            }
            case "deposit", "withdraw" -> {
                Gang g = mine(p); if (g == null) return;
                if (a.length < 2) { plugin.send(p, "<gray>Pakai: <white>/gang " + sub + " <jumlah>"); return; }
                double amt;
                try { amt = Double.parseDouble(a[1]); } catch (NumberFormatException ex) { plugin.send(p, "<red>Jumlah harus angka."); return; }
                if (amt <= 0 || Double.isNaN(amt) || Double.isInfinite(amt)) { plugin.send(p, "<red>Jumlah tidak valid."); return; }
                if (sub.equals("deposit")) {
                    if (!plugin.economy().has(p, amt)) { plugin.send(p, "<red>Uangmu tidak cukup."); return; }
                    plugin.economy().withdrawPlayer(p, amt);
                    g.bank += amt;
                    gm.save();
                    gm.broadcast(g, "<green>" + MM.escapeTags(p.getName()) + " menyetor <gold>$" + (long) amt + "</gold> ke kas.");
                } else {
                    if (!g.leader.equals(p.getUniqueId())) { plugin.send(p, "<red>Hanya pemimpin yang bisa menarik kas."); return; }
                    if (g.bank < amt) { plugin.send(p, "<red>Saldo kas tidak cukup."); return; }
                    g.bank -= amt;
                    plugin.economy().depositPlayer(p, amt);
                    gm.save();
                    gm.broadcast(g, "<yellow>" + MM.escapeTags(p.getName()) + " menarik <gold>$" + (long) amt + "</gold> dari kas.");
                }
            }
            default -> {
                Gang g = gm.byPlayer(p.getUniqueId());
                if (g == null) {
                    plugin.send(p, "<gray>Kamu belum punya gang/keluarga. Buat dengan <white>/gang create <gang|keluarga> <nama>");
                    return;
                }
                StringBuilder names = new StringBuilder();
                for (UUID id : g.members) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                    if (!names.isEmpty()) names.append(", ");
                    names.append(op.getName() == null ? "?" : MM.escapeTags(op.getName()));
                }
                plugin.send(p, "<gold>" + g.type.display() + ": <white>" + MM.escapeTags(g.name)
                        + "\n<gray>Kas: <green>$" + String.format(Locale.US, "%,.0f", g.bank)
                        + "\n<gray>Anggota (" + g.members.size() + "): <white>" + names);
            }
        }
    }

    private Gang mine(Player p) {
        Gang g = plugin.gangs().byPlayer(p.getUniqueId());
        if (g == null) plugin.send(p, "<red>Kamu belum punya gang/keluarga.");
        return g;
    }

    private UUID memberByName(Gang g, String name) {
        for (UUID id : g.members) {
            String n = Bukkit.getOfflinePlayer(id).getName();
            if (n != null && n.equalsIgnoreCase(name)) return id;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String cmd = c.getName().toLowerCase();
        if (cmd.equals("hud")) {
            if (a.length == 1) out.addAll(List.of("toggle", "reload"));
        } else if (a.length == 1) {
            out.addAll(List.of("create", "rename", "invite", "accept", "leave", "kick", "transfer", "disband", "info", "menu", "deposit", "withdraw"));
        } else if (a.length == 2) {
            switch (a[0].toLowerCase()) {
                case "create" -> out.addAll(List.of("gang", "keluarga"));
                case "invite" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                case "kick", "transfer" -> {
                    if (s instanceof Player p) {
                        Gang g = plugin.gangs().byPlayer(p.getUniqueId());
                        if (g != null) for (UUID id : g.members) {
                            String n = Bukkit.getOfflinePlayer(id).getName();
                            if (n != null) out.add(n);
                        }
                    }
                }
            }
        }
        String last = a.length > 0 ? a[a.length - 1].toLowerCase() : "";
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
