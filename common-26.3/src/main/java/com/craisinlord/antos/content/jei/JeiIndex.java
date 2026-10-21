package com.craisinlord.antos.content.jei;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.antazon.AntazonData;
import com.craisinlord.antos.content.antazon.AntazonDeals;
import com.craisinlord.antos.content.antazon.AntazonSellData;
import com.craisinlord.antos.content.antazon.AntazonServerData;
import com.craisinlord.antos.content.antazon.AntazonService;
import com.craisinlord.antos.content.antazon.AntazonUnlocks;
import com.craisinlord.antos.content.computer.ComputerTaskProgress;
import com.craisinlord.antos.content.computer.ComputerTasks;
import com.craisinlord.antos.content.computer.ComputerWorkspaceData;
import com.craisinlord.antos.content.computer.DiskProfileState;
import com.craisinlord.antos.content.computer.terminal.TerminalCommands;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.AnternetAccountHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

public final class JeiIndex {
    private static final long MINECRAFT_DAY = 24000L;
    private static final int MAX_POOL_DEPTH = 8;

    private JeiIndex() { }

    public static Key key() {
        boolean prices = AntOSSettings.jeiAntazonPrices() && AntOSSettings.appEnabled("ANTAZON");
        boolean sell = AntOSSettings.jeiSellValues() && AntOSSettings.appEnabled("ANTAZON");
        boolean tasks = AntOSSettings.jeiTaskRewards() && AntOSSettings.appEnabled("TASKS");
        return new Key(prices ? AntazonData.version() : 0L,
                sell ? System.identityHashCode(AntazonSellData.snapshot()) : 0,
                tasks ? System.identityHashCode(ComputerGuideData.tasks()) : 0,
                tasks ? System.identityHashCode(ComputerGuideData.taskRewardPools()) : 0,
                prices, sell, tasks);
    }

    public static Definitions definitions(Key key, long version) {
        List<AntazonData.Product> products = new ArrayList<>();
        List<AntazonSellData.Rule> rules = new ArrayList<>();
        List<ComputerGuideData.Task> tasks = new ArrayList<>();
        Map<Identifier, String> titles = new LinkedHashMap<>();
        Map<Identifier, String> disks = new LinkedHashMap<>();
        JsonObject root = new JsonObject();
        root.addProperty("version", version);
        JsonArray listingRows = new JsonArray();
        if (key.prices()) for (AntazonData.Product product : AntazonData.products()) {
            if (!product.enabled()) continue;
            products.add(product);
            listingRows.add(listing(product, titles, disks));
        }
        root.add("listings", listingRows);
        JsonArray sellRows = new JsonArray();
        if (key.sell()) for (AntazonSellData.Rule rule : AntazonSellData.rules()) {
            rules.add(rule);
            sellRows.add(sell(rule, titles, disks));
        }
        root.add("sell", sellRows);
        JsonArray taskRows = new JsonArray();
        if (key.tasks()) for (ComputerGuideData.Task task : ComputerGuideData.tasks()) {
            JsonObject row = task(task, titles);
            if (row == null) continue;
            tasks.add(task);
            taskRows.add(row);
        }
        root.add("tasks", taskRows);
        JsonObject titleRows = new JsonObject();
        titles.forEach((id, title) -> titleRows.addProperty(id.toString(), title));
        root.add("titles", titleRows);
        JsonObject diskRows = new JsonObject();
        disks.forEach((id, title) -> diskRows.addProperty(id.toString(), title));
        root.add("disks", diskRows);
        return new Definitions(version, List.copyOf(products), List.copyOf(rules), List.copyOf(tasks), root.toString());
    }

    public static String state(ServerPlayer player, Definitions definitions) {
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.level().getServer());
        ComputerWorkspaceData.AccountInfo session = AnternetAccountHandler.session(player);
        ComputerWorkspaceData.AccountInfo account = session != null ? session : data.faceIdAccount(player.getUUID());
        UUID accountId = account == null ? null : account.accountId();
        ComputerTaskProgress progress = accountId == null ? new ComputerTaskProgress()
                : data.effectiveProgress(accountId, data.progress(account.workspaceId()));
        DiskProfileState diskState = accountId == null || data.account(accountId) == null ? null : data.diskState(accountId);
        Predicate<Identifier> taskDone = progress::isComplete;
        Predicate<Identifier> diskDone = disk -> diskState != null && diskState.activated(disk);
        Predicate<Identifier> commandDone = command -> diskState != null && diskState.commandRun(command);
        long gameTime = player.level().getServer().overworld().getGameTime();
        long day = gameTime / MINECRAFT_DAY;
        JsonObject root = new JsonObject();
        root.addProperty("version", definitions.version());
        root.addProperty("signed_in", session != null);
        root.addProperty("account", accountId != null);
        if (!definitions.products().isEmpty()) {
            Predicate<AntazonData.Product> unlocked = product -> AntazonUnlocks.test(product.unlockTasks(), product.unlockDisks(),
                    product.unlockCommands(), product.unlockMode(), taskDone, diskDone, commandDone);
            UUID profile = accountId == null ? player.getUUID() : accountId;
            AntazonServerData antazon = AntazonServerData.access(player.level().getServer());
            Map<Identifier, AntazonDeals.Active> deals = AntazonDeals.deals(profile, unlocked, day);
            JsonArray locked = new JsonArray();
            JsonArray unavailable = new JsonArray();
            JsonArray dealRows = new JsonArray();
            for (int index = 0; index < definitions.products().size(); index++) {
                AntazonData.Product product = definitions.products().get(index);
                if (!unlocked.test(product)) locked.add(index);
                if (!AntazonData.availableOn(product, day)) unavailable.add(index);
                AntazonDeals.Active deal = deals.get(AntazonData.limitKey(product));
                if (deal == null) continue;
                int remaining = AntazonService.dealRemaining(antazon, profile, product, deal);
                if (remaining == 0) continue;
                JsonObject row = new JsonObject();
                row.addProperty("i", index);
                row.addProperty("label", deal.campaign().label());
                row.addProperty("discount", deal.discountPercent());
                row.addProperty("remaining", remaining);
                row.addProperty("ends_in_ms", deal.campaign().endsInMillis(gameTime, System.currentTimeMillis()));
                JsonArray prices = new JsonArray();
                for (AntazonData.Payment payment : product.payments()) prices.add(AntazonService.unitPrice(payment, deal));
                row.add("prices", prices);
                dealRows.add(row);
            }
            root.add("locked", locked);
            root.add("unavailable", unavailable);
            root.add("deals", dealRows);
        }
        if (!definitions.rules().isEmpty()) {
            JsonArray locked = new JsonArray();
            for (int index = 0; index < definitions.rules().size(); index++) {
                AntazonSellData.Rule rule = definitions.rules().get(index);
                if (!AntazonUnlocks.test(rule.unlockTasks(), rule.unlockDisks(), rule.unlockCommands(), rule.unlockMode(), taskDone, diskDone, commandDone))
                    locked.add(index);
            }
            root.add("sell_locked", locked);
        }
        if (!definitions.tasks().isEmpty()) {
            JsonArray rows = new JsonArray();
            for (int index = 0; index < definitions.tasks().size(); index++) {
                ComputerGuideData.Task task = definitions.tasks().get(index);
                if (!ComputerTasks.visible(task, progress)) continue;
                boolean complete = progress.isComplete(task.id());
                boolean claimed = complete && accountId != null && ComputerTasks.hasClaimableRewards(task) && data.hasClaimedTaskReward(accountId, task.id());
                int status = claimed ? 3 : complete ? 2 : ComputerTasks.available(task, progress) ? 1 : 0;
                rows.add(index * 4 + status);
            }
            root.add("tasks", rows);
        }
        return root.toString();
    }

    private static JsonObject listing(AntazonData.Product product, Map<Identifier, String> titles, Map<Identifier, String> disks) {
        JsonObject row = new JsonObject();
        row.addProperty("id", product.id().toString());
        row.addProperty("name", product.name());
        if (product.poolEntry() != null) row.addProperty("pool_item", product.poolEntry().item().toString());
        JsonArray rewards = new JsonArray();
        for (AntazonData.Reward reward : product.rewards()) {
            JsonObject rewardRow = new JsonObject();
            rewardRow.addProperty("item", reward.item().toString());
            rewardRow.addProperty("count", reward.count());
            rewards.add(rewardRow);
        }
        row.add("rewards", rewards);
        if (product.variants() != null) {
            row.addProperty("variant_mode", product.variants().mode());
            JsonArray variants = new JsonArray();
            for (Identifier variant : product.variantItems()) variants.add(variant.toString());
            row.add("variants", variants);
        }
        JsonArray payments = new JsonArray();
        for (AntazonData.Payment payment : product.payments()) {
            JsonObject paymentRow = new JsonObject();
            paymentRow.addProperty("type", payment.type());
            paymentRow.addProperty("resource", payment.resource());
            paymentRow.addProperty("amount", payment.amount());
            payments.add(paymentRow);
        }
        row.add("payments", payments);
        row.addProperty("hidden", product.hiddenUntilUnlocked());
        row.addProperty("rotating", product.poolEntry() != null && product.itemPool() != null && product.itemPool().rotation() != null);
        AntazonData.Availability availability = product.availability();
        row.addProperty("stock", availability.serverStock());
        row.addProperty("limit", availability.playerLimit());
        row.addProperty("limit_reset", availability.playerLimitReset());
        row.add("unlock", unlock(product.unlockMode(), product.unlockTasks(), product.unlockDisks(), product.unlockCommands(), titles, disks));
        return row;
    }

    private static JsonObject sell(AntazonSellData.Rule rule, Map<Identifier, String> titles, Map<Identifier, String> disks) {
        JsonObject row = new JsonObject();
        row.addProperty("item", rule.item().toString());
        row.addProperty("value", rule.value());
        row.addProperty("limit", rule.limit());
        row.addProperty("limit_reset", rule.limitReset());
        row.addProperty("hidden", rule.hiddenUntilUnlocked());
        row.add("unlock", unlock(rule.unlockMode(), rule.unlockTasks(), rule.unlockDisks(), rule.unlockCommands(), titles, disks));
        return row;
    }

    private static JsonObject unlock(String mode, List<Identifier> tasks, List<Identifier> diskIds, List<Identifier> commands,
                                     Map<Identifier, String> titles, Map<Identifier, String> disks) {
        JsonObject row = new JsonObject();
        row.addProperty("mode", mode);
        JsonArray taskRows = new JsonArray();
        for (Identifier id : tasks) {
            taskRows.add(id.toString());
            ComputerGuideData.Task task = ComputerGuideData.task(id);
            if (task != null) titles.putIfAbsent(id, task.titleKey());
        }
        row.add("tasks", taskRows);
        JsonArray diskRows = new JsonArray();
        for (Identifier id : diskIds) {
            diskRows.add(id.toString());
            ComputerGuideData.Disk disk = ComputerGuideData.disk(id);
            if (disk != null) disks.putIfAbsent(id, disk.titleKey());
        }
        row.add("disks", diskRows);
        JsonArray commandRows = new JsonArray();
        for (Identifier id : commands) commandRows.add(TerminalCommands.hint(id));
        row.add("commands", commandRows);
        return row;
    }

    private static JsonObject task(ComputerGuideData.Task task, Map<Identifier, String> titles) {
        JsonArray items = new JsonArray();
        long antcoins = 0L;
        int experience = 0;
        int levels = 0;
        for (ComputerGuideData.TaskReward reward : task.rewards()) {
            switch (reward.type()) {
                case "item" -> addItem(items, reward.itemId(), reward.count(), 1.0, "");
                case "item_pool" -> addPool(items, reward.poolId(), 1.0, reward.mode().equals("choice") ? "choice" : "random", 0);
                case "choice" -> {
                    for (ComputerGuideData.RewardOption option : reward.options()) {
                        for (ComputerGuideData.TaskReward optionReward : option.rewards()) {
                            if (optionReward.type().equals("item")) addItem(items, optionReward.itemId(), optionReward.count(), 1.0, "option:" + option.title());
                            else if (optionReward.type().equals("item_pool")) addPool(items, optionReward.poolId(), 1.0, "option:" + option.title(), 0);
                        }
                    }
                }
                case "antcoins" -> antcoins += reward.antcoins();
                case "experience" -> experience += reward.experiencePoints();
                case "experience_levels" -> levels += reward.count();
                default -> { }
            }
        }
        if (items.isEmpty()) return null;
        JsonObject row = new JsonObject();
        row.addProperty("id", task.id().toString());
        row.addProperty("title", task.titleKey());
        ComputerGuideData.TaskCategory category = category(task.category());
        row.addProperty("category", category == null ? "" : category.titleKey());
        row.add("items", items);
        if (antcoins > 0L) row.addProperty("antcoins", antcoins);
        if (experience > 0) row.addProperty("experience", experience);
        if (levels > 0) row.addProperty("levels", levels);
        JsonArray requires = new JsonArray();
        for (Identifier id : task.requires()) {
            requires.add(id.toString());
            ComputerGuideData.Task required = ComputerGuideData.task(id);
            if (required != null) titles.putIfAbsent(id, required.titleKey());
        }
        row.add("requires", requires);
        JsonArray requiresAny = new JsonArray();
        for (Identifier id : task.requiresAny()) {
            requiresAny.add(id.toString());
            ComputerGuideData.Task required = ComputerGuideData.task(id);
            if (required != null) titles.putIfAbsent(id, required.titleKey());
        }
        row.add("requires_any", requiresAny);
        return row;
    }

    private static ComputerGuideData.TaskCategory category(String id) {
        if (id == null || id.isBlank()) return null;
        for (ComputerGuideData.TaskCategory category : ComputerGuideData.taskCategories())
            if (category.id().toString().equals(id)) return category;
        return null;
    }

    private static void addItem(JsonArray items, Identifier item, int count, double chance, String group) {
        if (item == null) return;
        JsonObject row = new JsonObject();
        row.addProperty("item", item.toString());
        row.addProperty("count", Math.max(1, count));
        if (chance < 1.0) row.addProperty("chance", chance);
        if (!group.isEmpty()) row.addProperty("group", group);
        items.add(row);
    }

    private static void addPool(JsonArray items, Identifier poolId, double chance, String group, int depth) {
        ComputerGuideData.RewardPool pool = ComputerGuideData.taskRewardPool(poolId);
        if (pool == null || depth > MAX_POOL_DEPTH) return;
        long total = 0L;
        for (ComputerGuideData.RewardPoolEntry entry : pool.entries()) total += Math.max(0, entry.weight());
        if (total <= 0L) return;
        for (ComputerGuideData.RewardPoolEntry entry : pool.entries()) {
            if (entry.weight() <= 0) continue;
            double share = group.equals("random") ? chance * entry.weight() / total : chance;
            if (entry.itemId() != null) {
                addItem(items, entry.itemId(), entry.count(), share, group);
            } else if (entry.poolId() != null && !Objects.equals(entry.poolId(), poolId)) {
                addPool(items, entry.poolId(), share, group, depth + 1);
            }
        }
    }

    public record Key(long catalog, int sellRules, int taskList, int pools, boolean prices, boolean sell, boolean tasks) { }

    public record Definitions(long version, List<AntazonData.Product> products, List<AntazonSellData.Rule> rules,
                              List<ComputerGuideData.Task> tasks, String json) { }
}
