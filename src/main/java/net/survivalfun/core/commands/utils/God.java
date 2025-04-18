package net.survivalfun.core.commands.utils;

import net.survivalfun.core.PluginStart;
import net.survivalfun.core.managers.lang.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

public class God implements CommandExecutor {

    private final PluginStart plugin;

    public God(PluginStart plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        Lang lang = plugin.getLangManager();
        if (lang == null) {
            sender.sendMessage("Error: Language system not initialized");
            plugin.getLogger().log(Level.SEVERE, "LanguageManager not initialized when executing GC command");
            return true;
        }
        if (command.getName().equalsIgnoreCase("god")) {
            if (args.length == 0) {
                // /god (self)
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(lang.get("not-a-player"));
                    return true;
                }
                if (!player.hasPermission("core.god")) {
                    sender.sendMessage(lang.get("no-permission"));
                    return true;
                }
                boolean godModeToggled = toggleGodMode(player);
                if (godModeToggled) {
                    sender.sendMessage(lang.get("god.enable"));
                } else {
                    sender.sendMessage(lang.get("god.disable"));
                }


            } else if (args.length == 1) {
                // /god <player>
                if (sender.hasPermission("core.god.others")) {
                    Player target = Bukkit.getPlayer(args[0]);
                    if (target == null) {
                        sender.sendMessage(lang.get("player-not-found")
                                .replace("{name}", args[0]));
                        return true;
                    }
                    boolean godModeToggled = toggleGodMode(target);
                    if (godModeToggled) {
                        sender.sendMessage(lang.get("god.enable-other")
                                .replace("{name}", target.getName()));
                        target.sendMessage(lang.get("god.enable"));
                    } else {
                        sender.sendMessage(lang.get("god.disable-other")
                                .replace("{name}", target.getName()));
                        target.sendMessage(lang.get("god.disable"));
                    }

                } else {
                    sender.sendMessage(lang.get("no-permission"));
                    return true;
                }

            } else {
                sender.sendMessage(lang.get("command-usage")
                        .replace("{cmd}", label)
                        .replace("{args}", "[player]"));
                return true;
            }
            return true;
        }
        return false;
    }

    private boolean toggleGodMode(Player player) {
        NamespacedKey key = new NamespacedKey(plugin, "godmode");
        PersistentDataContainer dataContainer = player.getPersistentDataContainer();
        boolean godModeEnabled;

        if (dataContainer.has(key, PersistentDataType.INTEGER)) {
            Integer godMode = dataContainer.get(key, PersistentDataType.INTEGER);
            if (godMode != null && godMode == 1) {
                dataContainer.set(key, PersistentDataType.INTEGER, 0);
                player.setInvulnerable(false);
                godModeEnabled = false;



            } else {
                dataContainer.set(key, PersistentDataType.INTEGER, 1);
                player.setInvulnerable(true);
                godModeEnabled = true;
            }
        } else {
            dataContainer.set(key, PersistentDataType.INTEGER, 1);
            player.setInvulnerable(true);
            godModeEnabled = true;
        }
        return godModeEnabled;
    }
}
