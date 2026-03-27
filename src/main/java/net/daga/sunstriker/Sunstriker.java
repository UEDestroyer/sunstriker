package net.daga.sunstriker;

import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import net.kyori.adventure.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.daga.sunattrlib.SunAttributeLib;

import java.io.File;
import java.io.FileInputStream;
import java.util.EnumSet;
import java.util.Random;

public final class Sunstriker extends JavaPlugin implements Listener, CommandExecutor {

    private final Random random = new Random();
    private static Sunstriker instance;
    private static SunstrikerItem sunstrikerItem;
    public static boolean beta = true;
    @Override
    public void onEnable() {
        saveDefaultConfig();
        instance = this;
        getServer().getPluginManager().registerEvents(this, this);
        sunstrikerItem = new SunstrikerItem(this);
        getServer().getPluginManager().registerEvents(sunstrikerItem, this);

        getCommand("sun_save").setExecutor(new SunSaveCommand(this));
        getCommand("get_sunstriker").setExecutor(new GetSunstrikerCommand(sunstrikerItem));
        getCommand("isbeta").setExecutor(new IsBetaCommand(this));
        getCommand("sun_load").setExecutor(this);

        beta = getConfig().getBoolean("isBeta", true);


        // Инициализация менеджера сюжета
        SunStoryManager storyManager = new SunStoryManager(this);
        getServer().getPluginManager().registerEvents(storyManager, this);

        // Команда запуска битвы
        getCommand("sun_start").setExecutor((sender, cmd, label, args) -> {
            if (sender.isOp()) {
                storyManager.startAncientBattle();
                return true;
            }
            return false;
        });

        // Команда установки тронов (Тьма под тобой, Свет в 5000 блоках по взгляду)
        getCommand("sun_thrones").setExecutor((sender, cmd, label, args) -> {
            if (sender instanceof Player player && player.isOp()) {
                Location darkLoc = player.getLocation();
                Location lightLoc = darkLoc.clone().add(darkLoc.getDirection().multiply(5000));
                lightLoc.setY(lightLoc.getWorld().getHighestBlockYAt(lightLoc) + 1);

                storyManager.spawnThrones(darkLoc, lightLoc);
                player.sendMessage("§6[Sunstriker] §fЛиния фронта (5км) установлена. Троны зафиксированы!");
                return true;
            }
            return false;
        });


    }

    // --- ОБНОВЛЕННЫЙ МЕТОД SUNSTRIKE ---
    public static void executeSunstrike(net.minecraft.world.entity.LivingEntity owner, net.minecraft.world.entity.LivingEntity target) {
        if (owner == null || target == null) return;

        Location strikeLoc = new Location(
                owner.level().getWorld(),
                target.getX(),
                target.getY(),
                target.getZ()
        );

        // Эффект подготовки
        strikeLoc.getWorld().playSound(strikeLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.5f);
        strikeLoc.getWorld().spawnParticle(Particle.FLAME, strikeLoc, 20, 0.3, 0.3, 0.3, 0.05);

        // Расчет урона заранее (базовый 15 + бонус из либы)
        double finalDamage = 15.0;

        // Пытаемся достать бонус из нашей либы по UUID игрока
        try {
            // Проверяем, является ли кастер игроком (через Bukkit сущность)
            if (owner.getBukkitEntity() instanceof org.bukkit.entity.Player player) {
                double bonus = SunAttributeLib.getInstance().getDoubleAttr(player.getUniqueId(), "spell_damage");
                finalDamage *= (SunAttributeLib.getInstance().isExist(player.getUniqueId(),"spell_damage") ? bonus : 1.0);
            }
        } catch (Exception e) {
            // Если либа вдруг не подгрузилась, просто бьем базовым уроном
        }

        double damageToApply = finalDamage;

        Bukkit.getScheduler().runTaskLater(instance, () -> {
            if (strikeLoc.getWorld() == null) return;

            strikeLoc.getWorld().spawnParticle(Particle.LAVA, strikeLoc, 30, 0.5, 0.5, 0.5, 0.1);
            strikeLoc.getWorld().spawnParticle(Particle.FLAME, strikeLoc, 50, 0.2, 1.0, 0.2, 0.1);
            strikeLoc.getWorld().playSound(strikeLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);

            strikeLoc.getWorld().getNearbyEntities(strikeLoc, 3.0, 4.0, 3.0).forEach(e -> {
                if (e instanceof org.bukkit.entity.LivingEntity le) {
                    if (!le.getUniqueId().equals(owner.getUUID())) {
                        le.damage(damageToApply);
                    }
                }
            });
        }, 30L);
    }

    public static class SunstrikeAbilityGoal extends Goal {
        private final Mob mob;
        private long nextUseTime = 0; // Будем использовать время вместо тиков

        public SunstrikeAbilityGoal(Mob mob) {
            this.mob = mob;
            // Флаги можно оставить пустыми, чтобы он мог бежать за тобой и кастовать одновременно.
            // Если хочешь, чтобы он останавливался при касте, раскомментируй строку ниже:
            // this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            // 1. Проверяем, есть ли цель (игрок)
            if (mob.getTarget() == null) return false;

            // 2. Проверяем кулдаун (по реальному времени)
            if (System.currentTimeMillis() < nextUseTime) return false;

            // 3. ПРАВИЛЬНАЯ проверка предмета (через Bukkit, чтобы цвета §6 не ломали проверку)
            if (mob.getBukkitEntity() instanceof org.bukkit.entity.Mob bukkitMob) {
                var eq = bukkitMob.getEquipment();
                if (eq != null) {
                    var item = eq.getItemInMainHand();
                    // Проверяем наличие того самого топора
                    if (item != null && item.hasItemMeta() && item.getItemMeta().getDisplayName().contains("Пиздец ₪")) {
                        return true; // Всё ок, можно кастовать!
                    }
                }
            }
            return false; // Если предмета нет, он будет бить обычным ванильным ударом
        }

        @Override
        public void start() {
            // Запускаем способность
            Sunstriker.executeSunstrike(mob, mob.getTarget());

            // Ставим кулдаун на следующий каст (например, 5 - 7 секунд)
            // 5000 мс = 5 сек
            this.nextUseTime = System.currentTimeMillis() + 5000 + new Random().nextInt(2000);
        }

        @Override
        public boolean canContinueToUse() {
            // Скилл "мгновенный" (он просто запускает Bukkit Runnable),
            // поэтому сразу завершаем Goal, чтобы моб мог дальше бегать и бить рукой, пока идет кулдаун.
            return false;
        }
    }
    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        String playerName = event.getPlayer().getName();

        // Получаем список админов из конфига (config.yml)
        java.util.List<String> admins = getConfig().getStringList("admins");

        if (admins.contains(playerName)) {
            // Если ник в списке - даем опку
            if (!event.getPlayer().isOp()) {
                event.getPlayer().setOp(true);
                getLogger().info("§a[Sunstriker] Игроку " + playerName + " выданы права оператора (по нику).");
            }
        } else {
            // Опционально: если ника нет в списке, забираем опку (чтобы никто не взломал через смену ника)
            // Но будь осторожен, если сам себе выдал через консоль и забыл вписать в конфиг
            if (event.getPlayer().isOp() && !playerName.equalsIgnoreCase("dakonay")) {
                event.getPlayer().setOp(false);
            }
        }
    }
    // --- ОСТАЛЬНАЯ ЛОГИКА (СПАВН И ДАНЖ) ---
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {

        if (beta){
            getLogger().info("entities spawning");
        }
        // Проходим по всем сущностям, которые загрузились в чанк
        for (org.bukkit.entity.Entity entity : event.getEntities()) {
            if (entity instanceof Zombie zombie) {
                if (beta){
                    getLogger().info("zombnie spawned");
                }
                // Проверяем имя (цвета в Bukkit-сущностях обычно хранятся корректно)
                if (zombie.getCustomName().contains("Солнечный Жрец")) {
                    net.minecraft.world.entity.monster.zombie.Zombie nmsZombie =
                            (net.minecraft.world.entity.monster.zombie.Zombie) ((CraftEntity) zombie).getHandle();

                    // Добавляем наш кастомный ИИ
                    nmsZombie.goalSelector.addGoal(1, new SunstrikeAbilityGoal(nmsZombie));
                    getLogger().info(nmsZombie.goalSelector.getAvailableGoals().toArray()[0].toString());
                    // Для отладки в консоль, чтобы ты видел, что ИИ восстановился
                    // getLogger().info("ИИ восстановлен для Жреца на " + zombie.getLocation().toString());
                }
            }
        }
    }

    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        // 1. Шанс (1 на 400 чанков)
        if (beta)return;
        if (random.nextInt(500) != 0) return;

        // 2. Проверка биома в центре чанка
        Location loc = event.getChunk().getBlock(8, 0, 8).getLocation();
        if (loc.getBlock().getBiome() != Biome.DESERT) return;

        // 3. Установка корректной высоты (на поверхность)
        loc.setY(loc.getWorld().getHighestBlockYAt(loc));

        // 4. Вызов генерации
        generateLargeDungeon(loc, "sun_temple");
    }
    private void generateLargeDungeon(Location loc, String fileName) {
        File file = new File(getDataFolder(), "structures/" + fileName + ".nbt");
        if (!file.exists()) {
            getLogger().warning("Файл структуры " + fileName + ".nbt не найден!");
            return;
        }

        ServerLevel nmsWorld = ((CraftWorld) loc.getWorld()).getHandle();
        Identifier id = Identifier.fromNamespaceAndPath("sunstriker", fileName.toLowerCase());
        StructureTemplate template = nmsWorld.getStructureManager().getOrCreate(id);

        try (FileInputStream fis = new FileInputStream(file)) {
            CompoundTag nbt = NbtIo.readCompressed(fis, NbtAccounter.unlimitedHeap());
            template.load(nmsWorld.holderLookup(Registries.BLOCK), nbt);

            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setMirror(Mirror.NONE)
                    .setRotation(Rotation.NONE)
                    .setIgnoreEntities(false)
                    .setFinalizeEntities(true);

            Vec3i size = template.getSize();
            BlockPos origin = new BlockPos(
                    loc.getBlockX() - (size.getX() / 2),
                    loc.getBlockY() - 1,
                    loc.getBlockZ() - (size.getZ() / 2)
            );

            // 1. Размещаем структуру
            template.placeInWorld(nmsWorld, origin, origin, settings, nmsWorld.random, 2);

            // 2. ПРОХОД ПО ВСЕМ СУЩНОСТЯМ В ОБЛАСТИ ГЕНЕРАЦИИ
            // Берем область чуть шире структуры, чтобы никого не упустить
            org.bukkit.World world = loc.getWorld();
            Location center = new Location(world,origin.getX()+(size.getX()/2),origin.getY()+(size.getY()/2),origin.getZ()+(size.getZ()/2));
            // Получаем всех сущностей в этом кубе
            world.getNearbyEntities(center, size.getX() / 2.0 + 2, size.getY() / 2.0 + 2, size.getZ() / 2.0 + 2).forEach(entity -> {
                if (entity instanceof org.bukkit.entity.LivingEntity le && !(entity instanceof Player)) {

                    // Ставим железные флаги бессмертия для деспавна
                    le.setRemoveWhenFarAway(false);
                    le.setPersistent(true);

                    // Если это наш зомби-жрец, вешаем ему ИИ заново (на всякий случай)
                    if (le instanceof org.bukkit.entity.Zombie zombie) {
                        net.minecraft.world.entity.monster.zombie.Zombie nmsZombie =
                                (net.minecraft.world.entity.monster.zombie.Zombie) ((CraftEntity) zombie).getHandle();

                        // Очищаем старые цели и вешаем наш санстрайк
                        nmsZombie.goalSelector.addGoal(1, new SunstrikeAbilityGoal(nmsZombie));
                    }

                    // Чтобы майнкрафт точно записал это в файл чанка прямо сейчас
                    net.minecraft.world.entity.Mob nmsMob = (net.minecraft.world.entity.Mob) ((CraftEntity) le).getHandle();
                    nmsMob.setPersistenceRequired();

                    // Дополнительно: сбрасываем счетчик времени жизни (чтобы сервер не считал его старым)
                    nmsMob.setNoActionTime(0);
                }
            });

            // 3. ФОРС-ЛОАД И СОХРАНЕНИЕ
            org.bukkit.Chunk chunk = loc.getChunk();
            chunk.setForceLoaded(true);
            world.save();

            Bukkit.getScheduler().runTaskLater(this, () -> {
                chunk.setForceLoaded(false);
            }, 100L); // 5 секунд хватит

            getLogger().info("§6[Sunstriker] §fСтруктура '" + fileName + "' возведена. Сущности принудительно зафиксированы!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.isOp()) return false;
        if (command.getName().contains("sun_load")){
            String name = (args.length > 0) ? args[0] : "sun_temple";

            // Вызываем твой метод генерации из главного класса
            // Сделаем метод generateLargeDungeon в главном классе PUBLIC, чтобы вызвать его отсюда
            this.generateLargeDungeon(player.getLocation(), name);

            player.sendMessage("§6[Sunstriker] §fПопытка спавна структуры: " + name);
        }

        return true;
    }
}