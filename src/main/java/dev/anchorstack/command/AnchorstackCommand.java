package dev.anchorstack.command;

import dev.anchorstack.AnchorstackPlugin;
import dev.anchorstack.loader.Loader;
import dev.anchorstack.loader.LoaderManager;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class AnchorstackCommand implements CommandExecutor, TabCompleter {

    private final AnchorstackPlugin plugin;
    private final LoaderManager manager;

    public AnchorstackCommand(AnchorstackPlugin plugin, LoaderManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.msg("usage"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "list" -> list(sender);
            case "remove" -> remove(sender, args);
            case "admin" -> admin(sender, args);
            case "reload" -> {
                if (!sender.hasPermission("anchorstack.admin")) {
                    sender.sendMessage(plugin.msg("no-permission"));
                    return true;
                }
                plugin.reloadConfig();
                plugin.startTasks();
                sender.sendMessage(plugin.msg("reloaded"));
            }
            default -> sender.sendMessage(plugin.msg("usage"));
        }
        return true;
    }

    private void list(CommandSender sender) {
        if (!(sender instanceof Player player) || !sender.hasPermission("anchorstack.use")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return;
        }
        List<Loader> own = manager.ownedBy(player.getUniqueId());
        if (own.isEmpty()) {
            sender.sendMessage(plugin.msg("list-empty"));
            return;
        }
        for (Loader loader : own) {
            sender.sendMessage(entry(loader));
        }
    }

    private void remove(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || !sender.hasPermission("anchorstack.use")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return;
        }
        Loader loader = args.length > 1 ? manager.findByPrefix(args[1], player.getUniqueId()) : null;
        if (loader == null) {
            sender.sendMessage(plugin.msg("not-found"));
            return;
        }
        manager.remove(loader);
        sender.sendMessage(plugin.msg("removed", "pos", loader.pos(), "reason", plain("reason-manual")));
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("anchorstack.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase() : "list";
        switch (sub) {
            case "list" -> {
                for (Loader loader : manager.all()) {
                    String owner = Bukkit.getOfflinePlayer(loader.owner()).getName();
                    sender.sendMessage(entry(loader).append(net.kyori.adventure.text.Component.text(
                            " " + (owner == null ? loader.owner().toString() : owner))));
                }
            }
            case "tp" -> {
                Loader loader = args.length > 2 ? manager.findByPrefix(args[2], null) : null;
                World world = loader == null ? null : Bukkit.getWorld(loader.world());
                if (loader == null || world == null || !(sender instanceof Player player)) {
                    sender.sendMessage(plugin.msg("not-found"));
                    return;
                }
                player.teleport(new Location(world, loader.x() + 0.5, loader.y() + 3, loader.z() + 0.5));
            }
            case "cleanup" -> sender.sendMessage(net.kyori.adventure.text.Component.text(
                    plugin.cleanup() + " Loader ohne Saeule entfernt."));
            case "remove" -> {
                Loader loader = args.length > 2 ? manager.findByPrefix(args[2], null) : null;
                if (loader == null) {
                    sender.sendMessage(plugin.msg("not-found"));
                    return;
                }
                manager.remove(loader);
                plugin.notifyOwner(loader, "removed", "reason-manual");
                sender.sendMessage(plugin.msg("removed", "pos", loader.pos(), "reason", plain("reason-manual")));
            }
            default -> sender.sendMessage(plugin.msg("usage"));
        }
    }

    private net.kyori.adventure.text.Component entry(Loader loader) {
        World world = Bukkit.getWorld(loader.world());
        return plugin.msg("list-entry", "id", loader.shortId(), "pos", loader.pos(),
                "tier", String.valueOf(loader.tier()), "world", world == null ? "?" : world.getName());
    }

    private String plain(String key) {
        return plugin.getConfig().getString("messages." + key, key);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("anchorstack.admin");
        if (args.length == 1) {
            out.addAll(List.of("list", "remove"));
            if (admin) {
                out.addAll(List.of("admin", "reload"));
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && admin) {
            out.addAll(List.of("list", "tp", "remove", "cleanup"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove") && sender instanceof Player player) {
            manager.ownedBy(player.getUniqueId()).forEach(l -> out.add(l.shortId()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && admin) {
            manager.all().forEach(l -> out.add(l.shortId()));
        }
        String last = args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.startsWith(last));
        return out;
    }
}
