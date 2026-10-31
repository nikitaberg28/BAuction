package org.by1337.bauction.db.kernel;

import org.by1337.bauction.Main;

import java.util.*;
public final class RelistService {
    public static final long COOLDOWN_MS = 7L * 60L * 1000L;
    private static final Map<UUID, Long> LAST_USE = new HashMap<>();

    private RelistService() {
    }

    public record Result(int relisted, int skippedByLimit, int failed, boolean onCooldown, long cooldownLeftMs, int limit) {
    }

    public static synchronized Result relistActive(User user) {
        UUID uuid = user.getUuid();
        long now = System.currentTimeMillis();
        Long last = LAST_USE.get(uuid);
        if (last != null && now - last < COOLDOWN_MS) {
            return new Result(0, 0, 0, true, COOLDOWN_MS - (now - last), 0);
        }
        if (LAST_USE.size() > 1000) {
            LAST_USE.values().removeIf(t -> now - t >= COOLDOWN_MS);
        }

        FileDataBase db = Main.getStorage();

        int limit = Math.max(0, Main.getCfg().getMaxSlots() + user.getExternalSlots());
        long duration = Main.getCfg().getDefaultSellTime() + user.getExternalSellTime();

        List<SellItem> items = new ArrayList<>();
        db.forEachSellItemsByUser(items::add, uuid);
        items.sort(Comparator.comparingLong((SellItem i) -> i.timeListedForSale)
                .thenComparing(i -> i.uniqueName.getKey()));

        int total = items.size();
        int toProcess = Math.min(limit, total);
        if (toProcess <= 0) {
            return new Result(0, Math.max(0, total - toProcess), 0, false, 0, limit);
        }
        LAST_USE.put(uuid, now);

        int relisted = 0;
        int failed = 0;
        long lastTime = 0;

        for (int x = 0; x < toProcess; x++) {
            SellItem old = items.get(x);
            long time = Math.max(System.currentTimeMillis(), lastTime + 1);
            lastTime = time;

            SellItem fresh;
            try {
                fresh = SellItem.builder()
                        .item(old.item)
                        .sellerName(old.sellerName)
                        .server(old.server)
                        .sellerUuid(old.sellerUuid)
                        .price(old.price)
                        .saleByThePiece(old.saleByThePiece)
                        .tags(old.tags)
                        .timeListedForSale(time)
                        .removalDate(time + duration)
                        .uniqueName(Main.getUniqueNameGenerator().getNextCombination())
                        .material(old.material)
                        .amount(old.amount)
                        .priceForOne(old.priceForOne)
                        .sellFor(new HashSet<>(old.sellFor))
                        .compressed(old.compressed)
                        .build();
            } catch (Throwable t) {
                failed++;
                Main.getMessage().error(t);
                continue;
            }

            db.lock.writeLock().lock();
            try {
                try {
                    db.removeSellItem(old.uniqueName);
                } catch (Throwable t) {
                    failed++;
                    continue;
                }

                try {
                    db.addSellItem(fresh);
                    relisted++;
                } catch (Throwable t) {
                    failed++;
                    Main.getMessage().error(t);
                    boolean freshGone = true;
                    if (db.hasSellItem(fresh.uniqueName)) {
                        try {
                            db.removeSellItem(fresh.uniqueName);
                        } catch (Throwable t2) {
                            freshGone = false;
                            Main.getMessage().error(t2);
                        }
                    }
                    if (freshGone) {
                        try {
                            db.addSellItem(old);
                        } catch (Throwable t3) {
                            Main.getMessage().error("RELIST: FAILED TO RESTORE item of %s! Serialized item: %s", old.sellerName, old.item);
                            Main.getMessage().error(t3);
                        }
                    } else {
                        Main.getMessage().error("RELIST: new item %s is kept, old item %s NOT restored to avoid duplication", fresh.uniqueName, old.uniqueName);
                    }
                }
            } finally {
                db.lock.writeLock().unlock();
            }
        }
        return new Result(relisted, Math.max(0, total - toProcess), failed, false, 0, limit);
    }
}

// вась вась...лети далеко! как папино пальто! как мамина рука