package id.roleplay.hud;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Jembatan ke Nexo lewat PlaceholderAPI (pakai reflection, jadi tidak perlu dependensi compile).
 * %nexo_<glyph_id>%  -> karakter glyph (font default)
 * %nexo_shift_<n>%   -> karakter spasi geser (n piksel, boleh negatif)
 */
public class NexoBridge {
    private final RoleplayHUD plugin;
    private Method setPlaceholders;
    private final Map<String, String> cache = new HashMap<>();
    private final Set<String> warned = new HashSet<>();

    public NexoBridge(RoleplayHUD plugin) {
        this.plugin = plugin;
        try {
            Class<?> c = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            setPlaceholders = c.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        } catch (Exception e) {
            setPlaceholders = null;
        }
    }

    public boolean available() {
        return setPlaceholders != null
                && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
                && Bukkit.getPluginManager().isPluginEnabled("Nexo");
    }

    public void clearCache() {
        cache.clear();
        warned.clear();
    }

    private String papi(Player p, String text) {
        try {
            return (String) setPlaceholders.invoke(null, p, text);
        } catch (Exception e) {
            return text;
        }
    }

    private String resolve(Player p, String key, String raw) {
        String cached = cache.get(key);
        if (cached != null) return cached;
        String r = papi(p, raw);
        if (r == null || r.equals(raw) || r.contains("<glyph") || r.isBlank()) {
            if (warned.add(key)) {
                plugin.getLogger().warning("Glyph/shift '" + raw + "' belum bisa dipakai. Pastikan glyph ada di "
                        + "plugins/Nexo/glyphs/hud.yml, tidak memakai font custom, dan /nexo reload all sudah dijalankan.");
            }
            return "";
        }
        cache.put(key, r);
        return r;
    }

    public String glyph(Player p, String id) {
        return resolve(p, "g:" + id, "%nexo_" + id + "%");
    }

    public String shift(Player p, int n) {
        if (n == 0) return "";
        return resolve(p, "s:" + n, "%nexo_shift_" + n + "%");
    }

    /** Perkiraan lebar teks font default Minecraft (piksel GUI, sudah termasuk jarak antar huruf). */
    public static int width(String s) {
        int w = 0;
        for (char ch : s.toCharArray()) w += charWidth(ch);
        return w;
    }

    private static int charWidth(char c) {
        return switch (c) {
            case ' ' -> 4;
            case '!', '.', ',', ':', ';', 'i', '|' -> 2;
            case '\'', 'l', '`' -> 3;
            case 'I', 't', '[', ']', '*' -> 4;
            case '(', ')', '{', '}', '<', '>', 'f', 'k', '"' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }
}
