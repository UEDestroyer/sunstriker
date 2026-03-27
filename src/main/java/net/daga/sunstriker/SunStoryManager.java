package net.daga.sunstriker;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.*;

public class SunStoryManager implements Listener {

    private final Sunstriker plugin;
    private final Set<UUID> forcesOfDarkness = new HashSet<>();
    private final Set<UUID> spiesOfLight = new HashSet<>();
    private final Map<String, UUID> throneUUIDs = new HashMap<>();
    private boolean battleStarted = false;

    public SunStoryManager(Sunstriker plugin) {
        this.plugin = plugin;
        loadData();
    }

    public void startAncientBattle() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) return;

        Collections.shuffle(players);
        battleStarted = true;
        forcesOfDarkness.clear();
        spiesOfLight.clear();

        // Распределение: до 5 Тьма, следующие 3 (до 8-го) - шпионы
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (i < 5) {
                forcesOfDarkness.add(p.getUniqueId());
                sendSystemMessage(p, "§cуспешно вы были выбраны силами тьмы");
            } else if (i < 8) {
                spiesOfLight.add(p.getUniqueId());
                sendSystemMessage(p, "§eвы были выбраны шпионом сил света (отныне вы должны притворяться союзником до момента финального сражения)");
            }
        }

        saveData();

        // Сюжетные сообщения системы
        Bukkit.broadcastMessage("§7[Система]: §fЗапуск битвы древних...");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.broadcastMessage("§7[Система]: §fИспользование порядочности на изменение правил сделано с помощью сил тьмы.");
            Bukkit.broadcastMessage("§7[Система]: §fОтныне герои сил света не способны возродиться.");
            Bukkit.broadcastMessage("§c[Цель 1]: §fУбить героя Инвокер Анализ.");
            Bukkit.broadcastMessage("§c[Анализ]: §fТребуется разозлить его и убить всех учеников.");
            Bukkit.broadcastMessage("§c[Цель 1.1]: §fХранитель солнца (носитель санстрайка).");
        }, 100L);
    }

    private void sendSystemMessage(Player p, String msg) {
        p.sendMessage("§7[Система]: " + msg);
    }

    public void spawnThrones(Location darkLoc, Location lightLoc) {
        removeOldThrones();

        // Поворачиваем их лицами друг к другу через 5000 блоков
        Vector directionToLight = lightLoc.toVector().subtract(darkLoc.toVector()).normalize();
        Vector directionToDark = darkLoc.toVector().subtract(lightLoc.toVector()).normalize();

        darkLoc.setDirection(directionToLight);
        lightLoc.setDirection(directionToDark);

        // Принудительная загрузка чанков (Force Load)
        darkLoc.getChunk().setForceLoaded(true);
        lightLoc.getChunk().setForceLoaded(true);

        Zombie dark = createThrone(darkLoc, "§0Трон Сил Тьмы");
        Zombie light = createThrone(lightLoc, "§eТрон Сил Света");

        throneUUIDs.put("DARK", dark.getUniqueId());
        throneUUIDs.put("LIGHT", light.getUniqueId());

        saveData();
    }

    private Zombie createThrone(Location loc, String name) {
        loc.getChunk().load();
        Zombie zombie = loc.getWorld().spawn(loc, Zombie.class);
        zombie.setCustomName(name);
        zombie.setCustomNameVisible(true);
        zombie.setAI(false);
        zombie.setInvulnerable(true);

        // Физика: нельзя толкать
        zombie.setCollidable(false);

        zombie.getAttribute(Attribute.MAX_HEALTH).setBaseValue(2000.0);
        zombie.setHealth(2000.0);
        zombie.setPersistent(true);
        zombie.setRemoveWhenFarAway(false);

        // NMS фиксация для сохранения в чанке
        net.minecraft.world.entity.Mob nmsMob = (net.minecraft.world.entity.Mob) ((CraftEntity) zombie).getHandle();
        nmsMob.setPersistenceRequired();
        nmsMob.setNoActionTime(0);

        return zombie;
    }

    @EventHandler
    public void onThroneDamage(EntityDamageEvent event) {
        if (throneUUIDs.containsValue(event.getEntity().getUniqueId())) {
            // Если трон помечен как неуязвимый в метаданных/флаге
            if (event.getEntity().isInvulnerable()) {
                event.setCancelled(true);
            }
        }
    }

    public void makeThroneVulnerable(String team) {
        UUID uuid = throneUUIDs.get(team.toUpperCase());
        if (uuid != null) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) {
                e.setInvulnerable(false);
                Bukkit.broadcastMessage("§7[Система]: §4" + e.getCustomName() + " §fтеперь уязвим!");
            }
        }
    }

    public void saveData() {
        FileConfiguration config = plugin.getConfig();
        config.set("story.battleStarted", battleStarted);
        config.set("story.darkness", forcesOfDarkness.stream().map(UUID::toString).toList());
        config.set("story.spies", spiesOfLight.stream().map(UUID::toString).toList());
        config.set("story.thrones.DARK", throneUUIDs.get("DARK") != null ? throneUUIDs.get("DARK").toString() : null);
        config.set("story.thrones.LIGHT", throneUUIDs.get("LIGHT") != null ? throneUUIDs.get("LIGHT").toString() : null);
        plugin.saveConfig();
    }

    private void loadData() {
        FileConfiguration config = plugin.getConfig();
        battleStarted = config.getBoolean("story.battleStarted", false);
        config.getStringList("story.darkness").forEach(s -> forcesOfDarkness.add(UUID.fromString(s)));
        config.getStringList("story.spies").forEach(s -> spiesOfLight.add(UUID.fromString(s)));
        if (config.contains("story.thrones.DARK"))
            throneUUIDs.put("DARK", UUID.fromString(config.getString("story.thrones.DARK")));
        if (config.contains("story.thrones.LIGHT"))
            throneUUIDs.put("LIGHT", UUID.fromString(config.getString("story.thrones.LIGHT")));
    }

    private void removeOldThrones() {
        throneUUIDs.values().forEach(uuid -> {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) {
                e.getLocation().getChunk().setForceLoaded(false);
                e.remove();
            }
        });
        throneUUIDs.clear();
    }
}