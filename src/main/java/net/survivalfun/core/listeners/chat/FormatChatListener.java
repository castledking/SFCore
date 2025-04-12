package net.survivalfun.core.listeners.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.query.QueryOptions;
import net.milkbowl.vault2.chat.Chat;
import net.survivalfun.core.managers.config.Config;
import net.survivalfun.core.utils.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormatChatListener implements Listener {

    private final JavaPlugin plugin;
    private final LegacyComponentSerializer legacyComponentSerializer;
    private final LuckPerms luckPerms;
    private final Chat vaultChat;
    private final Map<String, String> groupFormats;
    private final boolean useVaultGroups;
    private final Config config;
    private final String defaultFormat;
    private final boolean placeholderAPIEnabled;
    private final Pattern placeholderPattern = Pattern.compile("%\\{([^}]+)\\}%");

    public FormatChatListener(JavaPlugin plugin, Chat vaultChat, LuckPerms luckPerms, Config config) {
        this.plugin = plugin;
        this.config = config;
        this.vaultChat = vaultChat;
        this.luckPerms = luckPerms;

        legacyComponentSerializer = LegacyComponentSerializer.builder().hexColors().build();

        // Get the default format from config
        this.defaultFormat = config.getString("chat-format.default", "<prefix> &a<player>&f: &f<message>");

        // Check if PlaceholderAPI is available
        this.placeholderAPIEnabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (placeholderAPIEnabled) {
            plugin.getLogger().info("PlaceholderAPI found! Chat format will support placeholders.");
        } else {
            plugin.getLogger().info("PlaceholderAPI not found. Placeholders in chat format will not work.");
        }

        // Determine Group Mode and Load Formats
        if (vaultChat != null && luckPerms == null) {
            useVaultGroups = true;
            plugin.getLogger().info("Using Vault for group formatting.");
        } else if (luckPerms != null) {
            useVaultGroups = false;
            plugin.getLogger().info("Using LuckPerms for group formatting.");
        } else {
            useVaultGroups = false;
            plugin.getLogger().warning("Neither LuckPerms nor Vault found, disabling group formatting.");
        }

        // Load group formats safely
        groupFormats = new HashMap<>();
        loadGroupFormats();
    }

    /**
     * Loads group formats from the config
     */
    private void loadGroupFormats() {
        FileConfiguration fileConfig = plugin.getConfig();
        ConfigurationSection groupsSection = fileConfig.getConfigurationSection("chat-format.groups");

        if (groupsSection != null) {
            Map<String, Object> tempMap = groupsSection.getValues(false);
            for (Map.Entry<String, Object> entry : tempMap.entrySet()) {
                groupFormats.put(entry.getKey(), entry.getValue().toString());
            }
            plugin.getLogger().info("Loaded " + groupFormats.size() + " group chat formats.");
        } else {
            plugin.getLogger().info("No group chat formats found in config. Using default format for all groups.");
        }
    }

    public boolean canEnable() {
        return luckPerms != null || vaultChat != null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(@NotNull AsyncChatEvent event) {
        // Check if chat formatting is enabled
        if (!config.getBoolean("enable-chat-formatting")) {
            return;
        }

        boolean debugMode = config.getBoolean("debug-mode");
        Player player = event.getPlayer();

        // Safety check for player
        if (player == null) {
            plugin.getLogger().warning("Received chat event with null player, skipping formatting");
            return;
        }

        Component messageComponent = event.message();
        String rawMessage = legacyComponentSerializer.serialize(messageComponent); // Serialize the message

        if (debugMode) {
            plugin.getLogger().info("Raw message from " + player.getName() + ": " + rawMessage);
        }

        // Process color codes based on player permissions
        String processedMessage;
        if (player.hasPermission("chat.color")) {
            // Apply color codes based on specific permissions
            processedMessage = Text.stripColor(rawMessage, player);
            // Parse the colors in the message
            processedMessage = Text.parseColors(processedMessage);

            if (debugMode) {
                plugin.getLogger().info("Processed message with colors: " + processedMessage);
            }
        } else {
            // Strip all color codes if player doesn't have permission
            processedMessage = Text.stripColor(rawMessage, null);

            if (debugMode) {
                plugin.getLogger().info("Processed message without colors: " + processedMessage);
            }
        }

        String chatFormat = getChatFormat(player); // Get the appropriate chat format
        if (debugMode) {
            plugin.getLogger().info("Chat format: " + chatFormat);
        }

        String formattedMessage = replacePlaceholders(chatFormat, player, processedMessage); // Replace placeholders

        // Parse colors in the entire formatted message (including format, prefix, etc.)
        formattedMessage = Text.parseColors(formattedMessage);

        if (debugMode) {
            plugin.getLogger().info("Final formatted message: " + formattedMessage);
        }

        // Create a component from the formatted message
        Component formattedComponent = legacyComponentSerializer.deserialize(formattedMessage);

        // Set the message to our formatted component instead of cancelling
        event.renderer((source, sourceDisplayName, message, viewer) -> formattedComponent);
    }

    private String getChatFormat(Player player) {
        String groupName = "default"; // Default group

        // Debug mode logging
        boolean debugMode = config.getBoolean("debug-mode");

        if (useVaultGroups && vaultChat != null) {
            groupName = vaultChat.getPrimaryGroup(player);
            if (debugMode) {
                plugin.getLogger().info("Vault group for " + player.getName() + ": " + groupName);
            }
        } else if (luckPerms != null) {
            User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
            CachedMetaData metaData = user.getCachedData().getMetaData(QueryOptions.nonContextual());
            groupName = metaData.getPrimaryGroup();
            if (groupName == null) {
                groupName = "default";
            }
            if (debugMode) {
                plugin.getLogger().info("LuckPerms group for " + player.getName() + ": " + groupName);
            }
        }

        // Get format for the group, or the default format if not found
        if (groupFormats.containsKey(groupName)) {
            return groupFormats.get(groupName);
        } else {
            if (debugMode) {
                plugin.getLogger().info("No format found for group: " + groupName + ", using default format");
            }
            return defaultFormat;
        }
    }

    private String replacePlaceholders(String format, Player player, String message) {
        String prefix = getPrefix(player);
        String displayName = player.getDisplayName();
        String playerName = player.getName();

        // Debug mode logging
        boolean debugMode = config.getBoolean("debug-mode");
        if (debugMode) {
            plugin.getLogger().info("Format before replacement: " + format);
            plugin.getLogger().info("Prefix: " + prefix);
            plugin.getLogger().info("Player name: " + playerName);
            plugin.getLogger().info("Display name: " + displayName);
            plugin.getLogger().info("Message: " + message);
        }

        String result = format.replace("<prefix>", prefix)
                .replace("<player>", playerName)
                .replace("<display_name>", displayName)
                .replace("<name>", playerName) // In case they use <name>
                .replace("<message>", message);

        // Process PlaceholderAPI placeholders if available
        if (placeholderAPIEnabled) {
            result = processPlaceholderAPIPlaceholders(result, player);
        }

        if (debugMode) {
            plugin.getLogger().info("Format after replacement: " + result);
        }

        return result;
    }

    /**
     * Process PlaceholderAPI placeholders in the format %{placeholder}%
     * @param text The text containing placeholders
     * @param player The player to process placeholders for
     * @return The text with processed placeholders
     */
    private String processPlaceholderAPIPlaceholders(String text, Player player) {
        if (!placeholderAPIEnabled) return text;

        // First, handle our custom format %{placeholder}%
        Matcher matcher = placeholderPattern.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            // Convert our format to PlaceholderAPI format
            String papiPlaceholder = "%" + placeholder + "%";
            // Process with PlaceholderAPI
            String replacement = PlaceholderAPI.setPlaceholders(player, papiPlaceholder);
            // Replace in the string
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);

        // Also process any standard PlaceholderAPI placeholders that might be in the text
        return PlaceholderAPI.setPlaceholders(player, buffer.toString());
    }

    private String getPrefix(Player player) {
        String prefix = ""; // Initialize prefix
        boolean debugMode = config.getBoolean("debug-mode");

        try {
            if (luckPerms != null) {
                User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
                // LuckPerms API guarantees that user will not be null here.
                CachedMetaData metaData = user.getCachedData().getMetaData(QueryOptions.nonContextual());
                // metaData should not be null
                prefix = Objects.requireNonNullElse(metaData.getPrefix(), "");
                if (debugMode) {
                    plugin.getLogger().info("LuckPerms Prefix: " + prefix);
                }
            } else if (vaultChat != null) {
                prefix = Objects.requireNonNullElse(vaultChat.getPlayerPrefix(player), "");
                if (debugMode) {
                    plugin.getLogger().info("Vault Prefix: " + prefix);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting prefix: " + e.getMessage());
        }

        if (debugMode) {
            plugin.getLogger().info("Final Prefix: " + prefix);
        }
        return Text.parseColors(prefix);
    }

    /**
     * Reloads the chat formats from the config
     */
    public void reload() {
        groupFormats.clear();
        loadGroupFormats();
    }
}
