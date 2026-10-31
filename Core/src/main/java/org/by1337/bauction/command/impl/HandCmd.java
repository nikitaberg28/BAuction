package org.by1337.bauction.command.impl;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.by1337.bauction.Main;
import org.by1337.bauction.lang.Lang;
import org.by1337.bauction.menu.HomeMenu;
import org.by1337.bauction.util.auction.Category;
import org.by1337.blib.command.Command;
import org.by1337.blib.command.CommandException;
import org.by1337.blib.command.argument.ArgumentMap;
import org.by1337.blib.command.requires.RequiresPermission;
import org.by1337.bmenu.menu.MenuLoader;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * /ah hand - поиск лотов, похожих на предмет в руке: тот же материал и те же зачарования.
 * <p>
 * Команда только читает предмет в руке (без изменений и без клонирования в инвентарь)
 * и открывает главное меню с категорией "Поиск". Покупка, продажа и логика самого меню не затрагиваются.
 */
public class HandCmd extends Command<CommandSender> {

    private final MenuLoader menuLoader;
    private final String homeMenuId;

    public HandCmd(String command, MenuLoader menuLoader, String homeMenuId) {
        super(command);
        this.menuLoader = menuLoader;
        this.homeMenuId = homeMenuId;
        requires(new RequiresPermission<>("bauc.search")); // та же пермиссия, что и у /ah search
        requires(sender -> sender instanceof Player);
        executor(this::execute);
    }

    private void execute(CommandSender sender, ArgumentMap<String, Object> args) throws CommandException {
        Player player = (Player) sender;

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()) {
            throw new CommandException(Lang.getMessage("item_in_hand_required"));
        }

        // Теги строятся в том же формате, что и TagUtil.getTags(): всё в нижнем регистре
        // материал (diamond_sword), зачарования с уровнем (sharpness:5),
        // для книг - ключ чара без уровня (так их хранит TagUtil)
        Set<String> tags = new HashSet<>();
        tags.add(inHand.getType().name().toLowerCase(Locale.ENGLISH));

        for (Map.Entry<Enchantment, Integer> e : inHand.getEnchantments().entrySet()) {
            tags.add((e.getKey().getKey().getKey() + ":" + e.getValue()).toLowerCase(Locale.ENGLISH));
        }
        ItemMeta meta = inHand.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            for (Enchantment e : storageMeta.getStoredEnchants().keySet()) {
                tags.add(e.getKey().getKey().toLowerCase(Locale.ENGLISH));
            }
        }

        // Строгий режим: у лота должны быть ВСЕ эти теги (тот же материал и те же чары).
        // Без чаров тег один, поэтому результат равен /ah search <название>
        Category custom = Main.getCfg().getSorting().getAs("special.search", Category.class);
        custom.setSoft(false);
        custom.setTags(tags);

        var menu = menuLoader.getMenu(homeMenuId);
        Objects.requireNonNull(menu, "Menu " + homeMenuId + " not found!");
        var m = menu.create(player, null);

        if (m instanceof HomeMenu homeMenu0) {
            homeMenu0.setCustom(custom);
            homeMenu0.getCategories().add(custom);
            Collections.sort(homeMenu0.getCategories());
        }
        m.open();
    }
}
