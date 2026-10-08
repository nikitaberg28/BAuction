package org.by1337.bauction.util.auction;

import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionData;
import org.bukkit.plugin.Plugin;
import org.by1337.bauction.Main;
import org.by1337.blib.configuration.YamlContext;
import org.by1337.bauction.db.kernel.SellItem;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public class TagUtil {
    private static final Map<String, String> tagAliases = new HashMap<>();

    public static void loadAliases(Plugin plugin) {
        tagAliases.clear();

        YamlContext context;
        File file;
        file = new File(plugin.getDataFolder().getPath() + "/tagUtil.yml");
        if (!file.exists()) {
            plugin.saveResource("tagUtil.yml", true);
        }
        context = new YamlContext(YamlConfiguration.loadConfiguration(file));

        for (String tag : context.getHandle().getConfigurationSection("tags").getKeys(false)) {
            List<String> list = context.getList("tags." + tag, String.class);
            list.forEach(str -> tagAliases.put(str.toLowerCase(Locale.ENGLISH), tag));
        }

    }

    public static HashSet<String> getTags(@NotNull ItemStack itemStack) {
        List<String> list = new ArrayList<>();

        Material material = itemStack.getType();
        list.add(material.name());
        ItemMeta im = itemStack.getItemMeta();
        if (im != null) {
            try {
                if (im.hasCustomModelData()) {
                    list.add("customModelData:" + im.getCustomModelData());
                }
            } catch (Throwable t) {
                warnOnce("cmd", t);
            }
        }
        itemStack.getEnchantments().forEach((e, i) -> {
            list.add(e.getKey().getKey());
            list.add(e.getKey().getKey() + ":" + i);
        });
        list.addAll(getTags(material));

        if (im != null) {
            if (im instanceof PotionMeta potionMeta) {
                try {
                    addPotionTags(list, potionMeta);
                } catch (Throwable t) {
                    warnOnce("potion", t);
                }
            }
            list.addAll(parseTags(im.getPersistentDataContainer()));
            if (im instanceof EnchantmentStorageMeta enchantmentStorageMeta) {
                enchantmentStorageMeta.getStoredEnchants().keySet().forEach(e -> list.add(e.getKey().getKey()));
            }
        }

        list.replaceAll(String::toLowerCase);

        for (String str : list.toArray(new String[0])) {
            String s = tagAliases.get(str);
            if (s != null) {
                list.add(s);
            }
        }

        return new HashSet<>(list);
    }

    public static List<String> parseTags(PersistentDataContainer pdc) {
        return parseTagsFallback(pdc);
    }
    private static List<String> parseTagsFallback(PersistentDataContainer pdc) {
        List<String> list = new ArrayList<>();
        try {
            for (NamespacedKey key : pdc.getKeys()) {
                Object value = readPdcValue(pdc, key);
                list.add(value == null ? key.toString() : key + ":" + value);
            }
        } catch (Throwable t) {
            warnOnce("pdc-fallback", t);
        }
        return list;
    }

    private static Object readPdcValue(PersistentDataContainer pdc, NamespacedKey key) {
        try {
            if (pdc.has(key, PersistentDataType.STRING)) return pdc.get(key, PersistentDataType.STRING);
            if (pdc.has(key, PersistentDataType.INTEGER)) return pdc.get(key, PersistentDataType.INTEGER);
            if (pdc.has(key, PersistentDataType.LONG)) return pdc.get(key, PersistentDataType.LONG);
            if (pdc.has(key, PersistentDataType.DOUBLE)) return pdc.get(key, PersistentDataType.DOUBLE);
            if (pdc.has(key, PersistentDataType.FLOAT)) return pdc.get(key, PersistentDataType.FLOAT);
            if (pdc.has(key, PersistentDataType.SHORT)) return pdc.get(key, PersistentDataType.SHORT);
            if (pdc.has(key, PersistentDataType.BYTE)) return pdc.get(key, PersistentDataType.BYTE);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void addPotionTags(List<String> list, PotionMeta potionMeta) {
        for (PotionEffect potionEffect : potionMeta.getCustomEffects()) {
            PotionEffectType type = potionEffect.getType();
            String legacy = null;
            try {
                legacy = type.getName();
            } catch (Throwable ignored) {
            }
            String key = null;
            if (type instanceof Keyed keyed) {
                try {
                    key = keyed.getKey().getKey();
                } catch (Throwable ignored) {
                }
            }
            if (legacy != null) {
                list.add(legacy);
                list.add(legacy + ":" + potionEffect.getAmplifier());
            }
            if (key != null && !key.equalsIgnoreCase(legacy)) {
                list.add(key);
                list.add(key + ":" + potionEffect.getAmplifier());
            }
        }

        PotionData base = null;
        try {
            base = potionMeta.getBasePotionData();
        } catch (Throwable ignored) {
        }
        if (base != null && base.getType() != null) {
            list.add(base.getType().name());
            if (base.isUpgraded()) {
                list.add(base.getType().name() + ":1");
            }
            return;
        }
        try {
            Object type = PotionMeta.class.getMethod("getBasePotionType").invoke(potionMeta);
            String name = null;
            if (type instanceof Enum<?> en) {
                name = en.name();
            } else if (type instanceof Keyed keyed) {
                name = keyed.getKey().getKey();
            }
            if (name != null) list.add(name);
        } catch (Throwable ignored) {
        }
    }

    private static final Set<String> WARNED = Collections.synchronizedSet(new HashSet<>());

    private static void warnOnce(String id, Throwable t) {
        if (WARNED.add(id)) {
            Main.getMessage().error("TagUtil: ошибка (%s), используется безопасный режим: %s", id, String.valueOf(t));
        }
    }

    public static List<String> getTags(Material material) {
        List<String> list = new ArrayList<>();
        if (material.isFlammable()) list.add(Tags.IS_FLAMMABLE.getTag());
        if (material.isBurnable()) list.add(Tags.IS_BURNABLE.getTag());
        if (material.isFuel()) list.add(Tags.IS_FUEL.getTag());
        if (material.hasGravity()) list.add(Tags.HAS_GRAVITY.getTag());
        if (material.isSolid()) list.add(Tags.IS_SOLID.getTag());
        if (material.isRecord()) list.add(Tags.IS_RECORD.getTag());
        if (material.isEdible()) list.add(Tags.IS_EDIBLE.getTag());
        if (material.isBlock()) list.add(Tags.IS_BLOCK.getTag());
        return list;
    }

    public static boolean matchesCategory(Category category, SellItem sellItem) {
        if (category.tags().contains("any")) return true;
        for (String s : sellItem.getTags()) {
            if (category.tags().contains(s))
                return true;
        }
        return false;
    }

    public enum Tags {
        IS_FLAMMABLE("is_flammable"),
        IS_BURNABLE("is_burnable"),
        IS_FUEL("is_fuel"),
        HAS_GRAVITY("has_gravity"),
        IS_SOLID("is_solid"),
        IS_RECORD("is_record"),
        IS_EDIBLE("is_edible"),
        IS_BLOCK("is_block"),
        IS_TOOL("is_tool"),
        IS_ARMOR("is_armor"),
        IS_WEAPON("is_weapon"),
        IS_SPAWN_AGG("is_spawn_agg");

        private final String tag;

        Tags(String tag) {
            this.tag = tag;
        }

        public String getTag() {
            return tag;
        }
    }

}
