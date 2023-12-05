/*
 * This file is part of InteractionVisualizer.
 *
 * Copyright (C) 2022. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2022. Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.loohp.interactionvisualizer.entities;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.cryptomorin.xseries.XMaterial;
import com.loohp.interactionvisualizer.InteractionVisualizer;
import com.loohp.interactionvisualizer.api.InteractionVisualizerAPI;
import com.loohp.interactionvisualizer.api.InteractionVisualizerAPI.Modules;
import com.loohp.interactionvisualizer.api.VisualizerRunnableDisplay;
import com.loohp.interactionvisualizer.api.events.InteractionVisualizerReloadEvent;
import com.loohp.interactionvisualizer.managers.PlayerLocationManager;
import com.loohp.interactionvisualizer.nms.NMS;
import com.loohp.interactionvisualizer.objectholders.BoundingBox;
import com.loohp.interactionvisualizer.objectholders.EntryKey;
import com.loohp.interactionvisualizer.objectholders.WrappedIterable;
import com.loohp.interactionvisualizer.protocol.WatchableCollection;
import com.loohp.interactionvisualizer.utils.ChatColorUtils;
import com.loohp.interactionvisualizer.utils.ColorUtils;
import com.loohp.interactionvisualizer.utils.ComponentFont;
import com.loohp.interactionvisualizer.utils.JsonUtils;
import com.loohp.interactionvisualizer.utils.LanguageUtils;
import com.loohp.interactionvisualizer.utils.LineOfSightUtils;
import com.loohp.interactionvisualizer.utils.RarityUtils;
import com.loohp.interactionvisualizer.utils.SyncUtils;
import com.loohp.interactionvisualizer.utils.XMaterialUtils;
import io.github.bananapuncher714.nbteditor.NBTEditor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class ItemDisplay extends VisualizerRunnableDisplay implements Listener {

    public static final EntryKey KEY = new EntryKey("item");

    private static final ItemStack AIR = new ItemStack(Material.AIR);

    private final Map<Item, Set<Player>> outOfRangePlayersMap = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Item, WrappedDataWatcher> defaultWatchers = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Item, WrappedDataWatcher> modifiedWatchers = Collections.synchronizedMap(new WeakHashMap<>());

    private String regularFormatting;
    private String singularFormatting;
    private String toolsFormatting;
    private String highColor = "";
    private String mediumColor = "";
    private String lowColor = "";
    private int cramp = 6;
    private int updateRate = 20;
    private boolean stripColorBlacklist;
    private BiPredicate<String, Material> blacklist;

    public ItemDisplay() {
        onReload(new InteractionVisualizerReloadEvent());
    }

    @EventHandler
    public void onReload(InteractionVisualizerReloadEvent event) {
        regularFormatting = ChatColorUtils.translateAlternateColorCodes('&', InteractionVisualizer.plugin.getConfiguration().getString("Entities.Item.Options.RegularFormat"));
        singularFormatting = ChatColorUtils.translateAlternateColorCodes('&', InteractionVisualizer.plugin.getConfiguration().getString("Entities.Item.Options.SingularFormat"));
        toolsFormatting = ChatColorUtils.translateAlternateColorCodes('&', InteractionVisualizer.plugin.getConfiguration().getString("Entities.Item.Options.ToolsFormat"));
        highColor = ChatColorUtils.translateAlternateColorCodes('&', InteractionVisualizer.plugin.getConfiguration().getString("Entities.Item.Options.Color.High"));
        mediumColor = ChatColorUtils.translateAlternateColorCodes('&', InteractionVisualizer.plugin.getConfiguration().getString("Entities.Item.Options.Color.Medium"));
        lowColor = ChatColorUtils.translateAlternateColorCodes('&', InteractionVisualizer.plugin.getConfiguration().getString("Entities.Item.Options.Color.Low"));
        cramp = InteractionVisualizer.plugin.getConfiguration().getInt("Entities.Item.Options.Cramping");
        updateRate = InteractionVisualizer.plugin.getConfiguration().getInt("Entities.Item.Options.UpdateRate");
        stripColorBlacklist = InteractionVisualizer.plugin.getConfiguration().getBoolean("Entities.Item.Options.Blacklist.StripColorWhenMatching");
        blacklist = InteractionVisualizer.plugin.getConfiguration().getList("Entities.Item.Options.Blacklist.List").stream().map(each -> {
            @SuppressWarnings("unchecked")
            List<String> entry = (List<String>) each;
            Pattern pattern = Pattern.compile(entry.get(0));
            Predicate<String> name = str -> pattern.matcher(str).matches();
            Predicate<Material> material;
            if (entry.size() > 1 && !entry.get(1).equals("*")) {
                try {
                    Material m = Material.valueOf(entry.get(1).toUpperCase());
                    material = e -> e.equals(m);
                } catch (Exception er) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[InteractionVisualizer] " + entry.get(1).toUpperCase() + " is not a valid material");
                    material = e -> true;
                }
            } else {
                material = e -> true;
            }
            Predicate<Material> finalmaterial = material;
            return (BiPredicate<String, Material>) (s, m) -> name.test(s) && finalmaterial.test(m);
        }).reduce(BiPredicate::or).orElse((s, m) -> false);
    }

    @Override
    public EntryKey key() {
        return KEY;
    }

    @Override
    public int gc() {
        return -1;
    }

    @Override
    public int run() {
        return new BukkitRunnable() {
            int i = 0;

            @Override
            public void run() {
                if (--i > 0) {
                    return;
                }
                i = updateRate;
                for (World world : Bukkit.getWorlds()) {
                    WrappedIterable<?, Entity> entities = NMS.getInstance().getEntities(world);
                    for (Entity entity : entities) {
                        SyncUtils.runAsyncWithSyncCondition(() -> entity.isValid() && entity instanceof Item, 200, () -> tick((Item) entity, entities));
                    }
                }
            }
        }.runTaskTimer(InteractionVisualizer.plugin, 0, 1).getTaskId();
    }

    private void tick(Item item, WrappedIterable<?, Entity> items) {
        World world = item.getWorld();
        Location location = item.getLocation();
        BoundingBox area = BoundingBox.of(item.getLocation(), 0.5, 0.5, 0.5);
        int ticks = NBTEditor.getShort(item, "Age");

        ItemStack itemstack = item.getItemStack();
        if (itemstack == null) {
            itemstack = new ItemStack(Material.AIR);
        } else {
            itemstack = itemstack.clone();
        }
        Component name = getDisplayName(itemstack);
        String matchingname = getMatchingName(itemstack, stripColorBlacklist);

        if (!blacklist.test(matchingname, itemstack.getType())) {
            if (NBTEditor.getShort(item, "PickupDelay") >= Short.MAX_VALUE || ticks < 0 || isCramping(world, area, items)) {
                PacketContainer defaultPacket = InteractionVisualizer.protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
                defaultPacket.getIntegers().write(0, item.getEntityId());
                WrappedDataWatcher watcher = WatchableCollection.resetCustomNameWatchableCollection(item, defaultWatchers.get(item));
                WatchableCollection.writeMetadataPacket(defaultPacket, watcher);
                defaultWatchers.put(item, watcher);
                Collection<Player> players = InteractionVisualizerAPI.getPlayerModuleList(Modules.HOLOGRAM, KEY);
                for (Player player : players) {
                    InteractionVisualizer.protocolManager.sendServerPacket(player, defaultPacket);
                }
            } else {
                int amount = itemstack.getAmount();
                String durDisplay = null;

                if (itemstack.getType().getMaxDurability() > 0) {
                    @SuppressWarnings("deprecation")
                    int durability = itemstack.getType().getMaxDurability() - (InteractionVisualizer.version.isLegacy() ? itemstack.getDurability() : ((Damageable) itemstack.getItemMeta()).getDamage());
                    int maxDur = itemstack.getType().getMaxDurability();
                    double percentage = ((double) durability / (double) maxDur) * 100;
                    String color;
                    if (percentage > 66.666) {
                        color = highColor;
                    } else if (percentage > 33.333) {
                        color = mediumColor;
                    } else {
                        color = lowColor;
                    }
                    durDisplay = color + durability + "/" + maxDur;
                }

                int despawnRate = NMS.getInstance().getItemDespawnRate(item);
                int ticksLeft = despawnRate - ticks;
                int secondsLeft = ticksLeft / 20;
                String timerColor;
                if (secondsLeft <= 30) {
                    timerColor = lowColor;
                } else if (secondsLeft <= 120) {
                    timerColor = mediumColor;
                } else {
                    timerColor = highColor;
                }

                String timer = timerColor + String.format("%02d:%02d", secondsLeft / 60, secondsLeft % 60);

                Component display;
                String line1;
                if (ticksLeft >= 600 && durDisplay != null) {
                    line1 = toolsFormatting.replace("{Amount}", amount + "").replace("{Timer}", timer).replace("{Durability}", durDisplay);
                } else {
                    if (amount == 1) {
                        line1 = singularFormatting.replace("{Amount}", amount + "").replace("{Timer}", timer);
                    } else {
                        line1 = regularFormatting.replace("{Amount}", amount + "").replace("{Timer}", timer);
                    }
                }
                display = ComponentFont.parseFont(LegacyComponentSerializer.legacySection().deserialize(line1));
                display = display.replaceText(TextReplacementConfig.builder().matchLiteral("{Item}").replacement(name).build());

                WrappedDataWatcher modifiedWatcher = WatchableCollection.createCustomNameWatchableCollection(display, modifiedWatchers.get(item));
                WrappedDataWatcher defaultWatcher = WatchableCollection.resetCustomNameWatchableCollection(item, defaultWatchers.get(item));

                modifiedWatchers.put(item, modifiedWatcher);
                defaultWatchers.put(item, defaultWatcher);

                PacketContainer modifiedPacket = InteractionVisualizer.protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
                PacketContainer defaultPacket = InteractionVisualizer.protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);

                modifiedPacket.getIntegers().write(0, item.getEntityId());
                defaultPacket.getIntegers().write(0, item.getEntityId());

                WatchableCollection.writeMetadataPacket(modifiedPacket, modifiedWatcher);
                WatchableCollection.writeMetadataPacket(defaultPacket, defaultWatcher);

                Location entityCenter = location.clone();
                entityCenter.setY(entityCenter.getY() + item.getHeight() * 1.7);

                Set<Player> outOfRangePlayers;
                synchronized (outOfRangePlayersMap) {
                    outOfRangePlayers = outOfRangePlayersMap.get(item);
                    if (outOfRangePlayers == null) {
                        outOfRangePlayers = ConcurrentHashMap.newKeySet();
                        outOfRangePlayersMap.put(item, outOfRangePlayers);
                    }
                }

                Collection<Player> players = location.getWorld().getPlayers();
                Collection<Player> enabledPlayers = InteractionVisualizerAPI.getPlayerModuleList(Modules.HOLOGRAM, KEY);
                Collection<Player> playersInRange = PlayerLocationManager.filterOutOfRange(players, location, player -> !InteractionVisualizer.hideIfObstructed || LineOfSightUtils.hasLineOfSight(player.getEyeLocation(), entityCenter));
                for (Player player : players) {
                    if (playersInRange.contains(player) && enabledPlayers.contains(player)) {
                        InteractionVisualizer.protocolManager.sendServerPacket(player, modifiedPacket);
                        outOfRangePlayers.remove(player);
                    } else if (!outOfRangePlayers.contains(player)) {
                        InteractionVisualizer.protocolManager.sendServerPacket(player, defaultPacket);
                        outOfRangePlayers.add(player);
                    }
                }
            }
        }
    }

    private Component getDisplayName(ItemStack itemstack) {
        return getDisplayName(itemstack, ChatColor.WHITE);
    }

    private Component getDisplayName(ItemStack itemstack, ChatColor defaultRarityColor) {
        if (itemstack == null) {
            itemstack = AIR.clone();
        }
        XMaterial xMaterial = XMaterialUtils.matchXMaterial(itemstack);
        ChatColor rarityChatColor = RarityUtils.getRarityColor(itemstack);
        if (rarityChatColor.equals(ChatColor.WHITE)) {
            rarityChatColor = defaultRarityColor;
        }
        Component component = Component.empty();
        if (rarityChatColor != null) {
            component = component.color(ColorUtils.toTextColor(rarityChatColor));
        }
        if (!itemstack.getType().equals(Material.AIR) && NBTEditor.contains(itemstack, "display", "Name")) {
            String name = NBTEditor.getString(itemstack, "display", "Name");
            if (!InteractionVisualizer.version.isLegacy()) {
                component = component.decorate(TextDecoration.ITALIC);
            }
            try {
                if (JsonUtils.isValid(name)) {
                    component = component.append(GsonComponentSerializer.gson().deserialize(name));
                } else {
                    component = component.append(LegacyComponentSerializer.legacySection().deserialize(name));
                }
            } catch (Throwable e) {
                component = component.append(LegacyComponentSerializer.legacySection().deserialize(name));
            }
        } else {
            boolean displayNameCompleted = false;
            if (itemstack.hasItemMeta() && itemstack.getItemMeta() instanceof BookMeta) {
                BookMeta meta = (BookMeta) itemstack.getItemMeta();
                String rawTitle = meta.getTitle();
                if (rawTitle != null) {
                    displayNameCompleted = true;
                    component = component.append(LegacyComponentSerializer.legacySection().deserialize(rawTitle));
                }
            }
            if (!displayNameCompleted) {
                TranslatableComponent translatableComponent = Component.translatable(LanguageUtils.getTranslationKey(itemstack));
                if (xMaterial.equals(XMaterial.PLAYER_HEAD)) {
                    String owner = NBTEditor.getString(itemstack, "SkullOwner", "Name");
                    if (owner != null) {
                        translatableComponent = translatableComponent.args(Component.text(owner));
                    }
                }
                component = component.append(translatableComponent);
            }
        }
        return component;
    }

    private String getMatchingName(ItemStack item, boolean stripColor) {
        if (item.hasItemMeta() && item.getItemMeta() != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName() && meta.getDisplayName() != null) {
                if (stripColor) {
                    return ChatColorUtils.stripColor(meta.getDisplayName());
                } else {
                    return meta.getDisplayName();
                }
            }
        }
        return "";
    }

    private boolean isCramping(World world, BoundingBox area, WrappedIterable<?, Entity> items) {
        if (cramp <= 0) {
            return false;
        }
        try {
            return items.stream().filter(each -> each != null && each.getWorld().equals(world) && area.contains(each.getLocation().toVector())).count() > cramp;
        } catch (Throwable e) {
            return false;
        }
    }

}
