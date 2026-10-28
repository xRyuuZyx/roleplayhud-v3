package id.roleplay.hud;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

/** HUD ala FiveM: kartu info di atas (boss bar) + bar status di bawah (action bar), semua pakai glyph Nexo. */
public class NexoHud {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int ICON_ADVANCE = 9; // 8px + 1

    private final RoleplayHUD plugin;
    private final HudManager hud;
    private final NexoBridge bridge;
    private final Map<UUID, List<BossBar>> bars = new HashMap<>();

    public NexoHud(RoleplayHUD plugin, HudManager hud, NexoBridge bridge) {
        this.plugin = plugin;
        this.hud = hud;
        this.bridge = bridge;
    }

    public void update(Player p) {
        FileConfiguration cfg = plugin.getConfig();
        List<Component> cards = new ArrayList<>();

        int cw = cfg.getInt("nexo.card-width", 121);
        int pad = cfg.getInt("nexo.text-padding", 15);
        int iconPad = cfg.getInt("nexo.icon-padding", 4);
        int offset = cfg.getInt("nexo.offset", 140);
        String cardGlyph = cfg.getString("nexo.card-glyph", "hud_card");

        for (Map<?, ?> row : cfg.getMapList("nexo.rows")) {
            String icon = String.valueOf(row.get("icon"));
            String text = hud.apply(String.valueOf(row.get("text")), p);
            int w = NexoBridge.width(PlainTextComponentSerializer.plainText().serialize(MM.deserialize(text)));

            // Total lebar selalu = 2*offset + cw, jadi kartu tidak bergeser walau teks berubah panjang.
            StringBuilder sb = new StringBuilder("<white>");
            sb.append(bridge.shift(p, offset * 2));
            sb.append(bridge.glyph(p, cardGlyph));
            sb.append(bridge.shift(p, -cw));
            sb.append(bridge.shift(p, iconPad));
            sb.append(bridge.glyph(p, icon));
            sb.append(bridge.shift(p, pad - iconPad - ICON_ADVANCE));
            sb.append("<reset>").append(text);
            sb.append(bridge.shift(p, cw - pad - w));
            cards.add(MM.deserialize(sb.toString()));
        }
        showBars(p, cards);

        Component status = statusBar(p);
        if (status != null) p.sendActionBar(status);
    }

    private Component statusBar(Player p) {
        FileConfiguration cfg = plugin.getConfig();
        List<Map<?, ?>> stats = cfg.getMapList("nexo.status");
        if (stats.isEmpty()) return null;
        int segments = Math.max(1, cfg.getInt("nexo.segments", 10));
        int gap = cfg.getInt("nexo.status-gap", 8);
        String seg = bridge.glyph(p, cfg.getString("nexo.segment-glyph", "hud_seg"));
        String empty = cfg.getString("nexo.empty-color", "#33333d");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stats.size(); i++) {
            Map<?, ?> s = stats.get(i);
            double pct = percent(p, String.valueOf(s.get("stat")));
            int k = (int) Math.round(pct * segments);
            if (pct > 0 && k == 0) k = 1;
            String color = String.valueOf(s.get("color"));
            sb.append("<white>").append(bridge.glyph(p, String.valueOf(s.get("icon")))).append(bridge.shift(p, 2));
            sb.append("<").append(color).append(">").append(seg.repeat(k));
            sb.append("<").append(empty).append(">").append(seg.repeat(Math.max(0, segments - k)));
            if (i < stats.size() - 1) sb.append(bridge.shift(p, gap));
        }
        return MM.deserialize(sb.toString());
    }

    private double percent(Player p, String stat) {
        double v = switch (stat.toLowerCase()) {
            case "health" -> {
                AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                yield p.getHealth() / (max == null ? 20.0 : max.getValue());
            }
            case "armor" -> {
                AttributeInstance a = p.getAttribute(Attribute.ARMOR);
                yield (a == null ? 0 : a.getValue()) / 20.0;
            }
            case "food" -> p.getFoodLevel() / 20.0;
            default -> 0;
        };
        return Math.max(0, Math.min(1, v));
    }

    private void showBars(Player p, List<Component> comps) {
        List<BossBar> list = bars.get(p.getUniqueId());
        if (list == null || list.size() != comps.size()) {
            hide(p);
            list = new ArrayList<>();
            for (Component c : comps) {
                BossBar b = BossBar.bossBar(c, 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                p.showBossBar(b);
                list.add(b);
            }
            bars.put(p.getUniqueId(), list);
            return;
        }
        for (int i = 0; i < comps.size(); i++) list.get(i).name(comps.get(i));
    }

    public void hide(Player p) {
        List<BossBar> list = bars.remove(p.getUniqueId());
        if (list != null) for (BossBar b : list) p.hideBossBar(b);
        p.sendActionBar(Component.empty());
    }

    public void hideAll() {
        for (Map.Entry<UUID, List<BossBar>> e : new ArrayList<>(bars.entrySet())) {
            Player p = plugin.getServer().getPlayer(e.getKey());
            if (p != null) for (BossBar b : e.getValue()) p.hideBossBar(b);
        }
        bars.clear();
    }

    public void forget(UUID id) { bars.remove(id); }
}
