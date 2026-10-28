package id.roleplay.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class GangMenu implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private static Component c(String s) {
        return MM.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        if (lore.length > 0) meta.lore(Arrays.stream(lore).map(GangMenu::c).toList());
        it.setItemMeta(meta);
        return it;
    }

    public static void open(Player p, Gang g) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 54,
                MM.deserialize("<gold>" + g.type.display() + ": <white>" + MM.escapeTags(g.name)));
        h.inv = inv;

        // Pemimpin dulu, lalu anggota lain
        List<UUID> order = new ArrayList<>();
        order.add(g.leader);
        for (UUID id : g.members) if (!id.equals(g.leader)) order.add(id);

        int slot = 0;
        for (UUID id : order) {
            if (slot >= 45) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            boolean leader = id.equals(g.leader);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(op);
            String nm = op.getName() == null ? "Unknown" : MM.escapeTags(op.getName());
            sm.displayName(c((leader ? "<gold>★ " : "<white>") + nm));
            sm.lore(List.of(
                    c("<gray>Jabatan: <white>" + (leader ? "Pemimpin" : "Anggota")),
                    c(op.isOnline() ? "<green>● Online" : "<red>● Offline")));
            head.setItemMeta(sm);
            inv.setItem(slot++, head);
        }

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);
        inv.setItem(48, item(Material.GOLD_INGOT, "<gold>Kas " + g.type.display(),
                "<gray>Saldo: <green>$" + String.format(Locale.US, "%,.0f", g.bank),
                "<dark_gray>/gang deposit <jumlah>", "<dark_gray>/gang withdraw <jumlah> (pemimpin)"));
        inv.setItem(49, item(Material.NAME_TAG, "<yellow>" + MM.escapeTags(g.name),
                "<gray>Tipe: <white>" + g.type.display(), "<gray>Anggota: <white>" + g.members.size()));
        inv.setItem(50, item(Material.BARRIER, "<red>Keluar dari " + g.type.display(),
                "<gray>Klik untuk keluar"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (e.getRawSlot() == 50 && e.getWhoClicked() instanceof Player p) {
            p.closeInventory();
            p.performCommand("gang leave");
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
