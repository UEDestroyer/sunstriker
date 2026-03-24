package net.daga.sunstriker;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.Random;

public final class Sunstriker extends JavaPlugin implements Listener {

    private final Random random = new Random();
    private static Sunstriker instance;
    private static SunstrikerItem sunstrikerItem;
    @Override
    public void onEnable() {
        instance = this;
        getServer().getPluginManager().registerEvents(this, this);
        sunstrikerItem = new SunstrikerItem(this);
        getServer().getPluginManager().registerEvents(sunstrikerItem, this);
        // Регистрация команды сохранения
        getCommand("sun_save").setExecutor(new SunSaveCommand(this));
        getCommand("get_sunstriker").setExecutor(new GetSunstrikerCommand(sunstrikerItem));
    }

    // --- ОТДЕЛЬНЫЙ МЕТОД SUNSTRIKE ---
    /**
     * @param owner Тот, кто кастует (NMS Mob)
     * @param target Тот, в кого целимся (NMS LivingEntity)
     */
    public static void executeSunstrike(Mob owner, net.minecraft.world.entity.LivingEntity target) {
        if (owner == null || target == null) return;

        // Переводим координаты NMS в Bukkit Location
        Location strikeLoc = new Location(
                owner.level().getWorld(),
                target.getX(),
                target.getY(),
                target.getZ()
        );

        // 1. Визуальный эффект подготовки (предупреждение)
        strikeLoc.getWorld().playSound(strikeLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.5f);
        strikeLoc.getWorld().spawnParticle(Particle.FLAME, strikeLoc, 40, 0.2, 0.1, 0.2, 0.05);

        // 2. Задержка перед основным ударом (1.5 сек = 30 тиков)
        Bukkit.getScheduler().runTaskLater(instance, () -> {
            // Визуал самого удара
            strikeLoc.getWorld().spawnParticle(Particle.FLASH, strikeLoc, 1);
            strikeLoc.getWorld().spawnParticle(Particle.LAVA, strikeLoc, 25, 0.2, 1, 0.2, 0.1);
            strikeLoc.getWorld().playSound(strikeLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);

            // Нанесение урона всем в радиусе 2.5 блоков (кроме самого кастера)
            strikeLoc.getWorld().getNearbyEntities(strikeLoc, 2.5, 3, 2.5).forEach(e -> {
                if (e instanceof LivingEntity le && !e.getUniqueId().equals(owner.getUUID())) {
                    le.damage(10.0);
                }
            });
        }, 30L);
    }

    // --- ОБНОВЛЕННЫЙ GOAL С ИСПОЛЬЗОВАНИЕМ МЕТОДА ---
    public static class SunstrikeAbilityGoal extends Goal {
        private final Mob mob;
        private int cooldown = 0;

        public SunstrikeAbilityGoal(Mob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return mob.getTarget() != null && cooldown <= 0;
        }

        @Override
        public void start() {
            this.cooldown = 120 + new Random().nextInt(40); // Кулдаун 6-8 секунд
            // Вызываем наш отдельный метод
            Sunstriker.executeSunstrike(mob, mob.getTarget());
        }

        @Override
        public void tick() {
            if (cooldown > 0) cooldown--;
        }
    }

    // --- ОСТАЛЬНАЯ ЛОГИКА (СПАВН И ДАНЖ) ---
    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Zombie zombie) {
            net.minecraft.world.entity.monster.zombie.Zombie nmsZombie =
                    (net.minecraft.world.entity.monster.zombie.Zombie) ((CraftEntity) zombie).getHandle();
            nmsZombie.goalSelector.addGoal(1, new SunstrikeAbilityGoal(nmsZombie));
            zombie.setCustomName("§6Солнечный Жрец");
            zombie.setCustomNameVisible(true);
        }
    }

    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        if (random.nextInt(400) == 0) {
            Location loc = event.getChunk().getBlock(8, 0, 8).getLocation();
            loc.setY(loc.getWorld().getHighestBlockYAt(loc) - 8);
            generateDungeon(loc);
        }
    }

    private void generateDungeon(Location loc) {
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = -2; z <= 2; z++) {
                    Location target = loc.clone().add(x, y, z);
                    target.getBlock().setType(
                            (y == 0 || y == 4 || x == -2 || x == 2 || z == -2 || z == 2)
                                    ? Material.MOSSY_COBBLESTONE : Material.AIR
                    );
                }
            }
        }
        loc.getWorld().spawn(loc.clone().add(0, 1, 0), Zombie.class);
    }
}