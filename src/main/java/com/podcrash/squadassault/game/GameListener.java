package com.podcrash.squadassault.game;

import com.podcrash.squadassault.Main;
import com.podcrash.squadassault.game.events.GunDamageEvent;
import com.podcrash.squadassault.nms.NmsUtils;
import com.podcrash.squadassault.shop.ItemType;
import com.podcrash.squadassault.shop.PlayerShopItem;
import com.podcrash.squadassault.util.ItemBuilder;
import com.podcrash.squadassault.util.Messages;
import com.podcrash.squadassault.util.Utils;
import com.podcrash.squadassault.weapons.Grenade;
import com.podcrash.squadassault.weapons.GrenadeType;
import com.podcrash.squadassault.weapons.Gun;
import com.podcrash.squadassault.weapons.ProjectileStats;
import net.jafama.FastMath;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("unused")
public class GameListener implements Listener {

    private final Inventory selector;
    private final ConcurrentMap<SAGame, Boolean> bombPlants;
    private final Map<Player, Long> clickMap;

    public SAGame getGame() {
        return game;
    }

    public void setGame(SAGame game) {
        this.game = game;
    }

    private SAGame game;

    public GameListener(SAGame game) {
        this.game = game;
        selector = Bukkit.createInventory(null, 27, "Team Selector");
        selector.setItem(11, ItemBuilder.create(Material.WOOL, 1, (short)14, "Team A", "Click to join Team A"));
        selector.setItem(13, ItemBuilder.create(Material.WOOL, 1, (short)8, "Random", "Click to join a random team"));
        selector.setItem(15, ItemBuilder.create(Material.WOOL, 1, (short)10, "Team B", "Click to join Team B"));
        bombPlants = new ConcurrentHashMap<>();
        clickMap = new HashMap<>();
    }

    private boolean checkClick(Player player) {
        if(!clickMap.containsKey(player)) {
            clickMap.put(player, System.currentTimeMillis());
            return true;
        }
        boolean ret = System.currentTimeMillis() - clickMap.get(player) >= 150;
        if(ret) {
            clickMap.put(player, System.currentTimeMillis());
        }
        return ret;
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onIgnite(BlockIgniteEvent blockIgniteEvent) {
        if (blockIgniteEvent.getCause() == BlockIgniteEvent.IgniteCause.SPREAD) {
            blockIgniteEvent.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        SAGame game = Main.getGameManager().getGame(player);
        if(game == null) {
            return;
        }
        if(event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if(game.getState() == SAGameState.WAITING) {
                if(player.getInventory().getItemInHand() != null) {
                    if(player.getInventory().getItemInHand().getType() == Material.DIAMOND) {
                        player.openInventory(selector);
                    } else if(player.getInventory().getItemInHand().getType() == Material.LEATHER) {
                        event.setCancelled(true);
                        Main.getGameManager().removePlayer(game, player, false, false);
                        game.sendToAll(Messages.PLAYER_LEAVE.replace("%p%", player.getDisplayName()));
                        player.sendMessage(ChatColor.AQUA + "You left the game");
                    }
                }
            } else if(game.getState() == SAGameState.ROUND_LIVE || game.getState() == SAGameState.ROUND_START) {
                ItemStack inHand = player.getItemInHand();
                if(inHand != null && inHand.getType() == Material.GHAST_TEAR) {
                    if(game.isAtSpawn(player)) {
                        if(game.getTimer() > 85 || game.getState() == SAGameState.ROUND_START) {
                            player.openInventory(game.getShops().get(player.getUniqueId()));
                        } else {
                            player.sendMessage(Messages.PLAYER_SHOP_DENIED_ELAPSED.toString());
                        }
                    } else {
                        player.sendMessage(Messages.PLAYER_SHOP_DENIED_OUTOFBOUNDS.toString());
                    }
                    return;
                }
                if(inHand != null && inHand.getType() != Material.AIR && game.getState() == SAGameState.ROUND_LIVE) {
                    if((inHand.getType() == Material.SHEARS || inHand.getType() == Material.GOLD_NUGGET) && event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.DAYLIGHT_DETECTOR) {
                        addDefuse(event, player, game, inHand);
                    }
                    if((inHand.getType() == Material.SHEARS || inHand.getType() == Material.GOLD_NUGGET) && event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.CROPS) {
                        addDefuse(event, player, game, inHand);
                    }
                    if(inHand.getType() == Material.GOLDEN_APPLE && game.getState() == SAGameState.ROUND_LIVE && !game.isRoundEnding() && bombPlants.get(game) == null) {
                        event.setCancelled(true);
                        bombPlants.put(game, true);
                        player.getLocation().getWorld().playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 3f);
                        new BukkitRunnable() {
                            public void run() {
                                if(!game.isAtBombsite(player.getLocation())) {
                                    bombPlants.remove(game);
                                    player.sendMessage(ChatColor.AQUA + "You are not at the bombsite!");
                                    cancel();
                                    return;
                                }
                                Block block = player.getLocation().getBlock();
                                if(block.getType() == Material.AIR) {
                                    player.getInventory().setItem(7, ItemBuilder.create(Material.COMPASS, 1, "Bomb Locator", false));
                                    block.setType(Material.DAYLIGHT_DETECTOR);
                                    game.getBomb().setLocation(block.getLocation());
                                    game.getBomb().setTimer(40);
                                    game.getBomb().setPlanted(true);
                                    game.setGameTimer(game.getBomb().getTimer());
                                    game.setMoney(player, game.getMoney(player)+300);
                                    game.getStats().get(player.getUniqueId()).addBombPlants(1);
                                    for(Player omega : Main.getGameManager().getTeam(game, SATeam.Team.OMEGA).getPlayers()) {
                                        omega.setCompassTarget(game.getBomb().getLocation());
                                        //todo play sound
                                        NmsUtils.sendTitle(omega,0,23,0,"",ChatColor.AQUA + "Bomb Planted");
                                    }
                                    for(Player alpha : Main.getGameManager().getTeam(game, SATeam.Team.ALPHA).getPlayers()) {
                                        //todo play sound
                                        NmsUtils.sendTitle(alpha,0,23,0,"",ChatColor.AQUA + "Bomb Planted");
                                    }
                                } else {
                                    player.sendMessage(ChatColor.AQUA + "You must be on the ground/not a half-slab to plant!");
                                }
                                bombPlants.remove(game);
                            }
                        }.runTaskLater(Main.getInstance(), 60);
                        return;
                    }
                }
                Gun gun = Main.getWeaponManager().getGun(inHand);
                if(checkClick(player) && gun != null && !game.isDefusing(player)) {
                    gun.shoot(game, player);
                }
                Grenade grenade = Main.getWeaponManager().getGrenade(inHand);
                if (grenade != null && !game.isRoundEnding() && !game.isDefusing(player) && game.getState() != SAGameState.ROUND_START) {
                    event.setCancelled(true);
                    grenade.throwGrenade(game, player);
                }
            }
        } else if(event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            ItemStack inHand = player.getItemInHand();
            if(inHand != null && inHand.getType() != Material.AIR) {
                Gun gun = Main.getWeaponManager().getGun(inHand);
                if(gun != null) {
                    gun.reload(player, player.getInventory().getHeldItemSlot(), player.getItemInHand().getAmount());
                    return;
                }
                Grenade grenade = Main.getWeaponManager().getGrenade(inHand);
                if(grenade != null && game.getState() != SAGameState.ROUND_START) {
                    grenade.roll(game, player);
                }
            }
        }
    }

    private void addDefuse(PlayerInteractEvent event, Player player, SAGame game, ItemStack inHand) {
        event.setCancelled(true);
        if(Main.getGameManager().getTeam(game, SATeam.Team.ALPHA).getPlayers().contains(player) && !game.isDefusing(player) && player.getLocation().distance(game.getBomb().getLocation()) <= 3) {
            game.addDefuser(player, (inHand.getType() == Material.SHEARS ? 5 : 10));
            player.getLocation().getWorld().playSound(player.getLocation(), Sound.HORSE_ARMOR, 1.3f, 1f);
        }
    }

    @EventHandler
    public void onKnifeDamage(EntityDamageByEntityEvent event) {
        if(event.getEntity().getType() == EntityType.ITEM_FRAME && event.getDamager() instanceof Player && Main.getGameManager().getGame((Player) event.getDamager()) != null) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }
        Player damaged = (Player) event.getEntity();
        Player damager = (Player) event.getDamager();
        SAGame game = Main.getGameManager().getGame(damaged);
        if(game == null || damaged == null || damager == null) {
            return;
        }
        event.setCancelled(true);
        if(game.getState() == SAGameState.ROUND_LIVE && !game.sameTeam(damaged, damager) && damager.getInventory().getHeldItemSlot() == 2 && damager.getInventory().getItemInHand() != null && Main.getUpdateTask().getDelay().get(damaged.getUniqueId()) == null && !game.isDead(damaged)) {
            float angle =
                    damager.getEyeLocation().toVector().subtract(damaged.getEyeLocation().toVector()).angle(damaged.getEyeLocation().getDirection().normalize());
            //check if they are behind player or not
            if(damager.getLocation().distance(damaged.getLocation()) <= 1.7 || angle <= 1.5) {
                Main.getGameManager().damage(game, damager, damaged, 6, "Knife");
            } else {
                Main.getGameManager().damage(game, damager, damaged, 20, "Knife Backstab");
            }
            Main.getUpdateTask().getDelay().put(damaged.getUniqueId(), 17);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        SAGame game = Main.getGameManager().getGame(player);
        if(game == null) {
            for(SAGame saGame : Main.getGameManager().getGames()) {
                event.getRecipients().removeAll(saGame.getTeamA().getPlayers());
                event.getRecipients().removeAll(saGame.getTeamB().getPlayers());
            }
            return;
        }
        if(!event.getMessage().startsWith("#")) {
            event.setFormat(Messages.GENERAL_CHAT_FORMAT.replace("%p%",
                    Main.getGameManager().getTeam(game, player).getColor() + player.getDisplayName()));
            return;
        }
        event.getRecipients().clear();
        if(Main.getGameManager().getTeam(game, player) == SATeam.Team.ALPHA) {
            event.getRecipients().addAll(Main.getGameManager().getTeam(game, SATeam.Team.ALPHA).getPlayers());
            event.setMessage(event.getMessage().substring(1));
            event.setFormat(Messages.TEAM_CHAT_FORMAT.replace("%p%",ChatColor.AQUA + player.getDisplayName()));
        }
        if(Main.getGameManager().getTeam(game, player) == SATeam.Team.OMEGA) {
            event.getRecipients().addAll(Main.getGameManager().getTeam(game, SATeam.Team.OMEGA).getPlayers());
            event.setMessage(event.getMessage().substring(1));
            event.setFormat(Messages.TEAM_CHAT_FORMAT.replace("%p%",ChatColor.RED + player.getDisplayName()));
        }
    }

    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        SAGame game = Main.getGameManager().getGame(player);
        if (game == null || game.getState() == SAGameState.WAITING) {
            return;
        }
        Gun gun = Main.getWeaponManager().getGun(player.getInventory().getItem(event.getPreviousSlot()));
        if(gun != null) {
            gun.resetPlayer(player);
            player.getInventory().getItem(event.getPreviousSlot()).setDurability((short) 0);
        }
        Gun gun2 = Main.getWeaponManager().getGun(player.getItemInHand());
        if(gun2 != null && gun2.hasScope() && player.isSneaking()) {
            event.setCancelled(true);
        }
        if(!event.isCancelled()) {
            player.setWalkSpeed(0.2f);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (Main.getGameManager().getGame(event.getPlayer()) != null && event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if(Main.getGameManager().getGame(event.getPlayer()) == null) {
            return;
        }
        if(event.getRightClicked().getType() == EntityType.ARMOR_STAND) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        SAGame game = Main.getGameManager().getGame(player);
        if(game == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getSlotType() == InventoryType.SlotType.OUTSIDE) {
            return;
        }
        //team selection
        if(event.getClickedInventory().equals(selector)) {
            if(event.getSlot() == 11) {
                game.addTeamA(player);
                player.sendMessage(Messages.PLAYER_SELECT_TEAM.replace("%t%", "A"));
                player.closeInventory();
            } else if (event.getSlot() == 13) {
                game.randomTeam(player);
                player.sendMessage(Messages.PLAYER_SELECT_TEAM_RANDOM.replace("%t%",
                        (game.getTeamA().getPlayers().contains(player) ? "A" : "B")));
                player.closeInventory();
            } else if (event.getSlot() == 15) {
                game.addTeamB(player);
                player.sendMessage(Messages.PLAYER_SELECT_TEAM.replace("%t%", "B"));
                player.closeInventory();
            }
        }
        //buying stuff
        if(event.getClickedInventory().getName().equals("Shop")) {
            for(PlayerShopItem shop : Main.getShopManager().getShops()) {
                if(event.getSlot() == shop.getShopSlot() && (shop.getTeam() == null || Main.getGameManager().getTeam(game, player) == shop.getTeam())) {
                    if(game.getMoney(player) != null && shop.getPrice() > game.getMoney(player)) {
                        player.closeInventory();
                        player.sendMessage(Messages.PLAYER_SHOP_DENIED_FUNDS.toString());
                        break;
                    }
                    if(shop.getType() == ItemType.GRENADE) {
                        Grenade grenade = Main.getWeaponManager().getGrenade(shop.getWeaponName());
                        GrenadeType type = grenade.getType();
                        int max = type.getMax();
                        int current = 0;
                        for(int i = 3; i < 8; i++) {
                            if(Main.getWeaponManager().getGrenade(player.getInventory().getItem(i)) != null && Main.getWeaponManager().getGrenade(player.getInventory().getItem(i)).getType() == type) {
                                current++;
                            }
                        }
                        if(current == max) {
                            player.closeInventory();
                            player.sendMessage(ChatColor.AQUA + "You already have the maximum amount of that grenade!");
                            break;
                        }
                        int desiredSlot = findNadeSlot(player);
                        if(desiredSlot != -1) {
                            game.setMoney(player, game.getMoney(player)-shop.getPrice());
                            player.getInventory().setItem(desiredSlot,
                                    ItemBuilder.create(grenade.getItemWrapper().getType(), 1,
                                            grenade.getItemWrapper().getData(), grenade.getItemWrapper().getName()));
                            break;
                        }
                        player.closeInventory();
                        player.sendMessage(ChatColor.AQUA + "Your slots are full!");
                        break;
                    } else if(shop.getType() == ItemType.GUN) {
                        if(shop.getTeam() != null && Main.getGameManager().getTeam(game, player) != shop.getTeam()) {
                            break;
                        }
                        Gun gun = Main.getWeaponManager().getGun(shop.getWeaponName());
                        if(player.getInventory().getItem(gun.getType().ordinal()) != null) {
                            ItemStack oldStack = player.getInventory().getItem(gun.getType().ordinal());

                            gun.resetDelay(player);
                            ItemStack newStack = ItemBuilder.create(oldStack.getType(), 1, gun.getItemWrapper().getData(),
                                    oldStack.getItemMeta().getDisplayName(),
                                    oldStack.getItemMeta().getLore().toArray(new String[0]));
                            newStack = Utils.setReserveAmmo(newStack, Utils.getReserveAmmo(oldStack));
                            newStack.setAmount(1);
                            NmsUtils.addNBTInteger(newStack, "outofammo", NmsUtils.getNBTInteger(oldStack, "outofammo"));
                            player.getInventory().setItem(gun.getType().ordinal(), null);
                            game.getDrops().put(player.getWorld().dropItemNaturally(player.getLocation(), newStack), oldStack.getAmount());
                            if(gun.hasScope()) {
                                NmsUtils.sendFakeItem(player, 5, player.getInventory().getHelmet());
                            }
                        }

                        game.setMoney(player, game.getMoney(player) - shop.getPrice());
                        ItemStack stack = ItemBuilder.create(gun.getItemWrapper().getType(), gun.getMagSize(),
                                gun.getItemWrapper().getData(), gun.getItemWrapper().getName(), shop.getLore());
                        stack = Utils.setReserveAmmo(stack, gun.getTotalAmmoSize());
                        NmsUtils.addNBTInteger(stack, "outofammo", 0);
                        player.getInventory().setItem(gun.getType().ordinal(),
                                stack);
                        break;
                    } else {
                        if(shop.getTeam() != null && Main.getGameManager().getTeam(game, player) != shop.getTeam()) {
                            break;
                        }
                        //buying armor
                        if(shop.getMaterial() != Material.SHEARS) {
                            ItemStack itemStack = player.getInventory().getItem(shop.getHotbarSlot());
                            if(shop.getHotbarSlot() == 2 || itemStack == null || itemStack.getType() == Material.LEATHER_HELMET || itemStack.getType() == Material.LEATHER_CHESTPLATE) {
                                if((shop.getMaterial() == Material.IRON_HELMET || shop.getMaterial() == Material.CHAINMAIL_HELMET) && player.getInventory().getChestplate().getType() == Material.LEATHER_CHESTPLATE) {
                                    player.sendMessage(Messages.PLAYER_SHOP_DENIED_ARMOR.toString());
                                    break;
                                }
                                game.setMoney(player, game.getMoney(player) - shop.getPrice());
                                player.getInventory().setItem(shop.getHotbarSlot(),
                                        ItemBuilder.create(shop.getMaterial(), 1, shop.getName(), false));
                                break;
                            }
                        } else { //buying defuse kit
                            if(player.getInventory().getItem(shop.getHotbarSlot()).getType() != Material.SHEARS) {
                                game.setMoney(player, game.getMoney(player) - shop.getPrice());
                                player.getInventory().setItem(shop.getHotbarSlot(),
                                        ItemBuilder.create(shop.getMaterial(), 1, shop.getName(), false));
                                break;
                            }
                        }
                        player.closeInventory();
                        player.sendMessage(ChatColor.AQUA + "You already have that!");
                        break;
                    }
                }
            }
        }
    }

    private int findNadeSlot(Player player) {
        for(int i = 3; i < 8; i++) {
            if(player.getInventory().getItem(i) == null) {
                return i;
            }
        }
        return -1;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if(Main.getGameManager().getGame(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if(Main.getGameManager().getGame(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        //TODO: When we make this work properly with bungeecord, lots has to be changed probably
        if(Main.getGameManager().getGame(event.getPlayer()) == null) {
            for(SAGame game : Main.getGameManager().getGames()) {
                for (Player player : game.getTeamA().getPlayers()) {
                    player.hidePlayer(event.getPlayer());
                }
                for (Player player : game.getTeamB().getPlayers()) {
                    player.hidePlayer(event.getPlayer());
                }
            }
            Main.getGameManager().addPlayer(game, event.getPlayer());
        }
    }

    @EventHandler
    public void playerHitNotInGame(EntityDamageByEntityEvent event) {
        if(event.getDamager() instanceof Player) {
            if(Main.getGameManager().getGame((Player) event.getDamager()) == null) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        if(game.getState() != SAGameState.WAITING || game.getPlayerCount() >= game.getMaxPlayers()) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, ChatColor.RED + "You cannot join a game that is in progress!");
            return;
        }
        if((Main.getSAConfig().isPrivateLobby() && !Main.getSAConfig().getWhitelistedPlayers().contains(event.getPlayer().getName().toLowerCase())) || Main.getSAConfig().getBlacklistedPlayers().contains(event.getPlayer().getName().toLowerCase())) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, ChatColor.RED + "You are not whitelisted on this server");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        leaveGame(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        leaveGame(player);
    }

    private void leaveGame(Player player) {
        SAGame game = Main.getGameManager().getGame(player);
        if(game == null) {
            return;
        }
        game.getScoreboards().get(player.getUniqueId()).getStatus().reset();
        if(game.getState() == SAGameState.WAITING) {
            Main.getGameManager().removePlayer(game,player,false,true);
        } else {
            game.getSpectators().add(player);
            Bukkit.getScheduler().runTaskLater(Main.getInstance(),
                    () -> Main.getGameManager().checkAllPlayersOffline(game), 1L);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        SAGame game = Main.getGameManager().getGame(player);
        if(game == null) {
            return;
        }
        if(game.getState() == SAGameState.ROUND_START && !game.isDead(player) && (event.getTo().getX() != event.getFrom().getX() || event.getTo().getZ() != event.getFrom().getZ())) {
            event.setTo(event.getFrom());
            return;
        }
        if(game.getState() != SAGameState.ROUND_LIVE) {
            return;
        }
        if(player.getFallDistance() >= 6 && !game.isDead(player) && player.getLocation().getBlock().getRelative(BlockFace.DOWN).getType().isSolid()) {
            Main.getGameManager().damage(game, null, player, player.getFallDistance(), "Fall");
        }
        if((event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) && game.getBomb().getCarrier() == player) {
            ItemStack itemStack = player.getInventory().getItem(7);
            if(itemStack != null) {
                if(game.isAtBombsite(event.getTo())) {
                    if(itemStack.getType() == Material.QUARTZ) {
                        ItemMeta meta = itemStack.getItemMeta();
                        meta.setDisplayName("Bomb - Right Click");
                        itemStack.setItemMeta(meta);
                        itemStack.setType(Material.GOLDEN_APPLE);
                    } else if(itemStack.getType() == Material.GOLDEN_APPLE) {
                        ItemMeta meta = itemStack.getItemMeta();
                        meta.setDisplayName("Bomb");
                        itemStack.setItemMeta(meta);
                        itemStack.setType(Material.QUARTZ);
                    }
                }
            }
        }
        //todo callouts
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && Main.getGameManager().getGame((Player)event.getEntity()) != null && event.getCause() != EntityDamageEvent.DamageCause.CUSTOM) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player && Main.getGameManager().getGame((Player)event.getEntity()) != null) {
            event.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onHealthRegain(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player && Main.getGameManager().getGame((Player)event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover().getType() == EntityType.PLAYER && Main.getGameManager().getGame((Player)event.getRemover()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityBreak(HangingBreakEvent event) {
        if (event.getCause() != HangingBreakEvent.RemoveCause.ENTITY) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        SAGame game = Main.getGameManager().getGame(player);
        if (game == null) {
            return;
        }
        event.setCancelled(true);
        if (game.isDead(player) || (game.getState() != SAGameState.ROUND_LIVE && game.getState() != SAGameState.ROUND_START)) {
            return;
        }
        Item item = event.getItem();
        ItemStack itemStack = item.getItemStack();
        if((itemStack.getType() == Material.QUARTZ || itemStack.getType() == Material.GOLDEN_APPLE) && Main.getGameManager().getTeam(game, player) == SATeam.Team.OMEGA) {
            item.remove();
            game.getDrops().remove(item);
            if(itemStack.getType() == Material.QUARTZ && game.isAtBombsite(item.getLocation())) {
                ItemMeta meta = itemStack.getItemMeta();
                meta.setDisplayName("Bomb - Right Click");
                itemStack.setItemMeta(meta);
                itemStack.setType(Material.GOLDEN_APPLE);
            }
            if(itemStack.getType() == Material.GOLDEN_APPLE && !game.isAtBombsite(item.getLocation())) {
                ItemMeta meta = itemStack.getItemMeta();
                meta.setDisplayName("Bomb");
                itemStack.setItemMeta(meta);
                itemStack.setType(Material.QUARTZ);
            }
            game.getBomb().setCarrier(player);
            player.getInventory().setItem(7, itemStack);
            return;
        }
        Grenade grenade = Main.getWeaponManager().getGrenade(itemStack);
        if(grenade != null && game.getDrops().get(item) != null) {
            int slot = findNadeSlot(player);
            int current = 0;
            GrenadeType type = grenade.getType();
            int max = type.getMax();
            for(int i = 3; i < 8; i++) {
                if(Main.getWeaponManager().getGrenade(player.getInventory().getItem(i)) != null && Main.getWeaponManager().getGrenade(player.getInventory().getItem(i)).getType() == type) {
                    current++;
                }
            }
            if (slot != -1 && current != max) {
                event.setCancelled(true);
                itemStack.setAmount(1);
                player.getInventory().setItem(slot, itemStack);
                game.getDrops().remove(item);
                item.remove();
            }
        }
        Gun gun = Main.getWeaponManager().getGun(itemStack);
        Integer n = game.getDrops().get(item);
        if (gun != null && n != null) {
            int gunSlot = gun.getType().ordinal();
            if (player.getInventory().getItem(gunSlot) == null) {
                event.setCancelled(true);
                itemStack.setAmount(n + 1);
                player.getInventory().setItem(gunSlot, itemStack);
                NmsUtils.sendActionBar(player, player.getInventory().getItem(gunSlot).getAmount() + " / " + Utils.getReserveAmmo(itemStack));
                game.getDrops().remove(item);
                item.remove();
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        SAGame game = Main.getGameManager().getGame(player);
        if(game == null) {
            return;
        }
        int heldItemSlot = player.getInventory().getHeldItemSlot();
        ItemStack itemStack = event.getItemDrop().getItemStack();
        int amount = player.getItemInHand().getAmount();
        if(game.getState() == SAGameState.ROUND_LIVE || game.getState() == SAGameState.ROUND_START) {
            if(itemStack.getType() == Material.SHEARS) {
                event.setCancelled(true);
                return;
            }
            if(itemStack.getType() == Material.QUARTZ || itemStack.getType() == Material.GOLDEN_APPLE) {
                if(itemStack.getType() == Material.GOLDEN_APPLE) {
                    ItemMeta meta = itemStack.getItemMeta();
                    meta.setDisplayName("Bomb");
                    itemStack.setItemMeta(meta);
                    itemStack.setType(Material.QUARTZ);
                }
                game.getDrops().put(event.getItemDrop(), 1);
                game.getBomb().setDrop(event.getItemDrop());
                player.getInventory().setItem(7, ItemBuilder.create(Material.COMPASS, 1, "Bomb Locator", false));
                player.setCompassTarget(player.getLocation());
                return;
            }
            Gun gun = Main.getWeaponManager().getGun(itemStack);
            if(gun != null) {
                game.getDrops().put(event.getItemDrop(), amount);
                itemStack.setAmount(1);
                gun.resetDelay(player);
                ItemStack newStack = ItemBuilder.create(itemStack.getType(), 1, gun.getItemWrapper().getData(),
                        itemStack.getItemMeta().getDisplayName(),
                        itemStack.getItemMeta().getLore() == null ? new String[]{""} : itemStack.getItemMeta().getLore().toArray(new String[0]));
                newStack = Utils.setReserveAmmo(newStack, Utils.getReserveAmmo(itemStack));
                NmsUtils.addNBTInteger(newStack, "outofammo", NmsUtils.getNBTInteger(itemStack, "outofammo"));
                event.getItemDrop().setItemStack(newStack);
                player.getInventory().setItem(heldItemSlot, null);
                if(gun.hasScope()) {
                    NmsUtils.sendFakeItem(player, 5, player.getInventory().getHelmet());
                }
                return;
            }
            Grenade grenade = Main.getWeaponManager().getGrenade(itemStack);
            if(grenade != null) {
                game.getDrops().put(event.getItemDrop(), 1);
                itemStack.setAmount(1);
                event.getItemDrop().setItemStack(ItemBuilder.create(itemStack.getType(), 1,
                        grenade.getItemWrapper().getData(), itemStack.getItemMeta().getDisplayName()));
                player.getInventory().setItem(heldItemSlot, null);
                return;
            }
        }
        ItemStack clone = itemStack.clone();
        event.getItemDrop().remove();
        player.getInventory().setItem(player.getInventory().getHeldItemSlot(), clone);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if(Main.getGameManager().getGame(player) == null) {
            return;
        }
        Gun gun = Main.getWeaponManager().getGun(player.getItemInHand());
        if(gun == null || !gun.hasScope()) {
            return;
        }
        if(event.isSneaking()) {
            NmsUtils.sendFakeItem(player, 5, new ItemStack(Material.PUMPKIN));
            gun.scopeDelay(player);
        } else {
            NmsUtils.sendFakeItem(player, 5, player.getInventory().getHelmet());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInHand();
        SAGame game = Main.getGameManager().getGame(player);
        if (game == null || itemInHand == null || itemInHand.getType() == Material.AIR) {
            return;
        }
        event.setCancelled(true);
        Gun gun = Main.getWeaponManager().getGun(itemInHand);
        if(checkClick(player) && gun != null && !game.isDefusing(player) && game.getState() == SAGameState.ROUND_LIVE) {
            gun.shoot(game, player);
        }
    }

    @EventHandler
    public void onPhysics(BlockPhysicsEvent event) {
        if(event.getBlock().getType() == Material.CROPS) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void projectileDamage(EntityDamageByEntityEvent event) {
        if(!(event.getDamager() instanceof Snowball) || !(event.getEntity() instanceof Player)) {
            return;
        }
        event.setCancelled(true); //cancel knockback
        Projectile snowball = (Projectile) event.getDamager();

        ProjectileStats stats = Main.getWeaponManager().getProjectiles().get(snowball);
        Main.getWeaponManager().getProjectiles().remove(snowball);
        if(stats == null) {
            return;
        }
        Player damaged = (Player) event.getEntity();
        if(Main.getGameManager().getGame(damaged).sameTeam(damaged,stats.getShooter()) || Main.getGameManager().getGame(damaged).isDead(damaged)) {
            return;
        }
        HitType type = snowballCollision(damaged, snowball);
        if(type == HitType.HEAD) {
            double armorPen = damaged.getInventory().getHelmet().getType() == Material.LEATHER_HELMET ? 1 :
                    stats.getArmorPen();
            double rangeFalloff = (stats.getDropoff() * damaged.getLocation().distance(stats.getLocation()));
            double damage = stats.getDamage()*2.5;
            double finalDamage = Math.max(0,armorPen*(damage - rangeFalloff));
            Main.getInstance().getServer().getPluginManager().callEvent(new GunDamageEvent(finalDamage, true, stats.getShooter(), damaged));
            Main.getGameManager().getGame(damaged).getStats().get(stats.getShooter().getUniqueId()).addHeadshots(1);
            Main.getGameManager().damage(Main.getGameManager().getGame(damaged), stats.getShooter(), damaged,
                    finalDamage, stats.getGunName() + " headshot");
        } else if(type == HitType.BODY) {
            double armorPen = damaged.getInventory().getChestplate().getType() == Material.LEATHER_CHESTPLATE ? 1 :
                    stats.getArmorPen();
            double rangeFalloff = (stats.getDropoff() * damaged.getLocation().distance(stats.getLocation()));
            double damage = stats.getDamage();
            double finalDamage = Math.max(0,armorPen*(damage - rangeFalloff));
            Main.getInstance().getServer().getPluginManager().callEvent(new GunDamageEvent(finalDamage, false, stats.getShooter(), damaged));
            Main.getGameManager().damage(Main.getGameManager().getGame(damaged), stats.getShooter(), damaged,
                    finalDamage, stats.getGunName());
        }
    }

    @EventHandler
    public void onHungerChange(FoodLevelChangeEvent event) {
        ((Player) event.getEntity()).setFoodLevel(20);
    }

    @EventHandler
    public void onCraftingTable(PlayerInteractEvent event) {
        if(event.getAction() == Action.RIGHT_CLICK_BLOCK && (event.getClickedBlock().getType() == Material.WORKBENCH || event.getClickedBlock().getType() == Material.FURNACE || event.getClickedBlock().getType() == Material.CHEST)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEat(PlayerItemConsumeEvent event) {
        if(Main.getGameManager().getGame(event.getPlayer()) == null) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        if(event.getEntity() instanceof ArmorStand) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball) || !(event.getEntity().getShooter() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity().getShooter();
        Location eye = player.getEyeLocation();

        //hitscan taken from gun code, only runs if block is not already broken by the projectilehitevent
        double yawRad = FastMath.toRadians(Utils.dumbMinecraftDegrees(eye.getYaw())+90);
        double pitchRad = FastMath.toRadians(eye.getPitch() + 90);
        double x = eye.getX();
        double y = eye.getY();
        double z = eye.getZ();
        double cot = FastMath.sin(pitchRad) * FastMath.cos(yawRad);
        double cos = FastMath.cos(pitchRad);
        double sin2 = FastMath.sin(pitchRad) * FastMath.sin(yawRad);

        double distance = 0.5;
        while (distance < 30) {
            eye.setX(x + distance * cot);
            eye.setY(y + distance * cos);
            eye.setZ(z + distance * sin2);
            Material type = eye.getBlock().getType();
            if(type != Material.AIR && type != Material.CROPS) {
                if(type == Material.THIN_GLASS || type == Material.STAINED_GLASS_PANE) {
                    eye.getBlock().breakNaturally();
                }
                break;
            }
            distance += 0.25;
        }

        eye.setX(x);
        eye.setY(y);
        eye.setZ(sin2);
    }

    private List<Block> getSurrounding(Block block) {
        List<Block> blocks = new ArrayList<>();
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0)
                        continue;

                    blocks.add(block.getRelative(x, y, z));
                }
        return blocks;
    }

    private HitType snowballCollision(Player damaged, Projectile snowball) {
        Location start = snowball.getLocation();
        Location location = start.clone();

        while(!hitHead(damaged, location) && !hitBody(damaged, location) && Utils.offset(damaged.getLocation().toVector(), location.toVector()) < 6) {
            location.add(snowball.getVelocity().clone().multiply(0.1));
        }

        if(hitBody(damaged, location))
            return HitType.BODY;
        if(hitHead(damaged, location))
            return HitType.HEAD;
        return HitType.MISS;
    }

    private boolean hitBody(Player player, Location location) {
        return Utils.offset2d(location.toVector(), player.getLocation().toVector()) < 0.6 &&
                location.getY() > player.getLocation().getY() &&
                location.getY() < player.getEyeLocation().getY() - 0.1;
    }

    private boolean hitHead(Player player, Location location) {
        return Utils.offset2d(location.toVector(), player.getLocation().toVector()) < 0.2 &&
                location.getY() >= player.getEyeLocation().getY() - 0.0 &&
                location.getY() < player.getEyeLocation().getY() + 0.43;
    }


    private enum HitType {
        MISS, BODY, HEAD
    }

}
