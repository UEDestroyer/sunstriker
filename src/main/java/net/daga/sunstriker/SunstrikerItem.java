package net.daga.sunstriker;

import net.minecraft.world.entity.Mob;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;

public class SunstrikerItem implements Listener {

    private final Sunstriker plugin;
    private final Material itemType = Material.GOLDEN_AXE; // Тип предмета

    public SunstrikerItem(Sunstriker plugin) {
        this.plugin = plugin;
    }

    /**
     * Метод для выдачи предмета игроку (можно вызвать из команды)
     */
    public ItemStack getItem() {
        ItemStack item = new ItemStack(itemType);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Пиздец ₪");
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Проверяем, наш ли это предмет и нажата ли ПКМ
        if (item == null || item.getType() != itemType) return;
        if (!item.getItemMeta().hasDisplayName() || !item.getItemMeta().getDisplayName().contains("Пиздец ₪")) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // Проверка перезарядки (7 секунд = 140 тиков)
        if (player.hasCooldown(itemType)) {
            player.sendMessage("§cСпособность еще не готова!");
            return;
        }

        // Ищем цель, на которую смотрит игрок (в радиусе 30 блоков)
        LivingEntity target = getTargetEntity(player, 30);

        if (target != null) {
            // Превращаем цель в NMS LivingEntity
            net.minecraft.world.entity.LivingEntity nmsTarget = ((CraftLivingEntity) target).getHandle();

            // Вызываем Sunstrike (в качестве владельца передаем null, так как кастует не Mob)
            // Но чтобы избежать ошибок в методе executeSunstrike, мы передадим фиктивного "владельца"
            // или немного адаптируем вызов.

            Sunstriker.executeSunstrike((Mob) player, nmsTarget);

            // Устанавливаем визуальную перезарядку в 7 секунд
            player.setCooldown(itemType, 140);

            player.sendMessage("§eВы призываете гнев солнца на " + target.getName() + "!");
        } else {
            player.sendMessage("§7Цель не найдена.");
        }
    }

    // Вспомогательный метод для поиска цели взглядом
    private LivingEntity getTargetEntity(Player player, int range) {
        return player.getWorld().getNearbyEntities(player.getEyeLocation(), range, range, range).stream()
                .filter(e -> e instanceof LivingEntity && e != player)
                .map(e -> (LivingEntity) e)
                .filter(le -> player.hasLineOfSight(le))
                .min((e1, e2) -> Double.compare(e1.getLocation().distanceSquared(player.getLocation()),
                        e2.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);
    }
}