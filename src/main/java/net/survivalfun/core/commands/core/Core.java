package net.survivalfun.core.commands.core;

import net.survivalfun.core.PluginStart;
import net.survivalfun.core.commands.fun.Explode;
import net.survivalfun.core.listeners.security.CommandManager;
import net.survivalfun.core.managers.config.WorldDefaults;
import net.survivalfun.core.managers.lang.Lang;
import net.survivalfun.core.managers.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class Core implements CommandExecutor, TabCompleter {

    private final WorldDefaults worldDefaults;
    private final PluginStart plugin;
    private final FileConfiguration config;
    private final Lang lang;
    private final CommandManager commandManager;

    public Core(WorldDefaults worldDefaults, PluginStart plugin, FileConfiguration config, Lang lang, CommandManager commandManager) {
        this.worldDefaults = worldDefaults;
        this.plugin = plugin;
        this.config = config;
        this.lang = plugin.getLangManager();
        this.commandManager = commandManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (lang == null) {
            sender.sendMessage("Error: Language system not initialized");
            plugin.getLogger().log(Level.SEVERE, "LanguageManager not initialized when executing Core command");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "setgamerule":
                handleSetGameruleCommand(sender, args);
                break;


            case "reload":
                handleReloadCommand(sender, null);
                break;

            case "debug":
                handleDebugCommand(sender, args);
                break;
            case "hideupdate":
                handleHideUpdateCommand(sender, args);
                break;
            default:
                sender.sendMessage("§cUnknown subcommand. Use /core for help.");
                break;
        }

        return true;
    }

    private void handleHideUpdateCommand(@NotNull CommandSender sender, String @NotNull [] args) {
        if(!sender.hasPermission("core.admin")) {
            Text.sendErrorMessage(sender, "no-permission", lang, "{cmd}", "core");
            return;
        }
        if(args.length == 2 || args[1].equalsIgnoreCase("all")) {
            int count = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                commandManager.updatePlayerTabCompletion(player);
                count++;
            }
            sender.sendMessage(ChatColor.GREEN + "Updated tab completion for " + count + " players.");
            return;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            Text.sendErrorMessage(sender, "player-not-found", lang, "{player}", args[0]);
            return;
        }
        commandManager.updatePlayerTabCompletion(target);
        sender.sendMessage(ChatColor.GREEN + "Updated tab completion for " + target.getName());
    }

    private void sendHelpMessage(CommandSender sender) {
        if(!sender.hasPermission("core.admin")) {
            Text.sendErrorMessage(sender, "no-permission", lang, "{cmd}", "core");
            return;
        }
        sender.sendMessage("§aAvailable /core subcommands:");
        sender.sendMessage("§edebug §7- Toggle debug mode.");
        sender.sendMessage("§ereload §7- Reload the plugin configuration.");
        sender.sendMessage("§ehideupdate §7- Refresh tab completion for player.");
    }

    private void handleSetGameruleCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("core.worlddefaults")) {
            sender.sendMessage(lang.get("no-permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /core worlddefaults <gamerule> <value>");
            return;
        }

        String gamerule = args[1].toLowerCase();
        String value = args[2];

        // Update the gamerule in the config
        if (!worldDefaults.plugin().getConfig().contains("world-defaults." + gamerule)) {
            sender.sendMessage("§cInvalid gamerule: " + gamerule);
            return;
        }

        try {
            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                worldDefaults.plugin().getConfig().set("world-defaults." + gamerule, Boolean.parseBoolean(value));
            } else {
                int intValue = Integer.parseInt(value);
                worldDefaults.plugin().getConfig().set("world-defaults." + gamerule, intValue);
            }

            // Save and apply the changes
            worldDefaults.plugin().saveConfig();
            worldDefaults.applyWorldDefaults();

            sender.sendMessage("§aUpdated gamerule '" + gamerule + "' to '" + value + "' and applied it to all worlds.");
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid value. Use true, false, or a number.");
        }
    }

    private void handleDebugCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("core.admin")) {
            Text.sendErrorMessage(sender, "no-permission", lang);
            return;
        }
        // Toggle debug mode in config
        boolean currentDebugMode = plugin.getConfig().getBoolean("debug-mode", false);
        boolean newDebugMode = !currentDebugMode;

        // Update config
        plugin.getConfig().set("debug-mode", newDebugMode);
        plugin.saveConfig();
        handleReloadCommand(sender, true);



        // Log the change
        plugin.getLogger().info("Debug mode " + (newDebugMode ? "enabled" : "disabled") + " by " + sender.getName());
    }


    private void handleReloadCommand(CommandSender sender, Boolean isDebug) {
        if (!sender.hasPermission("core.admin")) {
            Text.sendErrorMessage(sender, "no-permission", lang, "{cmd}", "core");
            return;
        }
        try {
            // Reload the main config
            plugin.reloadConfig();

            // Reload the command manager
            commandManager.reload();

            // Re-initialize the ConfigManager to apply changes
            if (plugin.getConfigManager() != null) {
                plugin.getConfigManager().load();
            }

            // Reload language files
            if (lang != null) {
                lang.reload();
            }

            // Reload world defaults
            if (worldDefaults != null) {
                worldDefaults.plugin().reloadConfig();
                worldDefaults.applyWorldDefaults();
            }

            // Reload the chat formatter to apply new chat format settings
            plugin.reloadChatFormatter();

            // Reload the explode command configuration
            Explode explodeCommand = plugin.getExplodeCommand();
            if (explodeCommand != null) {
                explodeCommand.reloadConfig();
            }

            // Build success message with optional debug status
            StringBuilder successMessage = new StringBuilder("§aConfiguration reloaded successfully.");

            // Add debug status if isDebug is not null (meaning it was explicitly set)
            if (isDebug != null) {
                boolean debugMode = plugin.getConfig().getBoolean("debug-mode", false);
                successMessage.append(" (Debug: ").append(debugMode ? "§aenabled§a" : "§cdisabled§a").append(")");
            }

            sender.sendMessage(successMessage.toString());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error reloading configuration: ", e);
            sender.sendMessage("§cError reloading configuration! Check console for details.");
        }
    }
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (!command.getName().equalsIgnoreCase("core")) {
            return null;
        }

        return getCoreSuggestions(sender, args);
    }

    private List<String> getCoreSuggestions(@NotNull CommandSender sender, String @NotNull [] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            // Suggest subcommands for /core
            suggestions.addAll(List.of("reload", "debug", "hideupdate"));
        } else if (args.length > 1 && args[0].equalsIgnoreCase("worlddefaults")) {
            suggestions.addAll(getWorldDefaultsSuggestions(sender, args));
        } else if (args.length > 1 && args[0].equalsIgnoreCase("updatehide")) {
            suggestions.addAll(getUpdateHideSuggestions(args));
        }

        return filterSuggestions(suggestions, args[args.length - 1]);
    }

    private List<String> getWorldDefaultsSuggestions(@NotNull CommandSender sender, String @NotNull [] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 2) {
            // Suggest gamerules from the config.yml under "world-defaults"
            if (config.isConfigurationSection("world-defaults")) {
                suggestions.addAll(config.getConfigurationSection("world-defaults").getKeys(false));
            }
        } else if (args.length == 3) {
            // Suggest possible values for gamerules
            suggestions.addAll(List.of("true", "false", "1", "0"));
        }

        return suggestions;
    }

    private List<String> getUpdateHideSuggestions(String[] args) {
        List<String> suggestions = new ArrayList<>();
        if(args.length == 1) {
            suggestions.add("all");

            for (Player player : Bukkit.getOnlinePlayers()) {
                suggestions.add(player.getName());
            }
        }
        return suggestions;
    }

    private List<String> filterSuggestions(List<String> suggestions, String currentInput) {
        List<String> filtered = new ArrayList<>();
        for (String suggestion : suggestions) {
            if (suggestion.toLowerCase().startsWith(currentInput.toLowerCase())) {
                filtered.add(suggestion);
            }
        }
        return filtered;
    }
}