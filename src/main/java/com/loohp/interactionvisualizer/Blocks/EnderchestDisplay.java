package com.loohp.interactionvisualizer.Blocks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.loohp.interactionvisualizer.InteractionVisualizer;
import com.loohp.interactionvisualizer.API.InteractionVisualizerAPI;
import com.loohp.interactionvisualizer.API.InteractionVisualizerAPI.Modules;
import com.loohp.interactionvisualizer.EntityHolders.Item;
import com.loohp.interactionvisualizer.Managers.PacketManager;
import com.loohp.interactionvisualizer.Utils.InventoryUtils;
import com.loohp.interactionvisualizer.Utils.MCVersion;
import com.loohp.interactionvisualizer.Utils.MaterialUtils;
import com.loohp.interactionvisualizer.Utils.OpenInvUtils;
import com.loohp.interactionvisualizer.Utils.VanishUtils;

public class EnderchestDisplay implements Listener {
	
	public static ConcurrentHashMap<Player, List<Item>> link = new ConcurrentHashMap<Player, List<Item>>();
	public static ConcurrentHashMap<Player, Block> playermap = new ConcurrentHashMap<Player, Block>();
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void onOpenEnderChest(InventoryOpenEvent event) {
		if (event.isCancelled()) {	
			return;
		}
		if (VanishUtils.isVanished((Player) event.getPlayer())) {
			return;
		}
		if (OpenInvUtils.isSlientChest((Player) event.getPlayer())) {
			return;
		}
		if (event.getPlayer().getGameMode().equals(GameMode.SPECTATOR)) {
			return;
		}
		if (event.getView().getTopInventory() == null) {
			return;
		}
		if (!event.getView().getTopInventory().getType().equals(InventoryType.ENDER_CHEST)) {
			return;
		}
		if (!InventoryUtils.toBase64(event.getPlayer().getEnderChest()).equals(InventoryUtils.toBase64(event.getView().getTopInventory()))) {
			return;
		}
		if (!InteractionVisualizer.version.isLegacy() && !InteractionVisualizer.version.equals(MCVersion.V1_13) && !InteractionVisualizer.version.equals(MCVersion.V1_13_1)) {
			if (event.getPlayer().getTargetBlockExact(7, FluidCollisionMode.NEVER) != null) {
				if (!event.getPlayer().getTargetBlockExact(7, FluidCollisionMode.NEVER).getType().equals(Material.ENDER_CHEST)) {
					return;
				}
			} else {
				return;
			}
		} else {
			if (event.getPlayer().getTargetBlock(MaterialUtils.getNonSolidSet(), 7) != null) {
				if (!event.getPlayer().getTargetBlock(MaterialUtils.getNonSolidSet(), 7).getType().equals(Material.ENDER_CHEST)) {
					return;
				}
			} else {
				return;
			}
		}
		
		Block block = null;
		if (!InteractionVisualizer.version.isLegacy() && !InteractionVisualizer.version.equals(MCVersion.V1_13) && !InteractionVisualizer.version.equals(MCVersion.V1_13_1)) {
			block = event.getPlayer().getTargetBlockExact(7, FluidCollisionMode.NEVER);
		} else {
			block = event.getPlayer().getTargetBlock(MaterialUtils.getNonSolidSet(), 7);
		}
		
		playermap.put((Player) event.getPlayer(), block);
	}
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void onUseEnderChest(InventoryClickEvent event) {
		Player player = (Player) event.getWhoClicked();
		if (event.isCancelled()) {
			return;
		}
		if (!playermap.containsKey(player)) {
			return;
		}
		if (event.getClick().equals(ClickType.MIDDLE) && !event.getWhoClicked().getGameMode().equals(GameMode.CREATIVE)) {
			return;
		}
		
		Block block = playermap.get(player);
		Location loc = block.getLocation();
		
		boolean isIn = true;
		boolean isMove = false;
		ItemStack itemstack = null;
		
		if (event.getRawSlot() >= 0 && event.getRawSlot() <= 26) {
			
			itemstack = event.getCurrentItem();
			if (itemstack != null) {
				if (itemstack.getType().equals(Material.AIR)) {
					itemstack = null;
				} else {
					isIn = false;
					isMove = true;
				}
			}
			if (itemstack == null) {
				itemstack = event.getCursor();
				if (itemstack != null) {
					if (itemstack.getType().equals(Material.AIR)) {
						itemstack = null;
					} else {
						isMove = true;
					}
				}
			} else {
				if (event.getCursor() != null) {
					if (event.getCursor().getType().equals(itemstack.getType())) {
						isIn = true;
					}
				}
			}
			if (itemstack == null) {
				if (event.getAction().equals(InventoryAction.HOTBAR_MOVE_AND_READD) || event.getAction().equals(InventoryAction.HOTBAR_SWAP)) {
					itemstack = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
					if (itemstack != null) {
						if (itemstack.getType().equals(Material.AIR)) {
							itemstack = null;
						} else {
							isMove = true;
						}
					}
				}
			}		
		}
		
		if (itemstack == null) {
			if (event.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
				itemstack = event.getCurrentItem();
				if (itemstack != null) {
					if (itemstack.getType().equals(Material.AIR)) {
						itemstack = null;
					} else {
						isMove = true;
					}
				}
			}
		}
		
		if (event.isShiftClick() && event.getView().getItem(event.getRawSlot()) != null) {
			if (isIn) {
				if (!InventoryUtils.stillHaveSpace(event.getView().getTopInventory(), event.getView().getItem(event.getRawSlot()).getType())) {
					return;
				}
			} else {
				if (!InventoryUtils.stillHaveSpace(event.getWhoClicked().getInventory(), event.getView().getItem(event.getRawSlot()).getType())) {
					return;
				}
			}
		}
		if (event.getCursor() != null) {
			if (!event.getCursor().getType().equals(Material.AIR)) {
				if (event.getCurrentItem() != null) {
					if (!event.getCurrentItem().getType().equals(Material.AIR)) {
						if (event.getCurrentItem().getType().equals(event.getCursor().getType())) {
							if (event.getCurrentItem().getAmount() >= event.getCurrentItem().getType().getMaxStackSize()) {
								return;
							}
						}
					}
				}
			}
		}
		
		if (isMove == true) {
			PacketManager.sendHandMovement(InteractionVisualizerAPI.getPlayers(), player);
			if (itemstack != null) {
				Item item = new Item(loc.clone().add(0.5, 1, 0.5));
				Vector offset = new Vector(0.0, 0.15, 0.0);
				Vector vector = loc.clone().add(0.5, 1, 0.5).toVector().subtract(event.getWhoClicked().getEyeLocation().clone().add(0.0, -0.5, 0.0).add(0.0, InteractionVisualizer.playerPickupYOffset, 0.0).toVector()).multiply(-0.15).add(offset);
				item.setVelocity(vector);
				if (isIn) {
					item.teleport(event.getWhoClicked().getEyeLocation().add(0.0, InteractionVisualizer.playerPickupYOffset, 0.0));
					vector = loc.clone().add(0.5, 1, 0.5).toVector().subtract(event.getWhoClicked().getEyeLocation().clone().add(0.0, InteractionVisualizer.playerPickupYOffset, 0.0).toVector()).multiply(0.15).add(offset);
					item.setVelocity(vector);
				}
				PacketManager.sendItemSpawn(InteractionVisualizerAPI.getPlayerModuleList(Modules.ITEMDROP), item);
				item.setItemStack(itemstack);
				item.setPickupDelay(32767);
				item.setGravity(true);
				PacketManager.updateItem(item);
				if (!link.containsKey(player)) {
					link.put(player, new ArrayList<Item>());
				}
				List<Item> list = link.get(player);
				list.add(item);
				boolean finalIsIn = isIn;
				Bukkit.getScheduler().runTaskLater(InteractionVisualizer.plugin, () -> {
					if (finalIsIn) {
						item.teleport(loc.clone().add(0.5, 1, 0.5));
					} else {
						item.teleport(event.getWhoClicked().getEyeLocation().add(0.0, -0.5, 0.0).add(0.0, InteractionVisualizer.playerPickupYOffset, 0.0));
					}
					item.setVelocity(new Vector(0.0, 0.0, 0.0));
					item.setGravity(false);
					PacketManager.updateItem(item);
				}, 8);
				Bukkit.getScheduler().runTaskLater(InteractionVisualizer.plugin, () -> {
					PacketManager.removeItem(InteractionVisualizerAPI.getPlayers(), item);
					list.remove(item);
				}, 20);
			}						
		}
	}
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void onDragEnderChest(InventoryDragEvent event) {
		Player player = (Player) event.getWhoClicked();
		if (event.isCancelled()) {
			return;
		}
		if (!playermap.containsKey(player)) {
			return;
		}
		
		boolean ok = false;
		for (Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
			ItemStack item = event.getView().getItem(entry.getKey());
			if (item == null) {
				ok = true;
				break;
			}
			if (item.getType().equals(Material.AIR)) {
				ok = true;
				break;
			}
			if (!item.getType().equals(entry.getValue().getType())) {
				continue;
			}
			if (item.getAmount() < item.getType().getMaxStackSize()) {
				ok = true;
				break;
			}
		}
		if (!ok) {
			return;
		}
		
		Block block = playermap.get(player);
		Location loc = block.getLocation();
		
		for (int slot : event.getRawSlots()) {
			if (slot >= 0 && slot <= 26) {
				PacketManager.sendHandMovement(InteractionVisualizerAPI.getPlayers(), player);
				
				ItemStack itemstack = event.getOldCursor();
				if (itemstack != null) {
					if (itemstack.getType().equals(Material.AIR)) {
						itemstack = null;
					}
				}
				
				if (itemstack != null) {
					Item item = new Item(event.getWhoClicked().getEyeLocation().add(0.0, InteractionVisualizer.playerPickupYOffset, 0.0));
					Vector offset = new Vector(0.0, 0.15, 0.0);
					Vector vector = loc.clone().add(0.5, 1, 0.5).toVector().subtract(event.getWhoClicked().getEyeLocation().clone().add(0.0, InteractionVisualizer.playerPickupYOffset, 0.0).toVector()).multiply(0.15).add(offset);
					item.setVelocity(vector);
					PacketManager.sendItemSpawn(InteractionVisualizerAPI.getPlayerModuleList(Modules.ITEMDROP), item);
					item.setItemStack(itemstack);
					item.setCustomName(System.currentTimeMillis() + "");
					item.setPickupDelay(32767);
					item.setGravity(true);
					PacketManager.updateItem(item);
					if (!link.containsKey(player)) {
						link.put(player, new ArrayList<Item>());
					}
					List<Item> list = link.get(player);
					list.add(item);
					Bukkit.getScheduler().runTaskLater(InteractionVisualizer.plugin, () -> {
						item.teleport(loc.clone().add(0.5, 1, 0.5));
						item.setVelocity(new Vector(0.0, 0.0, 0.0));
						item.setGravity(false);
						PacketManager.updateItem(item);
					}, 8);
					Bukkit.getScheduler().runTaskLater(InteractionVisualizer.plugin, () -> {
						PacketManager.removeItem(InteractionVisualizerAPI.getPlayers(), item);
						list.remove(item);
					}, 20);
				}
				break;
			}
		}
	}
	
	@EventHandler
	public void onCloseEnderChest(InventoryCloseEvent event) {
		
		Player player = (Player) event.getPlayer();
		
		if (!playermap.containsKey(player)) {
			return;
		}
		
		List<Item> list = link.get(player);
		if (list != null) {
			Iterator<Item> itr = list.iterator();
			while (itr.hasNext()) {
				Item item = itr.next();
				PacketManager.removeItem(InteractionVisualizerAPI.getPlayers(), item);
			}
			link.remove(player);
		}
		
		playermap.remove(player);
	}
}
