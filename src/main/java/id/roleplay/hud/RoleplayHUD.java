package id.roleplay.hud;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;

public class RoleplayHUD extends JavaPlugin implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private Economy economy;
    private GangManager gangs;
    private HudManager hud;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("Tidak ada plugin ekonomi (Vault + EssentialsX/dll). Plugin dimatikan.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        economy = rsp.getProvider();
        gangs = new GangManager(this);
        hud = new HudManager(this);

        Commands cmds = new Commands(this);
        for (String name : new String[]{"gang", "hud"}) {
            getCommand(name).setExecutor(cmds);
            getCommand(name).setTabCompleter(cmds);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new GangMenu(), this);
    }

    @Override
    public void onDisable() {
        if (gangs != null) gangs.save();
        if (hud != null) hud.shutdown();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (getConfig().getBoolean("join-title.enabled", true)) {
            String t = hud.apply(getConfig().getString("join-title.title", ""), p);
            String st = hud.apply(getConfig().getString("join-title.subtitle", ""), p);
            p.showTitle(Title.title(MM.deserialize(t), MM.deserialize(st),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(800))));
        }
        hud.update(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        hud.remove(e.getPlayer().getUniqueId());
    }

    public Economy economy() { return economy; }
    public GangManager gangs() { return gangs; }
    public HudManager hud() { return hud; }

    public void send(CommandSender to, String miniMessage) {
        to.sendMessage(MM.deserialize(getConfig().getString("prefix", "") + miniMessage));
    }
}
