package org.by1337.bauction.event;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.by1337.bauction.Main;
import org.by1337.blib.command.Command;
import org.by1337.blib.command.argument.ArgumentEnumValue;
import org.by1337.blib.command.argument.ArgumentFloat;
import org.by1337.blib.command.argument.ArgumentStrings;
import org.by1337.blib.configuration.YamlContext;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EventManager {
    private final Map<String, EventListener> listenerMap = new HashMap<>();
    private final Command<Event> command = new Command<>("");

    public EventManager(YamlContext context) {
        initCommands();
        context.getMap("listeners", YamlContext.class).forEach((k, v) -> {
            var event = EventType.valueOf(v.getAsString("event").toUpperCase(Locale.ENGLISH));
            var commands = v.getList("commands", String.class);
            listenerMap.put(k, new EventListener(event, commands, command));
        });
    }

    public void onEvent(Event event) {
        for (String s : listenerMap.keySet()) {
            EventListener listener = listenerMap.get(s);
            try {
                listener.onEvent(event);
            } catch (Throwable t) {
                Main.getMessage().error("An error occurred while event listener %s was processing the event.", t, s);
            }
        }
    }

    private void initCommands() {
        command.addSubCommand(new Command<Event>("[SOUND]")
                .argument(new ArgumentStrings<>("args"))
                .executor((event, args) -> {
                    String line = (String) args.getOrThrow("args", "Use: [SOUND] <sound> <?volume> <?pitch>");
                    String[] parts = line.trim().split("\\s+");
                    float volume = 1f;
                    float pitch = 1f;
                    try {
                        if (parts.length > 1) volume = Float.parseFloat(parts[1]);
                        if (parts.length > 2) pitch = Float.parseFloat(parts[2]);
                    } catch (NumberFormatException e) {
                        Main.getMessage().error("[SOUND] invalid volume/pitch in '%s'", line);
                    }
                    playSound(event.getPlayer(), parts[0], volume, pitch);
                })).addSubCommand(new Command<Event>("[MESSAGE]")
                .argument(new ArgumentStrings<>("message"))
                .executor((event, args) -> {
                    String msg = (String) args.getOrThrow("message", "Use: [MESSAGE] <message>");
                    Main.getMessage().sendMsg(event.getPlayer(), event.replace(msg));
                })).addSubCommand(new Command<Event>("[CONSOLE]")
                .argument(new ArgumentStrings<>("command"))
                .executor((event, args) -> {
                    String msg = (String) args.getOrThrow("command", "Use: [CONSOLE] <command>");
                    Bukkit.getScheduler().runTask(Main.getInstance(), () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), Main.getMessage().messageBuilder(event.replace(msg))));
                })).addSubCommand(new Command<Event>("[PLAYER]")
                .argument(new ArgumentStrings<>("command"))
                .executor((event, args) -> {
                    String msg = (String) args.getOrThrow("command", "Use: [PLAYER] <command>");
                    Bukkit.getScheduler().runTask(Main.getInstance(), () -> event.getPlayer().performCommand(Main.getMessage().messageBuilder(event.replace(msg))));
                }));
    }


    /**
     * Sound в 1.21.3+ больше не enum, поэтому звук проигрывается по строковому ключу
     * (minecraft:block.note_block.pling). Старые имена вида UI_BUTTON_CLICK тоже пробуем разобрать.
     */
    private static void playSound(org.bukkit.entity.Player player, String name, float volume, float pitch) {
        String key = name.trim();
        if (!key.contains(":") && !key.contains(".")) {
            key = legacySoundKey(key);
        }
        if (key == null) {
            Main.getMessage().error("Unknown sound '%s'. Use a key like minecraft:block.note_block.pling", name);
            return;
        }
        player.playSound(player.getLocation(), key.toLowerCase(Locale.ENGLISH), org.bukkit.SoundCategory.MASTER, volume, pitch);
    }

    private static String legacySoundKey(String legacyName) {
        try {
            Object sound = Sound.class.getMethod("valueOf", String.class).invoke(null, legacyName.toUpperCase(Locale.ENGLISH));
            if (sound instanceof org.bukkit.Keyed keyed) {
                return keyed.getKey().toString();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
