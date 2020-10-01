package com.podcrash.squadassault.weapons;

import com.podcrash.squadassault.Main;
import com.podcrash.squadassault.game.SAGame;
import com.podcrash.squadassault.game.events.GunDamageEvent;
import com.podcrash.squadassault.nms.NmsUtils;
import com.podcrash.squadassault.util.Item;
import com.podcrash.squadassault.util.Randomizer;
import com.podcrash.squadassault.util.Utils;
import me.dpohvar.powernbt.api.NBTManager;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class Gun {

    private final Item item;
    private final String name;
    private final GunHotbarType type;
    private final boolean projectile;
    private final boolean isShotgun;
    private final String shootSound;
    private final String reloadSound;
    private int reloadDuration;
    private int magSize;
    private int totalAmmoSize;
    private double accuracy;
    private double damage;
    private int bulletsPerShot;
    private boolean scope;
    private int delayPerShot;
    private double dropoffPerBlock;
    private double armorPen;
    private int bulletsPerYaw;
    private int bulletsPerPitch;
    private int delayBullets;
    private int bulletsPerBurst;
    private int killReward;
    private double projectileConeMin;
    private double projectileConeMax;
    private double coneIncPerBullet;
    private double resetPerTick;
    private final Map<UUID, Long> delay;
    private final Map<UUID, GunCache> cache;
    private final Map<UUID, GunReload> reloading;

    public Gun(String name, Item item, GunHotbarType type, boolean projectile, String shootSound, String reloadSound,
     boolean isShotgun) {
        this.name = name;
        this.item = item;
        this.type = type;
        this.projectile = projectile;
        this.shootSound = shootSound;
        this.reloadSound = reloadSound;
        this.isShotgun = isShotgun;
        delay = new HashMap<>();
        reloading = new HashMap<>();
        cache = new HashMap<>();
    }

    public void setReloadDuration(int reloadDuration) {
        this.reloadDuration = reloadDuration;
    }

    public int getReloadDuration() {
        return reloadDuration;
    }

    public int getMagSize() {
        return magSize;
    }

    public void setMagSize(int magSize) {
        this.magSize = magSize;
    }

    public int getTotalAmmoSize() {
        return totalAmmoSize;
    }

    public void setTotalAmmoSize(int totalAmmoSize) {
        this.totalAmmoSize = totalAmmoSize;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = damage;
    }

    public int getBulletsPerShot() {
        return bulletsPerShot;
    }

    public void setBulletsPerShot(int bulletsPerShot) {
        this.bulletsPerShot = bulletsPerShot;
    }

    public boolean hasScope() {
        return scope;
    }

    public void setScope(boolean scope) {
        this.scope = scope;
    }

    public int getDelayPerShot() {
        return delayPerShot;
    }

    public void setDelayPerShot(int delayPerShot) {
        this.delayPerShot = delayPerShot;
    }

    public double getDropoffPerBlock() {
        return dropoffPerBlock;
    }

    public void setDropoffPerBlock(double dropoffPerBlock) {
        this.dropoffPerBlock = dropoffPerBlock;
    }

    public int getBulletsPerYaw() {
        return bulletsPerYaw;
    }

    public void setBulletsPerYaw(int bulletsPerYaw) {
        this.bulletsPerYaw = bulletsPerYaw;
    }

    public int getBulletsPerPitch() {
        return bulletsPerPitch;
    }

    public void setBulletsPerPitch(int bulletsPerPitch) {
        this.bulletsPerPitch = bulletsPerPitch;
    }

    public int getDelayBullets() {
        return delayBullets;
    }

    public void setDelayBullets(int delayBullets) {
        this.delayBullets = delayBullets;
    }

    public int getBulletsPerBurst() {
        return bulletsPerBurst;
    }

    public void setBulletsPerBurst(int bulletsPerBurst) {
        this.bulletsPerBurst = bulletsPerBurst;
    }

    public String getName() {
        return name;
    }

    public Item getItem() {
        return item;
    }

    public GunHotbarType getType() {
        return type;
    }

    public boolean isProjectile() {
        return projectile;
    }

    public String getShootSound() {
        return shootSound;
    }

    public String getReloadSound() {
        return reloadSound;
    }

    public void shoot(SAGame game, Player player) {
        if(player.getInventory().getHeldItemSlot() != type.ordinal()) {
            return;
        }
        long del = System.currentTimeMillis() / 49;
        if(delay.get(player.getUniqueId()) == null) {
            delay.put(player.getUniqueId(), del);
        } else if(del - delay.get(player.getUniqueId()) <= delayPerShot) {
            return;
        }
        GunCache gunCache = cache.get(player.getUniqueId());
        if(gunCache == null) {
            cache.put(player.getUniqueId(), new GunCache(game, bulletsPerShot, projectileConeMin));
        } else {
            gunCache.setRounds(bulletsPerShot);
            gunCache.setLastShot(System.currentTimeMillis());
        }
        delay.put(player.getUniqueId(), del);
    }

    public void reload(Player player, int slot, int left) {
        ItemStack itemStack = player.getInventory().getItem(slot);
        if (!item.equals(itemStack) || itemStack.getAmount() >= magSize || reloading.containsKey(player.getUniqueId())) {
            return;
        }
        try {
            if (Utils.getReserveAmmo(itemStack) <= 0) {
                NmsUtils.sendActionBar(player, itemStack.getAmount() + " / " + Utils.getReserveAmmo(itemStack));
                GunReload reload = new GunReload(left, reloadDuration);
                reload.setOutOfAmmo(true);
                reloading.put(player.getUniqueId(), reload);
                return;
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
            NBTManager.getInstance().read(itemStack).forEach((key, value) -> System.out.println(key + " " + value));
            return;
        }
        //play sound

        reloading.put(player.getUniqueId(), new GunReload(left, reloadDuration));
    }

    public void resetPlayer(Player player) {
        if(reloading.get(player.getUniqueId()) != null && !reloading.get(player.getUniqueId()).isOutOfAmmo()) {
            reloading.remove(player.getUniqueId());
        }
        cache.remove(player.getUniqueId());
    }

    public void resetDelay(Player player) {
        delay.remove(player.getUniqueId());
    }

    public int getKillReward() {
        return killReward;
    }

    public void setKillReward(int killReward) {
        this.killReward = killReward;
    }

    public void tick() {
        if(!reloading.isEmpty()) {
            Iterator<Map.Entry<UUID, GunReload>> iterator = reloading.entrySet().iterator();
            while(iterator.hasNext()) {
                Map.Entry<UUID, GunReload> entry = iterator.next();
                if(entry.getValue().isOutOfAmmo()) {
                    continue;
                }
                Player player = Bukkit.getPlayer(entry.getKey());
                if(player != null && !player.isDead() && player.isOnline() && Main.getGameManager().getGame(player) != null) {
                    if(item.equals(player.getItemInHand())) {
                        player.getItemInHand().setDurability((short)(entry.getValue().getLeft() / entry.getValue().getDuration() * player.getItemInHand().getType().getMaxDurability()));
                        if(entry.getValue().getLeft() <= 0) {
                            iterator.remove();
                            /*
                            Get ammo left in gun then deduct from mag size
                            If there isn't enough in reserves then just give whats left
                             */
//                            int oldAmount = player.getItemInHand().getAmount();
//                            int newAmount =
//                                    Utils.getReserveAmmo(player.getItemInHand()) >= magSize || Utils.getReserveAmmo(player.getItemInHand()) >= magSize - oldAmount ?
//                                            magSize :
//                                            Utils.getReserveAmmo(player.getItemInHand()) + oldAmount;
//                            Utils.setReserveAmmo(player.getItemInHand(),
//                                    Utils.getReserveAmmo(player.getItemInHand()) - newAmount);
                            int oldAmount = entry.getValue().getOldAmount();
                            int needed = magSize - oldAmount;
                            int reserve = Utils.getReserveAmmo(player.getItemInHand());
                            int toSet = reserve >= needed ? magSize : oldAmount + reserve;
                            int reserveLeft = reserve >= needed ? reserve - needed : 0;
                            ItemStack stack = Utils.setReserveAmmo(player.getItemInHand(), reserveLeft);
                            player.setItemInHand(stack);
                            NmsUtils.sendActionBar(player, toSet + " / " + reserveLeft);
                            player.getItemInHand().setAmount(toSet);
                            player.getItemInHand().setDurability((short) 0);
                            //play sound?
                        }
                        entry.getValue().setLeft(entry.getValue().getLeft() - 1);
                    } else {
                        iterator.remove();
                    }
                } else {
                    iterator.remove();
                }
            }
        }
        if(!delay.isEmpty()) {
            Iterator<Map.Entry<UUID, Long>> iterator = delay.entrySet().iterator();
            while(iterator.hasNext()) {
                Map.Entry<UUID, Long> entry = iterator.next();
                Player player = Bukkit.getPlayer(entry.getKey());
                long del = System.currentTimeMillis() / 49;
                if(player == null || player.isDead() || !player.isOnline() || Main.getGameManager().getGame(player) == null || del - entry.getValue() > delayPerShot) {
                    iterator.remove();
                }
            }
        }
        if (cache.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, GunCache>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, GunCache> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            GunCache gunCache = entry.getValue();
            if(System.currentTimeMillis() - gunCache.getLastShot() > delayPerShot*50) {
                gunCache.setCone(Math.max(gunCache.getCone() - resetPerTick, projectileConeMin));
            }
            if (!reloading.containsKey(entry.getKey())) {
                if (player != null && !player.isDead() && player.isOnline() && gunCache.getGame() != null) {
                    if (!gunCache.getGame().getSpectators().contains(player)) {
                        gunCache.setTicksLeft(gunCache.getTicksLeft() - 1);
                        if (gunCache.getRounds() > 0 && item.equals(player.getInventory().getItemInHand())) {
                            gunCache.setTicks(gunCache.getTicks() + 1);
                            if (gunCache.isFirstShot()) {
                                gunCache.setFirstShot(false);
                            } else if (gunCache.getTicks() % delayBullets != 0) {
                                return;
                            }
                            if(gunCache.getCone() == projectileConeMin) {
                                gunCache.setFirstShot(true);
                            }
                            if (player.getItemInHand().getAmount() == 1) {
                                iterator.remove();
                                reload(player, getType().ordinal(), 0);

                                //play sound
                            } else {
                                gunCache.setRounds(gunCache.getRounds() - 1);
                                player.getItemInHand().setAmount(player.getItemInHand().getAmount() - 1);
                            }
                            Location eye = player.getEyeLocation();
                            boolean isMoving = !player.getVelocity().equals(new Vector());
                            if (player.isSneaking()) {
                                eye.subtract(0, 0.03, 0);
                            } else {
                                eye.subtract(0, 0.1, 0);
                            }
                            //play sound

                            if (accuracy == 0) {
                                gunCache.setAccuracyPitch(0);
                                gunCache.setAccuracyYaw(0);
                            }
                            float yaw = eye.getYaw();
                            float pitch = eye.getPitch();
                            double x = eye.getX();
                            double y = eye.getY();
                            double z = eye.getZ();
                            for(int i = 0; i < bulletsPerBurst; i++) {
                                if (projectile) {
                                    if (isShotgun) {
                                        shotgun(player, isMoving, gunCache);
                                    } else {
                                        projectile(player, isMoving, gunCache);
                                    }
                                } else {
                                    if (isMoving) {
                                        gunCache.setAccuracyYaw(Randomizer.randomRange((int) (-accuracy * 3),
                                                (int) (accuracy * 3)) + 0.5f);
                                        gunCache.setAccuracyPitch(Randomizer.randomRange((int) (-accuracy * 3),
                                                (int) (accuracy * 3)) + 0.5f);
                                    } else if (scope && player.isSneaking()) {
                                        gunCache.setAccuracyYaw(0);
                                        gunCache.setAccuracyPitch(0);
                                    } else if (scope && !player.isSneaking()) {
                                        gunCache.setAccuracyYaw(Randomizer.randomRange((int) (-accuracy),
                                                (int) (accuracy)) + 0.5f);
                                        gunCache.setAccuracyPitch(Randomizer.randomRange((int) (-accuracy),
                                                (int) (accuracy)) + 0.5f);
                                    }
                                    double yawRad = Math.toRadians(Utils.dumbMinecraftDegrees(yaw + 0.6) + gunCache.getAccuracyYaw() + 90);
                                    double pitchRad = Math.toRadians(pitch + gunCache.getAccuracyPitch() + 90);
                                    double cot = Math.sin(pitchRad) * Math.cos(yawRad);
                                    double cos = Math.cos(pitchRad);
                                    double sin2 = Math.sin(pitchRad) * Math.sin(yawRad);
                                    hitscan(player, eye, x, y, z, cot, cos, sin2, gunCache);
                                    eye.setX(x);
                                    eye.setY(y);
                                    eye.setZ(sin2);
                                }
                            }
                        } else {
                            if (gunCache.getTicksLeft() > 0) {
                                continue;
                            }
                            iterator.remove();
                        }
                    } else {
                        iterator.remove();
                    }
                } else {
                    iterator.remove();
                }
            } else {
                iterator.remove();
            }
        }
    }

    private void hitscan(Player player, Location eye, double x, double y, double z, double cot, double cos,
                             double sin2, GunCache gunCache) {
        double distance = 0.5;
        while (distance < 100) {
            eye.setX(x + distance * cot);
            eye.setY(y + distance * cos);
            eye.setZ(z + distance * sin2);
            if (distance % 1.5 == 0.0) {
                player.spigot().playEffect(eye, Effect.FLAME, 0, 0, 0.0f, 0.0f, 0.0f, 0.0f, 1, 20);
            }
            if (distance == 0.5) {
                player.getWorld().playEffect(eye, Effect.SNOWBALL_BREAK, 0, 10);
                player.getWorld().playEffect(eye, Effect.SNOWBALL_BREAK, 0, 10);
            }
            final Material type = eye.getBlock().getType();
            if (type != Material.CROPS && type != Material.AIR) {
                int xMod = (int)((eye.getX() - eye.getBlockX()) * 1000.0);
                int yMod = (int)((eye.getY() - eye.getBlockY()) * 10.0);
                int zMod = (int)((eye.getZ() - eye.getBlockZ()) * 1000.0);
                String data = eye.getBlock().getState().getData().toString();
                boolean inverted = data.contains("inverted");
                Block block = eye.getBlock();
                if (type.name().contains("FENCE")) {
                    if (xMod >= 370 && xMod <= 620 && zMod >= 370 && zMod <= 620) {
                        player.getWorld().playEffect(eye, Effect.STEP_SOUND, block.getType());
                        break;
                    }
                } else if (type == Material.COBBLE_WALL) {
                    if (xMod >= 240 && xMod <= 750 && zMod <= 750) {
                        player.getWorld().playEffect(eye, Effect.STEP_SOUND, block.getType());
                        break;
                    }
                } else if (type.name().contains("STEP") || type.name().contains("STONE_SLAB2")) {
                    if (inverted) {
                        if (yMod > 5) {
                            player.getWorld().playEffect(eye, Effect.STEP_SOUND, block.getType());
                            break;
                        }
                    } else if (yMod < 5) {
                        player.getWorld().playEffect(eye, Effect.STEP_SOUND, block.getType());
                        break;
                    }
                } else {
                    if (!type.name().contains("STAIRS")) {
                        player.getWorld().playEffect(eye, Effect.STEP_SOUND, block.getType());
                        break;
                    }
                    if (data.contains("NORTH")) {
                        if (checkDirection(player, eye, yMod, inverted, block, zMod > 500, zMod < 500, zMod))
                            break;
                    } else if (data.contains("SOUTH")) {
                        if (checkDirection(player, eye, yMod, inverted, block, zMod < 500, zMod > 500, zMod))
                            break;
                    } else if (data.contains("EAST")) {
                        if (checkDirection(player, eye, yMod, inverted, block, xMod < 500, xMod > 500, zMod))
                            break;
                    } else if (data.contains("WEST")) {
                        if (inverted) {
                            if (xMod > 500 || (xMod < 500 && yMod >= 5)) {
                                player.getWorld().playEffect(eye, Effect.STEP_SOUND, block.getType());
                                break;
                            }
                        } else if (xMod > 500 || (xMod < 500 && yMod < 5)) {
                            player.getWorld().playEffect(eye, Effect.STEP_SOUND, block.getType());
                            break;
                        }
                    }
                }
            }
            Player hit = null;
            for (final Player p : gunCache.getGame().getTeamA().getPlayers()) {
                if (hitscanHit(player, gunCache, eye, p)) {
                    hit = p;
                    break;
                }
            }
            for (final Player p : gunCache.getGame().getTeamB().getPlayers()) {
                if (hitscanHit(player, gunCache, eye, p)) {
                    hit = p;
                    break;
                }
            }
            if (hit != null) {
                if ((hit.isSneaking() || eye.getY() - hit.getLocation().getY() <= 1.35 || eye.getY() - hit.getLocation().getY() >= 1.9) && (eye.getY() - hit.getLocation().getY() <= 1.27 || eye.getY() - hit.getLocation().getY() >= 1.82)) {
                    double armorPen = hit.getInventory().getChestplate().getType() == Material.LEATHER_CHESTPLATE ? 1 :
                            this.armorPen;
                    double rangeFalloff = (dropoffPerBlock * hit.getLocation().distance(player.getLocation()));
                    double damage = this.damage;
                    double finalDamage = Math.max(0,armorPen*(damage - rangeFalloff));
                    Main.getInstance().getServer().getPluginManager().callEvent(new GunDamageEvent(finalDamage, true,
                            player, hit));
                    Main.getGameManager().getGame(player).getStats().get(player.getUniqueId()).addHeadshots(1);
                    Main.getGameManager().damage(Main.getGameManager().getGame(hit), player, hit,
                            finalDamage, name);
                    break;
                }
                double armorPen = hit.getInventory().getHelmet().getType() == Material.LEATHER_HELMET ? 1 :
                        this.armorPen;
                double rangeFalloff = (dropoffPerBlock * hit.getLocation().distance(player.getLocation()));
                double damage = this.damage*2.5;
                double finalDamage = Math.max(0,armorPen*(damage - rangeFalloff));
                Main.getInstance().getServer().getPluginManager().callEvent(new GunDamageEvent(finalDamage, true,
                        player, hit));
                Main.getGameManager().getGame(player).getStats().get(player.getUniqueId()).addHeadshots(1);
                Main.getGameManager().damage(Main.getGameManager().getGame(hit), player, hit,
                        finalDamage, name + " headshot");
                //sound
                break;
            } else {
                distance += 0.25;
            }
        }
    }

    private boolean checkDirection(Player player, Location eye, int yMod, boolean inverted, Block block, boolean b, boolean b2, int zMod) {
        if (inverted) {
            if (b || (b2 && yMod >= 5)) {
                player.getWorld().playEffect(eye, Effect.STEP_SOUND, block.getType());
                return true;
            }
        } else if (b || (b2 && yMod < 5)) {
            player.getWorld().playEffect(eye, Effect.STEP_SOUND, block.getType());
            return true;
        }
        return false;
    }

    private void projectile(Player player, boolean moving, GunCache cache) {
        Snowball snowball = player.launchProjectile(Snowball.class);

        double cone = projectileSpray(player, moving, cache);
        Vector spray;
        if(cache.isFirstShot() && player.isOnGround() && !player.isSprinting()) {
            spray = new Vector(-0.5, -0.5, -0.5);
        } else {
            spray = new Vector(Randomizer.random() - 0.5, (Randomizer.random() - 0.2) * (5d/8d),
                    Randomizer.random() - 0.5);
        }
        spray.normalize();
        spray.multiply(cone);
        spray.add(player.getLocation().getDirection());
        spray.normalize();

        snowball.setVelocity(spray.multiply(4));
        cache.setCone(Math.min(projectileConeMax, cone + coneIncPerBullet));
        Main.getWeaponManager().getProjectiles().put(snowball, new ProjectileStats(name, player.getLocation().clone(),
                dropoffPerBlock, damage, armorPen, player));
    }

    private void shotgun(Player player, boolean moving, GunCache cache) {
        for(int i = 0; i < bulletsPerShot; i++) {
            //todo sound here
            projectile(player, moving, cache);
        }
    }

    private double projectileSpray(Player player, boolean isMoving, GunCache cache) {
        double cone = cache.getCone();
        if(!player.isOnGround()) {
            cone += 0.08;
        } else if(player.isSprinting()) {
            cone += 0.02;
        } else if(player.isSneaking()) {
            cone *= 0.8;
        }
        return cone;
    }

    private boolean hitscanHit(Player player, GunCache gunCache, Location eye, Player p) {
        return player != p && !gunCache.getGame().getSpectators().contains(p) && !gunCache.getGame().sameTeam(player,
                p) && (p.getLocation().add(0.0, 0.2, 0.0).distance(eye) <= 0.4 + 0.0 || p.getLocation().add(0.0, 1.0, 0.0).distance(eye) <= 0.5 + 0.0 || p.getEyeLocation().distance(eye) <= 0.35) && !p.isDead();
    }

    public void setArmorPen(double armorPen) {
        this.armorPen = armorPen;
    }

    public double getProjectileConeMin() {
        return projectileConeMin;
    }

    public void setProjectileConeMin(double projectileConeMin) {
        this.projectileConeMin = projectileConeMin;
    }

    public double getProjectileConeMax() {
        return projectileConeMax;
    }

    public void setProjectileConeMax(double projectileConeMax) {
        this.projectileConeMax = projectileConeMax;
    }

    public double getConeIncPerBullet() {
        return coneIncPerBullet;
    }

    public void setConeIncPerBullet(double coneIncPerBullet) {
        this.coneIncPerBullet = coneIncPerBullet;
    }

    public double getResetPerTick() {
        return resetPerTick;
    }

    public void setResetPerTick(double resetPerTick) {
        this.resetPerTick = resetPerTick;
    }

    public void resetReloading(Player player, int index) {
        reloading.remove(player.getUniqueId());
        player.getInventory().getItem(index).setAmount(magSize);
    }
}
