package de.minebench.simpleafkkick;

/*
 * SimpleAfkKick
 * Copyright (C) 2022 Max Lee aka Phoenix616 (max@themoep.de)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleAfkKick extends JavaPlugin implements Listener {
    
    private Set<Listener> listeners = new HashSet<>();
    private Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
    
    private Component kickMessage;
    private boolean kickOnActive;
    
    private BukkitTask checkTask = null;
    
    public void onEnable() {
        loadConfig();
        getCommand("simpleafkkick").setExecutor((sender, command, label, args) -> {
            if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
                loadConfig();
                sender.sendMessage(ChatColor.GREEN + "Reloaded the config!");
                return true;
            }
            return false;
        });
        getServer().getPluginManager().registerEvents(this, this);
    }
    
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        updateActive(event.getPlayer());
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastActive.remove(event.getPlayer().getUniqueId());
    }
    
    private void updateActive(HumanEntity human) {
        if (kickOnActive && human instanceof Player player && !player.hasPermission("simpleafkkick.bypass")) {
            player.kick(kickMessage);
        } else {
            lastActive.put(human.getUniqueId(), System.currentTimeMillis());
        }
    }
    
    public void loadConfig() {
        saveDefaultConfig();
        reloadConfig();
        
        unregisterListeners();
        stopTask();

        String rawKickMessage = getConfig().getString("kick-message", "lang:multiplayer.disconnect.idling");
        if (rawKickMessage.startsWith("lang:")) {
            kickMessage = Component.translatable(rawKickMessage.substring("lang:".length()));
        } else {
            kickMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(rawKickMessage);
        }

        kickOnActive = getConfig().getBoolean("kick-on-active");

        if (startTask()) {
            registerListeners();
        }
    }
    
    private boolean startTask() {
        long checkInterval = getConfig().getLong("check-interval");
        if (checkInterval > 0) {
            long afkTime = getConfig().getLong("afk-time");
            checkTask = getServer().getScheduler().runTaskTimer(this, () -> {
                for (Iterator<Map.Entry<UUID, Long>> it = lastActive.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<UUID, Long> entry = it.next();
                    if (entry.getValue() + afkTime * 1000 < System.currentTimeMillis()) {
                        Player player = getServer().getPlayer(entry.getKey());
                        if (player != null) {
                            if (!player.hasPermission("simpleafkkick.bypass") && !kickOnActive) {
                                player.kick(kickMessage);
                                it.remove();
                            }
                        } else {
                            it.remove();
                        }
                    }
                }
            }, checkInterval * 20, checkInterval * 20);
            return true;
        }
        return false;
    }
    
    private void stopTask() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
    }
    
    private static boolean sameOrientation(Location l1, Location l2) {
        return l1.getYaw() == l2.getYaw() && l1.getPitch() == l2.getPitch();
    }
    
    private static boolean sameBlock(Location l1, Location l2) {
        return l1.getWorld() == l2.getWorld()
                && l1.getBlockX() == l2.getBlockX()
                && l1.getBlockY() == l2.getBlockY()
                && l1.getBlockZ() == l2.getBlockZ();
    }
    
    private void registerListener(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
        listeners.add(listener);
    }
    
    private void unregisterListeners() {
        for (Listener listener : listeners) {
            HandlerList.unregisterAll(listener);
        }
        listeners.clear();
    }

    private boolean shouldCheck(String type) {
        return getConfig().getBoolean("check." + type);
    }
    
    private void registerListeners() {
        if (shouldCheck("blocks")) {
            registerListener(new Listener() {
                @EventHandler
                public void onBlockPlace(BlockPlaceEvent event) {
                    updateActive(event.getPlayer());
                }
    
                @EventHandler
                public void onBlockBreak(BlockBreakEvent event) {
                    updateActive(event.getPlayer());
                }
    
                @EventHandler
                public void onBlockDamage(BlockDamageEvent event) {
                    updateActive(event.getPlayer());
                }
            });
        }
        if (shouldCheck("move")) {
            boolean checkOrientation = shouldCheck("orientation");
            registerListener(new Listener() {
                @EventHandler
                public void onMove(PlayerMoveEvent event) {
                    if (!sameBlock(event.getFrom(), event.getTo())) {
                        if (!checkOrientation || !sameOrientation(event.getFrom(), event.getTo())) {
                            updateActive(event.getPlayer());
                        }
                    }
                }
            });
        }
        if (shouldCheck("sneaking")) {
            registerListener(new Listener() {
                @EventHandler
                public void onSneak(PlayerToggleSneakEvent event) {
                    updateActive(event.getPlayer());
                }
            });
        }
        if (shouldCheck("jumping")) {
            registerListener(new Listener() {
                @EventHandler
                public void onJump(PlayerJumpEvent event) {
                    updateActive(event.getPlayer());
                }
            });
        }
        if (shouldCheck("Interact")) {
            registerListener(new Listener() {
                @EventHandler
                public void onInteract(PlayerInteractEvent event) {
                    updateActive(event.getPlayer());
                }
                
                @EventHandler
                public void onInteract(PlayerInteractEntityEvent event) {
                    updateActive(event.getPlayer());
                }
                
                @EventHandler
                public void onInteract(PlayerInteractAtEntityEvent event) {
                    updateActive(event.getPlayer());
                }
    
                @EventHandler
                public void onInteract(PlayerAnimationEvent event) {
                    updateActive(event.getPlayer());
                }
                
    
                @EventHandler
                public void onInteract(PlayerBedLeaveEvent event) {
                    updateActive(event.getPlayer());
                }
            });
        }
        if (shouldCheck("inventory")) {
            registerListener(new Listener() {
                @EventHandler
                public void onInteract(InventoryClickEvent event) {
                    updateActive(event.getWhoClicked());
                }
            
                @EventHandler
                public void onInteract(InventoryCreativeEvent event) {
                    updateActive(event.getWhoClicked());
                }
            
                @EventHandler
                public void onInteract(InventoryDragEvent event) {
                    updateActive(event.getWhoClicked());
                }
    
                @EventHandler
                public void onInteract(InventoryOpenEvent event) {
                    updateActive(event.getPlayer());
                }
    
                @EventHandler
                public void onInteract(InventoryCloseEvent event) {
                    updateActive(event.getPlayer());
                }
    
                @EventHandler
                public void onInteract(PlayerDropItemEvent event) {
                    updateActive(event.getPlayer());
                }
    
                @EventHandler
                public void onInteract(PlayerItemConsumeEvent event) {
                    updateActive(event.getPlayer());
                }
    
                @EventHandler
                public void onInteract(PlayerItemHeldEvent event) {
                    updateActive(event.getPlayer());
                }
            });
        }
        if (shouldCheck("fishing")) {
            registerListener(new Listener() {
                @EventHandler
                public void onFish(PlayerFishEvent event) {
                    updateActive(event.getPlayer());
                }
            });
        }
        if (shouldCheck("commands")) {
            registerListener(new Listener() {
                @EventHandler
                public void onCommand(PlayerCommandPreprocessEvent event) {
                    updateActive(event.getPlayer());
                }
            });
        }
        if (shouldCheck("chat")) {
            registerListener(new Listener() {
                @EventHandler
                public void onChat(AsyncChatEvent event) {
                    updateActive(event.getPlayer());
                }
            });
        }
    }
    
}
