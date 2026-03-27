package net.daga.sunstriker;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SunSaveCommand implements CommandExecutor {

    private final Sunstriker plugin;
    private final Random random = new Random();

    public SunSaveCommand(Sunstriker plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.isOp()) return false;

        if (args.length < 6) {
            player.sendMessage("§cИспользование: /sun_save <x1> <y1> <z1> <x2> <y2> <z2>");
            return true;
        }

        try {
            World world = player.getWorld();
            int x1 = Integer.parseInt(args[0]);
            int y1 = Integer.parseInt(args[1]);
            int z1 = Integer.parseInt(args[2]);
            int x2 = Integer.parseInt(args[3]);
            int y2 = Integer.parseInt(args[4]);
            int z2 = Integer.parseInt(args[5]);

            saveAndPopulate(new Location(world, x1, y1, z1), new Location(world, x2, y2, z2), "sun_temple");
            player.sendMessage("§6[Sunstriker] §fСтруктура сохранена. Босс: 300HP, Шлем, Санстрайк.");

        } catch (NumberFormatException e) {
            player.sendMessage("§cКоординаты должны быть числами!");
        }

        return true;
    }

    private void saveAndPopulate(Location l1, Location l2, String fileName) {
        int minX = Math.min(l1.getBlockX(), l2.getBlockX());
        int minY = Math.min(l1.getBlockY(), l2.getBlockY());
        int minZ = Math.min(l1.getBlockZ(), l2.getBlockZ());
        int maxX = Math.max(l1.getBlockX(), l2.getBlockX());
        int maxY = Math.max(l1.getBlockY(), l2.getBlockY());
        int maxZ = Math.max(l1.getBlockZ(), l2.getBlockZ());

        World world = l1.getWorld();
        List<org.bukkit.entity.Entity> spawnedEntities = new ArrayList<>();

        // 1. ПОИСК БЕЗОПАСНОГО МЕСТА ДЛЯ БОССА (ЦЕНТР)
        Location bossLoc = new Location(world,
                minX + (maxX - minX) / 2.0,
                minY + 1,
                minZ + (maxZ - minZ) / 2.0
        );

        // Поднимаем, если внутри блоков
        while (bossLoc.getBlock().getType().isSolid() && bossLoc.getY() < maxY) {
            bossLoc.add(0, 1, 0);
        }

        // СПАВН И НАСТРОЙКА БОССА
        Zombie boss = (Zombie) world.spawnEntity(bossLoc, EntityType.ZOMBIE);
        boss.setCustomName("§6Солнечный Жрец");
        boss.setCustomNameVisible(true);
        boss.setPersistent(true);
        boss.setRemoveWhenFarAway(false);

        // ХП (300)
        var healthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(300.0);
            boss.setHealth(300.0);
        }

        // ЭКИПИРОВКА
        ItemStack helmet = new ItemStack(Material.GOLDEN_HELMET);
        ItemMeta meta = helmet.getItemMeta();
        meta.setUnbreakable(true);
        helmet.setItemMeta(meta);
        boss.getEquipment().setHelmet(helmet); // Чтобы не горел
        boss.getEquipment().setHelmetDropChance(0.0f);

        // ВЫДАЕМ САНСТРАЙК (Используем метод вашего предмета)
        // Если SunstrikerItem имеет метод getItem(), вызываем его
        boss.getEquipment().setItemInMainHand(new SunstrikerItem(this.plugin).getItem());
        boss.getEquipment().setItemInMainHandDropChance(0.1f);

        spawnedEntities.add(boss);

        // 2. СТРАЖИ
        for (int x = minX; x <= maxX; x += 5) {
            for (int z = minZ; z <= maxZ; z += 5) {
                if (random.nextInt(10) < 2) {
                    Location sLoc = new Location(world, x, minY + 1, z);
                    if (sLoc.getBlock().getType() == Material.AIR) {
                        WitherSkeleton skel = (WitherSkeleton) world.spawnEntity(sLoc, EntityType.WITHER_SKELETON);
                        skel.setCustomName("§8Хранитель Солнца");
                        skel.setRemoveWhenFarAway(false);
                        skel.setPersistent(true);
                        spawnedEntities.add(skel);
                    }
                }
            }
        }

        // 3. СОХРАНЕНИЕ (Захватит всех настроенных мобов)
        saveToNBT(world, minX, minY, minZ, maxX, maxY, maxZ, fileName);

        // 4. ОЧИСТКА (удаляем временных мобов из мира после сохранения в файл)
        spawnedEntities.forEach(org.bukkit.entity.Entity::remove);
    }

    private void saveToNBT(World world, int x1, int y1, int z1, int x2, int y2, int z2, String name) {
        var nmsWorld = ((CraftWorld) world).getHandle();
        StructureTemplate template = new StructureTemplate();

        BlockPos origin = new BlockPos(x1, y1, z1);
        BlockPos size = new BlockPos(x2 - x1 + 1, y2 - y1 + 1, z2 - z1 + 1);

        template.fillFromWorld(nmsWorld, origin, size, true, java.util.Collections.emptyList());

        File structuresDir = new File(plugin.getDataFolder(), "structures");
        if (!structuresDir.exists()) structuresDir.mkdirs();

        try (FileOutputStream fos = new FileOutputStream(new File(structuresDir, name + ".nbt"))) {
            CompoundTag nbtData = template.save(new CompoundTag());
            NbtIo.writeCompressed(nbtData, fos);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}