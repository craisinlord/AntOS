package com.craisinlord.antos.content.antazon;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.computer.ComputerWorkspace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class AntazonDeals {
    private static final Map<UUID, ProfileDeals> PROFILES = new ConcurrentHashMap<>();
    private static volatile CandidateIndex index;

    private AntazonDeals() { }

    public static void clear() {
        PROFILES.clear();
        index = null;
    }

    public static Active active(ServerPlayer player, ComputerWorkspace computer, AntazonData.Product product, long day) {
        if (player == null || computer == null || product == null) return null;
        UUID profile = AntazonService.accountOwner(computer, player);
        return deals(profile, listing -> AntazonService.unlocked(player, computer, listing), day).get(AntazonData.limitKey(product));
    }

    public static Map<ResourceLocation, Active> deals(UUID profile, Predicate<AntazonData.Product> unlocked, long day) {
        if (AntazonDealData.campaigns().isEmpty()) return Map.of();
        long now = System.currentTimeMillis();
        long stamp = stamp(day, now);
        ProfileDeals cached = PROFILES.get(profile);
        if (cached != null && cached.stamp() == stamp) return cached.deals();
        Map<ResourceLocation, Active> deals = pick(unlocked, day, now);
        PROFILES.put(profile, new ProfileDeals(stamp, deals));
        return deals;
    }

    public static Map<ResourceLocation, Active> pick(Predicate<AntazonData.Product> unlocked, long day, long now) {
        CandidateIndex candidates = index();
        Map<ResourceLocation, Active> chosen = new LinkedHashMap<>();
        for (AntazonDealData.Campaign campaign : AntazonDealData.campaigns()) {
            if (!campaign.enabled()) continue;
            List<Candidate> pool = candidates.byCampaign().getOrDefault(campaign.id(), List.of());
            if (pool.isEmpty()) continue;
            long period = campaign.period(day, now);
            Map<String, Integer> perCategory = new HashMap<>();
            AntazonRotation.OrderCache cache = new AntazonRotation.OrderCache();
            long position = period * campaign.picks();
            int found = 0;
            for (int step = 0; step < pool.size() && found < campaign.picks(); step++) {
                Candidate candidate = pool.get(AntazonRotation.slot(campaign.id(), campaign.shuffled(), position + step, pool.size(), cache));
                if (chosen.containsKey(candidate.key())) continue;
                if (campaign.maxPerCategory() > 0 && perCategory.getOrDefault(candidate.category(), 0) >= campaign.maxPerCategory()) continue;
                if (!eligible(candidate, day, unlocked)) continue;
                chosen.put(candidate.key(), new Active(campaign, discount(campaign, period, candidate.key()), period));
                perCategory.merge(candidate.category(), 1, Integer::sum);
                found++;
            }
        }
        return Map.copyOf(chosen);
    }

    public static List<ResourceLocation> preview(AntazonDealData.Campaign campaign, long day, long gameTime, long now, int periodsAhead) {
        long targetDay = day;
        long targetNow = now;
        if (periodsAhead > 0) {
            if (campaign.realTime()) targetNow = now + campaign.endsInMillis(gameTime, now) + (periodsAhead - 1L) * campaign.everyRealHours() * 3_600_000L;
            else targetDay = (campaign.period(day, now) + periodsAhead) * campaign.everyMinecraftDays();
        }
        List<ResourceLocation> keys = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Active> entry : pick(null, targetDay, targetNow).entrySet()) {
            if (entry.getValue().campaign().id().equals(campaign.id())) keys.add(entry.getKey());
        }
        return keys;
    }

    public static Map<ResourceLocation, Integer> discountsFor(AntazonDealData.Campaign campaign, long day, long gameTime, long now, int periodsAhead) {
        long period = campaign.period(day, now) + periodsAhead;
        Map<ResourceLocation, Integer> discounts = new LinkedHashMap<>();
        for (ResourceLocation key : preview(campaign, day, gameTime, now, periodsAhead)) discounts.put(key, discount(campaign, period, key));
        return discounts;
    }

    public static int candidateCount(AntazonDealData.Campaign campaign) {
        return index().byCampaign().getOrDefault(campaign.id(), List.of()).size();
    }

    private static boolean eligible(Candidate candidate, long day, Predicate<AntazonData.Product> unlocked) {
        for (AntazonData.Product listing : candidate.listings()) {
            if (!listing.enabled() || !AntazonData.availableOn(listing, day)) continue;
            return unlocked == null || unlocked.test(listing);
        }
        return false;
    }

    private static int discount(AntazonDealData.Campaign campaign, long period, ResourceLocation key) {
        if (campaign.discountMin() == campaign.discountMax()) return campaign.discountMin();
        long seed = campaign.id().toString().hashCode() * 0x9E3779B97F4A7C15L + period * 31L + key.toString().hashCode();
        return campaign.discountMin() + new java.util.Random(seed).nextInt(campaign.discountMax() - campaign.discountMin() + 1);
    }

    private static long stamp(long day, long now) {
        long stamp = AntazonData.version() * 31L + AntazonDealData.version();
        for (AntazonDealData.Campaign campaign : AntazonDealData.campaigns()) stamp = stamp * 31L + campaign.period(day, now);
        return stamp;
    }

    private static CandidateIndex index() {
        long catalogVersion = AntazonData.version();
        long dealVersion = AntazonDealData.version();
        CandidateIndex current = index;
        if (current != null && current.catalogVersion() == catalogVersion && current.dealVersion() == dealVersion) return current;
        synchronized (AntazonDeals.class) {
            current = index;
            if (current != null && current.catalogVersion() == catalogVersion && current.dealVersion() == dealVersion) return current;
            current = buildIndex(catalogVersion, dealVersion);
            index = current;
            PROFILES.clear();
            return current;
        }
    }

    private static CandidateIndex buildIndex(long catalogVersion, long dealVersion) {
        Map<ResourceLocation, List<AntazonData.Product>> groups = new LinkedHashMap<>();
        for (AntazonData.Product product : AntazonData.products()) {
            if (!product.enabled() || product.payments().isEmpty()) continue;
            groups.computeIfAbsent(AntazonData.limitKey(product), ignored -> new ArrayList<>()).add(product);
        }
        List<Candidate> all = new ArrayList<>();
        for (Map.Entry<ResourceLocation, List<AntazonData.Product>> entry : groups.entrySet()) {
            AntazonData.Product first = entry.getValue().get(0);
            Set<String> tags = new HashSet<>();
            for (String tag : first.tags()) tags.add(tag.toLowerCase(Locale.ROOT));
            all.add(new Candidate(entry.getKey(), first.category().toLowerCase(Locale.ROOT), Set.copyOf(tags),
                    first.payments().get(0).amount(), List.copyOf(entry.getValue())));
        }
        all.sort(java.util.Comparator.comparing(candidate -> candidate.key().toString()));
        Map<ResourceLocation, List<Candidate>> byCampaign = new HashMap<>();
        for (AntazonDealData.Campaign campaign : AntazonDealData.campaigns()) {
            List<Candidate> matching = all.stream().filter(candidate -> matches(campaign.candidates(), candidate)).toList();
            if (campaign.enabled() && matching.isEmpty()) AntOS.LOGGER.warn("Antazon deal campaign {} has no candidate products", campaign.id());
            if (campaign.enabled()) for (Candidate candidate : matching) warnSellBack(campaign, candidate);
            byCampaign.put(campaign.id(), matching);
        }
        return new CandidateIndex(catalogVersion, dealVersion, Map.copyOf(byCampaign));
    }

    private static boolean matches(AntazonDealData.Candidates rules, Candidate candidate) {
        boolean included = rules.includesEverything()
                || rules.products().contains(candidate.key())
                || rules.categories().contains(candidate.category())
                || candidate.tags().stream().anyMatch(rules.tags()::contains);
        if (!included) return false;
        if (rules.excludeProducts().contains(candidate.key()) || rules.excludeCategories().contains(candidate.category())
                || candidate.tags().stream().anyMatch(rules.excludeTags()::contains)) return false;
        if (rules.minPrice() > 0 && candidate.price() < rules.minPrice()) return false;
        return rules.maxPrice() <= 0 || candidate.price() <= rules.maxPrice();
    }

    private static void warnSellBack(AntazonDealData.Campaign campaign, Candidate candidate) {
        for (AntazonData.Product listing : candidate.listings()) {
            long sellValue = 0L;
            for (AntazonData.Reward reward : listing.rewards()) {
                AntazonSellData.Rule rule = AntazonSellData.rule(reward.item());
                if (rule != null) sellValue += (long) rule.value() * reward.count();
            }
            sellValue *= listing.quantity();
            if (sellValue <= 0L) continue;
            for (AntazonData.Payment payment : listing.payments()) {
                if (!payment.type().equals("antcoins")) continue;
                int lowest = Math.max(1, payment.amount() * (100 - campaign.discountMax()) / 100);
                if (lowest <= sellValue) {
                    AntOS.LOGGER.warn("Antazon deal campaign {} can sell {} for {} AntCoins, but its rewards sell back for {}",
                            campaign.id(), listing.id(), lowest, sellValue);
                    return;
                }
            }
        }
    }

    public record Active(AntazonDealData.Campaign campaign, int discountPercent, long period) {
        public int price(int amount) {
            return Math.max(1, amount * (100 - discountPercent) / 100);
        }

        public String key() {
            return campaign.id() + "@" + period;
        }
    }

    private record Candidate(ResourceLocation key, String category, Set<String> tags, int price, List<AntazonData.Product> listings) { }

    private record CandidateIndex(long catalogVersion, long dealVersion, Map<ResourceLocation, List<Candidate>> byCampaign) { }

    private record ProfileDeals(long stamp, Map<ResourceLocation, Active> deals) { }
}
