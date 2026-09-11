package me.primesmp.scoreboard;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PrimeSMPScoreboard extends JavaPlugin implements Listener {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private final Map<UUID, PlayerBoard> boards = new HashMap<>();
    private final Map<UUID, Long> joinTimes = new HashMap<>();

    private Economy economy;
    private Permission permission;
    private org.bukkit.configuration.file.YamlConfiguration data;

    private int updateTask = -1;
    private int titleTask = -1;
    private int titleIndex = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("data.yml");

        loadData();
        setupVault();

        Bukkit.getPluginManager().registerEvents(this, this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            joinTimes.put(player.getUniqueId(), System.currentTimeMillis());
            createBoard(player);
        }

        startTasks();
        getLogger().info("PrimeSMP Scoreboard v2 enabled.");
    }

    @Override
    public void onDisable() {
        if (updateTask != -1) Bukkit.getScheduler().cancelTask(updateTask);
        if (titleTask != -1) Bukkit.getScheduler().cancelTask(titleTask);

        for (PlayerBoard board : boards.values()) {
            board.remove();
        }

        saveData();
    }

    private void saveResourceIfMissing(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) saveResource(name, false);
    }

    private void setupVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault is not installed. Money/rank will show N/A.");
            return;
        }

        RegisteredServiceProvider<Economy> eco = getServer().getServicesManager().getRegistration(Economy.class);
        if (eco != null) economy = eco.getProvider();

        RegisteredServiceProvider<Permission> perms = getServer().getServicesManager().getRegistration(Permission.class);
        if (perms != null) permission = perms.getProvider();

        if (economy == null) getLogger().warning("No Vault economy provider found.");
        if (permission == null) getLogger().warning("No Vault permission provider found.");
    }

    private void loadData() {
        data = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new File(getDataFolder(), "data.yml")
        );
    }

    private void saveData() {
        if (data == null) return;
        try {
            data.save(new File(getDataFolder(), "data.yml"));
        } catch (IOException e) {
            getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    private void startTasks() {
        long updateTicks = Math.max(1, getConfig().getLong("scoreboard.update-interval", 20));
        long titleTicks = Math.max(1, getConfig().getLong("scoreboard.title-interval", 5));

        updateTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (!getConfig().getBoolean("scoreboard.enabled", true)) return;

            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerBoard board = boards.get(player.getUniqueId());
                if (board == null) {
                    createBoard(player);
                    board = boards.get(player.getUniqueId());
                }
                if (board != null) board.update(player);
            }
        }, 20L, updateTicks);

        titleTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            List<String> titles = getConfig().getStringList("scoreboard.animated-title");
            if (titles.isEmpty()) return;

            titleIndex++;
            if (titleIndex >= titles.size()) titleIndex = 0;

            String title = color(titles.get(titleIndex));
            for (PlayerBoard board : boards.values()) {
                board.setTitle(title);
            }
        }, titleTicks, titleTicks);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        joinTimes.put(player.getUniqueId(), System.currentTimeMillis());

        if (!data.contains("players." + player.getUniqueId() + ".shards")) {
            data.set("players." + player.getUniqueId() + ".shards",
                    getConfig().getLong("shards.starting-balance", 0));
            saveData();
        }

        Bukkit.getScheduler().runTaskLater(this, () -> createBoard(player), 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        addCurrentSessionToPlaytime(uuid);
        joinTimes.remove(uuid);

        PlayerBoard board = boards.remove(uuid);
        if (board != null) board.remove();

        saveData();
    }

    private void addCurrentSessionToPlaytime(UUID uuid) {
        Long joined = joinTimes.get(uuid);
        if (joined == null) return;

        long seconds = Math.max(0, (System.currentTimeMillis() - joined) / 1000);
        String path = "players." + uuid + ".playtime-seconds";
        data.set(path, data.getLong(path, 0) + seconds);
    }

    private void createBoard(Player player) {
        PlayerBoard old = boards.remove(player.getUniqueId());
        if (old != null) old.remove();

        PlayerBoard board = new PlayerBoard(player);
        boards.put(player.getUniqueId(), board);
        board.update(player);

        List<String> titles = getConfig().getStringList("scoreboard.animated-title");
        if (!titles.isEmpty()) board.setTitle(color(titles.get(titleIndex % titles.size())));
    }

    public void reloadPlugin() {
        reloadConfig();
        setupVault();

        List<String> titles = getConfig().getStringList("scoreboard.animated-title");
        if (!titles.isEmpty()) titleIndex %= titles.size();
        else titleIndex = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerBoard board = boards.get(player.getUniqueId());
            if (board != null) board.update(player);
        }
    }

    public long getShards(UUID uuid) {
        return data.getLong("players." + uuid + ".shards",
                getConfig().getLong("shards.starting-balance", 0));
    }

    public void setShards(UUID uuid, long amount) {
        data.set("players." + uuid + ".shards", Math.max(0, amount));
        saveData();
    }

    private String getRank(Player player) {
        if (permission == null) return "N/A";
        try {
            String group = permission.getPrimaryGroup(player);
            if (group != null && !group.isBlank()) return group;
        } catch (Exception ignored) {}
        return "N/A";
    }

    private String getTeam(Player player) {
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());
        return team == null ? "N/A" : team.getName();
    }

    private String getMoney(Player player) {
        if (economy == null) return "N/A";
        try {
            return economy.format(economy.getBalance(player));
        } catch (Exception e) {
            return "N/A";
        }
    }

    private String getPlaytime(Player player) {
        UUID uuid = player.getUniqueId();
        long saved = data.getLong("players." + uuid + ".playtime-seconds", 0);
        long session = joinTimes.containsKey(uuid)
                ? Math.max(0, (System.currentTimeMillis() - joinTimes.get(uuid)) / 1000)
                : 0;

        long total = saved + session;
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        return hours + "h " + minutes + "m";
    }

    public String replacePlaceholders(Player player, String input) {
        int kills = player.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = player.getStatistic(Statistic.DEATHS);

        double tps = Bukkit.getTPS()[0];
        if (tps > 20.0) tps = 20.0;

        return input
                .replace("%player%", player.getName())
                .replace("%rank%", getRank(player))
                .replace("%money%", getMoney(player))
                .replace("%kills%", String.valueOf(kills))
                .replace("%deaths%", String.valueOf(deaths))
                .replace("%shards%", String.valueOf(getShards(player.getUniqueId())))
                .replace("%playtime%", getPlaytime(player))
                .replace("%ping%", String.valueOf(Math.max(0, player.getPing())))
                .replace("%team%", getTeam(player))
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%max_players%", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("%tps%", String.format(Locale.US, "%.1f", tps));
    }

    public static String color(String text) {
        if (text == null) return "";

        Matcher matcher = HEX.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) replacement.append('§').append(c);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(result);

        return ChatColor.translateAlternateColorCodes('&', result.toString());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is for players.");
            return true;
        }

        if (!player.hasPermission("primesmp.scoreboard.use")) {
            player.sendMessage(color(getConfig().getString("messages.no-permission", "&cNo permission.")));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(color(getConfig().getString("messages.usage", "&e/psb <on|off|reload>")));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                createBoard(player);
                player.sendMessage(color(getConfig().getString("messages.enabled")));
            }
            case "off" -> {
                PlayerBoard board = boards.remove(player.getUniqueId());
                if (board != null) board.remove();
                player.sendMessage(color(getConfig().getString("messages.disabled")));
            }
            case "reload" -> {
                if (!player.hasPermission("primesmp.scoreboard.reload")) {
                    player.sendMessage(color(getConfig().getString("messages.no-permission")));
                    return true;
                }
                reloadPlugin();
                player.sendMessage(color(getConfig().getString("messages.reloaded")));
            }
            default -> player.sendMessage(color(getConfig().getString("messages.usage")));
        }

        return true;
    }

    private final class PlayerBoard {
        private final Scoreboard scoreboard;
        private final Objective objective;
        private final List<Team> teams = new ArrayList<>();
        private final List<String> entries = new ArrayList<>();

        PlayerBoard(Player player) {
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

            String title = color("&b&lPRIME SMP");
            objective = scoreboard.registerNewObjective(
                    "prime",
                    Criteria.DUMMY,
                    title
            );
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);

            List<String> configured = getConfig().getStringList("scoreboard.lines");
            int max = Math.min(15, configured.size());

            for (int i = 0; i < max; i++) {
                String entry = ChatColor.COLOR_CHAR + Integer.toHexString(i + 1) + "§r";
                Team team = scoreboard.registerNewTeam("line" + i);
                team.addEntry(entry);
                objective.getScore(entry).setScore(max - i);

                entries.add(entry);
                teams.add(team);
            }

            player.setScoreboard(scoreboard);
        }

        void update(Player player) {
            List<String> configured = getConfig().getStringList("scoreboard.lines");
            int max = Math.min(15, configured.size());

            for (int i = 0; i < max; i++) {
                String line = replacePlaceholders(player, configured.get(i));
                teams.get(i).setPrefix(color(line));
                teams.get(i).setSuffix("");
            }
        }

        void setTitle(String title) {
            objective.displayName(net.kyori.adventure.text.Component.text(title));
        }

        void remove() {
            scoreboard.clearSlot(DisplaySlot.SIDEBAR);
        }
    }
}
