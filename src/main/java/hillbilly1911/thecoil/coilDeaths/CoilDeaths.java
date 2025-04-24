package hillbilly1911.thecoil.coilDeaths;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CoilDeaths extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<String, Location> deathLocations = new HashMap<>();
    private final Map<UUID, String> teleportConfirmations = new HashMap<>();
    private File logFile;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("dc").setExecutor(this);
        setupLogFile();

        getLogger().info("CoilDeaths plugin with teleport logs has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("CoilDeaths plugin has been disabled!");
    }

    private void setupLogFile() {
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();

            logFile = new File(getDataFolder(), "teleport_logs.txt");
            if (!logFile.exists()) logFile.createNewFile();
        } catch (IOException e) {
            getLogger().severe("Error creating log file: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location deathLocation = player.getLocation();
        deathLocations.put(player.getName(), deathLocation);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player staff = (Player) sender;
        UUID staffUUID = staff.getUniqueId();

        if (!staff.hasPermission("coildeaths.teleport")) {
            staff.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
            handleTeleportRequest(staff, args[1]);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("confirm")) {
            handleTeleportConfirm(staff);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
            handleTeleportCancel(staff);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("logs")) {
            handleViewLogs(staff);
            return true;
        }

        staff.sendMessage("§cUsage: /dc tp <playername>, /dc confirm, /dc cancel, or /dc logs");
        return true;
    }

    private void handleTeleportRequest(Player staff, String targetName) {
        if (!deathLocations.containsKey(targetName)) {
            staff.sendMessage("§cNo death location found for player: " + targetName);
            return;
        }

        Location location = deathLocations.get(targetName);
        teleportConfirmations.put(staff.getUniqueId(), targetName);

        staff.sendMessage(String.format(
                "§eAre you sure you want to teleport to §a%s's §edeath location? " +
                        "Coordinates: X:§b%.2f§e, Y:§b%.2f§e, Z:§b%.2f§e. " +
                        "Type §b/dc confirm §eor §c/dc cancel §ewithin 30 seconds.",
                targetName, location.getX(), location.getY(), location.getZ()
        ));

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (teleportConfirmations.containsKey(staff.getUniqueId())) {
                teleportConfirmations.remove(staff.getUniqueId());
                staff.sendMessage("§cYour teleport request has expired.");
            }
        }, 600L);
    }

    private void handleTeleportConfirm(Player staff) {
        UUID staffUUID = staff.getUniqueId();

        if (!teleportConfirmations.containsKey(staffUUID)) {
            staff.sendMessage("§cNo pending teleport request to confirm.");
            return;
        }

        String targetName = teleportConfirmations.remove(staffUUID);
        Location location = deathLocations.get(targetName);

        if (location == null) {
            staff.sendMessage("§cDeath location data has been removed or is unavailable.");
            return;
        }

        staff.teleport(location);
        staff.sendMessage("§aTeleported to " + targetName + "'s death location!");
        logTeleport(staff.getName(), targetName, location);
    }

    private void handleTeleportCancel(Player staff) {
        if (teleportConfirmations.remove(staff.getUniqueId()) != null) {
            staff.sendMessage("§cTeleport request has been canceled.");
        } else {
            staff.sendMessage("§cNo pending teleport request to cancel.");
        }
    }

    private void handleViewLogs(Player staff) {
        if (!staff.hasPermission("coildeaths.logs")) {
            staff.sendMessage("§cYou do not have permission to view logs.");
            return;
        }

        staff.sendMessage("§eRecent Teleport Logs:");

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            List<String> lines = new ArrayList<>();
            String line;

            while ((line = reader.readLine()) != null) lines.add(line);

            int logsToShow = Math.min(lines.size(), 5);
            for (int i = lines.size() - logsToShow; i < lines.size(); i++) {
                staff.sendMessage("§b" + lines.get(i));
            }

        } catch (IOException e) {
            staff.sendMessage("§cError reading log file.");
            getLogger().severe("Error reading log file: " + e.getMessage());
        }
    }

    private void logTeleport(String staffName, String targetName, Location location) {
        String logMessage = String.format("[%s] %s teleported to %s's death location at X:%.2f Y:%.2f Z:%.2f",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                staffName, targetName, location.getX(), location.getY(), location.getZ());

        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(logMessage + "\n");
        } catch (IOException e) {
            getLogger().severe("Error writing to log file: " + e.getMessage());
        }
    }
}