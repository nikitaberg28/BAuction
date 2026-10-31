package org.by1337.bauction.command.impl;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
import java.util.Objects;
import java.util.Set;
public class HandCmd extends Command<CommandSender> {

    private final MenuLoader menuLoader;
    private final String homeMenuId;
    public HandCmd(String command, MenuLoader menuLoader, String homeMenuId) {
        super(command);
        this.menuLoader = menuLoader;
        this.homeMenuId = homeMenuId;
        requires(new RequiresPermission<>("bauc.search"));
        requires(sender -> sender instanceof Player);
        executor(this::execute);
    }

    private void execute(CommandSender sender, ArgumentMap<String, Object> args) throws CommandException {
        Player player = (Player) sender;

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()) {
            throw new CommandException(Lang.getMessage("item_in_hand_required"));
        }

        Set<String> tags = new HashSet<>();
        tags.add(inHand.getType().name().toLowerCase(Locale.ENGLISH));

        Category custom = Main.getCfg().getSorting().getAs("special.search", Category.class);
        custom.setSoft(true);
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