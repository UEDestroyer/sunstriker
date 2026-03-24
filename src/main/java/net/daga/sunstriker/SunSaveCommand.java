package net.daga.sunstriker;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

public class SunSaveCommand implements CommandExecutor {

    private final Sunstriker plugin;

    public SunSaveCommand(Sunstriker plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.isOp()) return false;
        // Проверка аргументов: /sun_save x1 y1 z1 x2 y2 z2
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

            Location loc1 = new Location(world, x1, y1, z1);
            Location loc2 = new Location(world, x2, y2, z2);

            saveAndPopulate(loc1, loc2, "sun_temple");
            player.sendMessage("§6[Sunstriker] §fСтруктура 'sun_temple' сохранена в файл!");

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

        Random random = new Random();
        int counter = 0;

        // 1. Расстановка визер-скелетов
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    counter++;
                    // Каждый 20-й блок в цикле
                    if (counter % 20 == 0) {
                        Location spawnLoc = new Location(l1.getWorld(), x, y + 1, z);
                        if (spawnLoc.getBlock().getType().isAir()) {
                            int groupSize = 1 + random.nextInt(3); // 1, 2 или 3
                            for (int i = 0; i < groupSize; i++) {
                                WitherSkeleton skeleton = (WitherSkeleton) l1.getWorld().spawnEntity(spawnLoc, EntityType.WITHER_SKELETON);
                                skeleton.setCustomName("§8Хранитель Солнца");
                            }
                        }
                    }
                }
            }
        }

        // 2. Сохранение в NBT файл через NMS
        saveToNBT(l1.getWorld(), minX, minY, minZ, maxX, maxY, maxZ, fileName);
    }

    private void saveToNBT(World world, int x1, int y1, int z1, int x2, int y2, int z2, String name) {
        var nmsWorld = ((CraftWorld) world).getHandle();
        StructureTemplate template = new StructureTemplate();

        BlockPos origin = new BlockPos(x1, y1, z1);
        BlockPos size = new BlockPos(x2 - x1 + 1, y2 - y1 + 1, z2 - z1 + 1);

        // Заполняем шаблон из мира (true - включая сущности, например рамки)
        template.fillFromWorld(nmsWorld, origin, size, true, null);

        File structuresDir = new File(plugin.getDataFolder(), "structures");
        if (!structuresDir.exists()) structuresDir.mkdirs();

        File outputFile = new File(structuresDir, name + ".nbt");

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            CompoundTag nbtData = template.save(new CompoundTag());
            NbtIo.writeCompressed(nbtData, fos);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}