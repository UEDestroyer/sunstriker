package net.daga.sunstriker;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GetSunstrikerCommand implements CommandExecutor {

    private final SunstrikerItem itemManager;

    public GetSunstrikerCommand(SunstrikerItem itemManager) {
        this.itemManager = itemManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Проверяем, что команду ввел игрок
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭту команду может использовать только игрок!");
            return true;
        }

        // Проверка прав (опционально, но полезно)
        if (!player.isOp()) {
            player.sendMessage("§c₪ §7У вас недостаточно прав для владения силой солнца.");
            return true;
        }
        // Выдаем предмет через метод, который мы создали в классе SunstrikerItem
        player.getInventory().addItem(itemManager.getItem());
        player.sendMessage("§6[Sunstriker] §fВы получили §eПосох Солнцестояния֎");

        return true;
    }
}