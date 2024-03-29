package com.podcrash.squadassault;

import com.podcrash.squadassault.game.SAGame;
import com.podcrash.squadassault.game.SATeam;
import com.podcrash.squadassault.shop.PlayerShopItem;
import com.podcrash.squadassault.util.ItemWrapper;
import com.podcrash.squadassault.util.Utils;
import com.podcrash.squadassault.weapons.Grenade;
import com.podcrash.squadassault.weapons.GrenadeType;
import com.podcrash.squadassault.weapons.Gun;
import com.podcrash.squadassault.weapons.WeaponManager;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class Config {

    private FileConfiguration config;
    private YamlConfiguration guns;
    private YamlConfiguration grenades;
    private YamlConfiguration shop;
    private YamlConfiguration maps;
    private int roundsPerHalf = 15;
    private int roundsToWin = 16;
    private int minPlayers = 4;
    private int maxPlayers = 16;
    private String map;
    private boolean privateServer = false; //whether ths is an MPS or not
    private boolean privateLobby = false; //whether people need to be whitelisted or not
    private boolean shutdownOnExit = true;
    private boolean randomizeSide = true;
    private boolean exportStatsAtEnd = true;
    private List<String> hosts;
    private List<String> whitelistedPlayers;
    private List<String> blacklistedPlayers;

    public void startConfig() {
        File dataFolder = Main.getInstance().getDataFolder();
        dataFolder.mkdirs();
        log("Loading config.yml");
        loadConfig();

        log("Loading maps.yml");
        File fileMaps = new File(dataFolder, "maps.yml");
        if(fileMaps.exists()) {
            maps = YamlConfiguration.loadConfiguration(fileMaps);
        } else {
            saveMaps(dataFolder);
        }
        loadMaps();

        log("Loading guns.yml");
        File fileGuns = new File(dataFolder, "guns.yml");
        if(!fileGuns.exists()) {
            Main.getInstance().saveResource("guns.yml", true);
        }
        guns = YamlConfiguration.loadConfiguration(fileGuns);
        loadGuns();

        log("Loading grenades.yml");
        File fileNades = new File(dataFolder, "grenades.yml");
        if(!fileNades.exists()) {
            Main.getInstance().saveResource("grenades.yml",true);
        }

        grenades = YamlConfiguration.loadConfiguration(fileNades);
        loadNades();

        log("loading shop.yml");
        File fileShop = new File(dataFolder, "shop.yml");
        if(!fileShop.exists()) {
            Main.getInstance().saveResource("shop.yml",true);
        }

        shop = YamlConfiguration.loadConfiguration(fileShop);
        loadShop();
    }

    private void loadShop() {
        WeaponManager manager = Main.getWeaponManager();
        for(String gun : shop.getConfigurationSection("ShopGuns").getKeys(false)) {
            if(manager.getGun(shop.getString("ShopGuns."+gun+".ItemName")) == null) {
                log(gun + " in shop.yml doesn't exist in guns.yml");
                continue;
            }
            int price = shop.getInt("ShopGuns."+gun+".Price");
            SATeam.Team side = Utils.nullSafeValueOf(shop.getString("ShopGuns."+gun+".Side"));
            String name = shop.getString("ShopGuns."+gun+".ItemName");
            String lore = shop.getString("ShopGuns."+gun+".ItemLore");
            int slot = shop.getInt("ShopGuns."+gun+".Slot");
            Main.getShopManager().addShop(new PlayerShopItem(shop.getString("ShopGuns."+gun+".ItemName"), name, slot,
                    price, lore, side));
        }
        for(String grenade : shop.getConfigurationSection("ShopGrenades").getKeys(false)) {
            if(manager.getGrenade(grenade) == null) {
                log(grenade + " in shop.yml doesn't exist in grenades.yml");
                continue;
            }
            Main.getShopManager().addShop(new PlayerShopItem(
                grenade, shop.getString("ShopGrenades."+grenade+".ItemName"), shop.getInt("ShopGrenades."+grenade+
                    ".Slot"), shop.getInt("ShopGrenades."+grenade+
                    ".Price"), shop.getString("ShopGrenades."+grenade+".ItemLore")
            ));
        }
        for(String item : shop.getConfigurationSection("ShopItems").getKeys(false)) {
            int slot = shop.getInt("ShopItems."+item+".Slot");
            int price = shop.getInt("ShopItems."+item+".Price");
            int slotPlace = shop.getInt("ShopItems."+item+".SlotPlace");
            String name = shop.getString("ShopItems."+item+".ItemName");
            String lore = shop.getString("ShopItems."+item+".ItemLore");
            SATeam.Team side = Utils.nullSafeValueOf(shop.getString("ShopItems."+item+".Side"));
            Material material = Material.getMaterial(shop.getString("ShopItems."+item+".Material"));
            Main.getShopManager().addShop(new PlayerShopItem(slot, slotPlace, name, material, price, lore, side, item));
        }
    }

    private void loadNades() {
        for(String nade : grenades.getConfigurationSection("Grenades").getKeys(false)) {
            Main.getWeaponManager().addGrenade(
                new Grenade(nade, GrenadeType.valueOf(grenades.getString("Grenades."+nade+".ItemInfo.Type")),
                        new ItemWrapper(Material.valueOf(grenades.getString("Grenades."+nade+".ItemInfo.ItemType")),
                                (byte)grenades.getInt("Grenades."+nade+".ItemInfo.Data"), grenades.getString(
                                        "Grenades."+nade+".ItemInfo.Name")), grenades.getInt("Grenades."+nade+
                        ".Properties.Delay"),grenades.getInt("Grenades."+nade+
                        ".Properties.Duration"), grenades.getDouble("Grenades."+nade+
                        ".Properties.ThrowSpeed"),grenades.getDouble("Grenades."+nade+
                        ".Properties.EffectPower")));
        }
    }

    private void loadMaps() {
        if(maps.getString("Game") != null && !maps.isString("Game")) {
            for(String id : maps.getConfigurationSection("Game").getKeys(false)) {
                World world = Bukkit.getServer().createWorld(new WorldCreator(maps.getString("Game."+id+".Name")));
                world.getLivingEntities().stream().filter(e -> e.getType() != EntityType.PLAYER).forEach(Entity::remove);
                world.setStorm(false);
                try {
                    Main.getGameManager().addGame(new SAGame(id, maps.getString("Game." + id + ".Name"),
                            Utils.getDeserializedLocation(maps.getString("Game." + id + ".Lobby")), minPlayers,
                            Utils.getDeserializedLocations(maps.getStringList("Game." + id + ".AlphaSpawns")),
                            Utils.getDeserializedLocations(maps.getStringList("Game." + id + ".OmegaSpawns")),
                            Utils.getDeserializedLocation(maps.getString("Game." + id + ".BombA")),
                            Utils.getDeserializedLocation(maps.getString("Game." + id + ".BombB"))));
                } catch (Exception e) {
                    error("Error loading game with ID " + id);
                    e.printStackTrace();
                }
            }
        }
    }

    private void loadGuns() {
        for(String gun : guns.getConfigurationSection("Guns").getKeys(false)) {
            Gun gunObj = new Gun(guns.getString("Guns."+gun+".ItemInfo.Name"), new ItemWrapper(Material.valueOf(guns.getString("Guns."+gun+".ItemInfo.Type")),
                    (byte)guns.getInt("Guns."+gun+".ItemInfo.Data"),guns.getString("Guns."+gun+".ItemInfo.Name")),
                    Gun.GunHotbarType.valueOf(guns.getString("Guns."+gun+".ItemInfo.HotbarType")),
                    guns.getBoolean("Guns."+gun+".Shoot.Projectile"), Sound.valueOf(guns.getString("Guns."+gun+
                    ".Shoot.Sound")), guns.getBoolean("Guns."+gun+
                    ".ItemInfo.IsShotgun"));
            gunObj.setBulletsPerPitch(guns.getInt("Guns." + gun + ".Burst.BulletsPerPitch"));
            gunObj.setBulletsPerYaw(guns.getInt("Guns." + gun + ".Burst.BulletsPerYaw"));
            gunObj.setDelayBullets(guns.getInt("Guns." + gun + ".Burst.DelayBullets"));
            gunObj.setBulletsPerBurst(guns.getInt("Guns." + gun + ".Burst.BulletsPerBurst"));
            gunObj.setDropoffPerBlock(guns.getDouble("Guns." + gun + ".Shoot.DropoffPerBlock"));
            gunObj.setAccuracy(guns.getDouble("Guns." + gun + ".Shoot.Accuracy"));
            gunObj.setScope(guns.getBoolean("Guns." + gun + ".Shoot.Scope"));
            gunObj.setDamage(guns.getDouble("Guns." + gun + ".Shoot.Damage"));
            gunObj.setReloadDuration(guns.getInt("Guns." + gun + ".Reload.Duration"));
            gunObj.setMagSize(guns.getInt("Guns." + gun + ".Reload.Amount"));
            gunObj.setTotalAmmoSize(guns.getInt("Guns." + gun + ".Reload.TotalAmount"));
            gunObj.setBulletsPerShot(guns.getInt("Guns." + gun + ".Shoot.BulletsPerShot"));
            gunObj.setDelayPerShot(guns.getInt("Guns." + gun + ".Shoot.Delay"));
            gunObj.setKillReward(guns.getInt("Guns."+gun+".ItemInfo.KillReward"));
            gunObj.setArmorPen(guns.getDouble("Guns."+gun+".Shoot.ArmorPen"));
            gunObj.setConeIncPerBullet(guns.getDouble("Guns."+gun+".Burst.ProjectileConeIncrease"));
            gunObj.setProjectileConeMin(guns.getDouble("Guns."+gun+".Burst.ProjectileConeMin"));
            gunObj.setProjectileConeMax(guns.getDouble("Guns."+gun+".Burst.ProjectileConeMax"));
            gunObj.setResetPerTick(guns.getDouble("Guns."+gun+".Burst.Reset"));
            gunObj.setScopeDelay(guns.getInt("Guns."+gun+".Shoot.ScopeDelay"));
            gunObj.setBurstDelay(guns.getInt("Guns."+gun+".Burst.BurstDelay"));
            gunObj.setShotgunBullets(guns.getInt("Guns."+gun+".Shoot.ShotgunBullets"));
            Main.getWeaponManager().addGun(gunObj);
        }
    }

    public void saveMaps(File dataFolder) {
        File file = new File(dataFolder, "maps.yml");
        try {
            if(!file.createNewFile()) {
                maps = YamlConfiguration.loadConfiguration(file);
                if(maps.getString("Game") == null) {
                    maps.set("Game", "No games made yet");
                }
            }
            maps.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public YamlConfiguration getMaps() {
        return maps;
    }

    public void loadConfig() {
        Main.getInstance().reloadConfig();
        config = Main.getInstance().getConfig();
        config.addDefault("Map", "Nuke");
        config.addDefault("MinPlayers",minPlayers);
        config.addDefault("MaxPlayers",maxPlayers);
        config.addDefault("PrivateLobby", privateLobby);
        config.addDefault("Private", privateServer);
        config.addDefault("AllowedPlayers", new ArrayList<>(Arrays.asList("n0toh", "pmahcgop")));
        config.addDefault("Hosts", new ArrayList<>(Arrays.asList("n0toh", "pmahcgop")));
        config.addDefault("BannedPlayers", new ArrayList<>(Collections.singletonList("notoh")));
        config.addDefault("RoundsPerHalf", roundsPerHalf);
        config.addDefault("RoundsToWin", roundsToWin);
        config.addDefault("RandomizeSide", randomizeSide);
        config.addDefault("ExportStatsAtEnd", exportStatsAtEnd);
        config.addDefault("ShutdownOnExit", shutdownOnExit);

        config.options().copyDefaults(true);
        Main.getInstance().saveConfig();

        roundsPerHalf = config.getInt("RoundsPerHalf");
        map = config.getString("Map");
        privateLobby = config.getBoolean("PrivateLobby");
        privateServer = config.getBoolean("Private");
        whitelistedPlayers = config.getStringList("AllowedPlayers");
        blacklistedPlayers = config.getStringList("BannedPlayers");
        hosts = config.getStringList("Hosts");
        roundsToWin = config.getInt("RoundsToWin");
        randomizeSide = config.getBoolean("RandomizeSide");
        exportStatsAtEnd = config.getBoolean("ExportStatsAtEnd");
        shutdownOnExit = config.getBoolean("ShutdownOnExit");
        minPlayers = config.getInt("MinPlayers");
        maxPlayers = config.getInt("MaxPlayers");
    }

    private void log(String msg) {
        Main.getInstance().getLogger().log(Level.INFO, msg);
    }

    private void error(String msg) {
        Main.getInstance().getLogger().log(Level.SEVERE, msg);
    }

    public String getMap() {
        return map;
    }

    public List<String> getBlacklistedPlayers() {
        return blacklistedPlayers;
    }

    public List<String> getWhitelistedPlayers() {
        return whitelistedPlayers;
    }

    public List<String> getHosts() {
        return hosts;
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public int getRoundsPerHalf() {
        return roundsPerHalf;
    }

    public int getRoundsToWin() {
        return roundsToWin;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public boolean isPrivateServer() {
        return privateServer;
    }

    public boolean isPrivateLobby() {
        return privateLobby;
    }

    public boolean getShutdownOnExit() {
        return shutdownOnExit;
    }

    public boolean getRandomizeSide() {
        return randomizeSide;
    }

    public boolean getExportStatsAtEnd() {
        return exportStatsAtEnd;
        //todo implement
    }

    public void setRoundsPerHalf(int roundsPerHalf) {
        this.roundsPerHalf = roundsPerHalf;
        config.set("RoundsPerHalf", roundsPerHalf);
    }

    public void setRoundsToWin(int roundsToWin) {
        this.roundsToWin = roundsToWin;
        config.set("RoundsToWin", roundsToWin);
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
        config.set("MinPlayers", minPlayers);
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
        config.set("MaxPlayers", maxPlayers);
    }

    public void setMap(String map) {
        this.map = map;
        config.set("Map", map);
    }

    public void setPrivateLobby(boolean privateLobby) {
        this.privateLobby = privateLobby;
        config.set("PrivateLobby", privateLobby);
    }

    public void setShutdownOnExit(boolean shutdownOnExit) {
        this.shutdownOnExit = shutdownOnExit;
        config.set("ShutdownOnExit", shutdownOnExit);
    }

    public void setRandomizeSide(boolean randomizeSide) {
        this.randomizeSide = randomizeSide;
        config.set("RandomizeSide", randomizeSide);
    }

    public void setExportStatsAtEnd(boolean exportStatsAtEnd) {
        this.exportStatsAtEnd = exportStatsAtEnd;
        config.set("ExportStatsAtEnd", exportStatsAtEnd);
    }
}
