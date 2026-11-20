package com.craisinlord.antos.content.antmail;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.computer.ComputerWorkspaceData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Development smoke test for Antmail teams, groups and lists. It seeds throwaway AntOS accounts and mail into the
 * current world and checks the result against the real saved data.
 *
 * Disabled unless the {@code ANTOS_SMOKE} environment variable is set, so it never exists in normal play.
 * Use a copy of a world: seeding creates accounts, a team and mail that persist in that save.
 */
public final class AntmailSmoke {
    public static final String PASSWORD = "SmokeTest2026!";
    public static final String LEAD = "smoke_lead", SCOUT = "smoke_scout", MEDIC = "smoke_medic", OUTSIDER = "smoke_out";
    public static final String CREW = "Smoke Crew", OLD_RAID = "Old Raid", NEWS = "Smoke News";
    public static final String MAIL_TEAM_SUBJECT = "Smoke: mail team";
    private static final String PREFIX = "[AntOS smoke] ";

    private AntmailSmoke() { }

    public static boolean enabled() { return System.getenv("ANTOS_SMOKE") != null; }

    /** {@code /antos smoke seed} and {@code /antos smoke check <stage>}; registered only when enabled. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!enabled()) return;
        dispatcher.register(Commands.literal("antos").requires(source -> source.hasPermission(2))
                .then(Commands.literal("smoke")
                        .then(Commands.literal("seed").executes(context -> {
                            report(context.getSource(), seed(context.getSource().getServer(), context.getSource().getPlayerOrException().getUUID()).lines());
                            return 1;
                        }))
                        .then(Commands.literal("check").then(Commands.argument("stage", StringArgumentType.word()).executes(context -> {
                            report(context.getSource(), check(context.getSource().getServer(), StringArgumentType.getString(context, "stage")));
                            return 1;
                        })))));
    }

    private static void report(CommandSourceStack source, List<String> lines) {
        for (String line : lines) source.sendSuccess(() -> Component.literal(PREFIX + line), false);
    }

    /** What the seed produced, for the client driver. */
    public record Seed(List<String> lines, BlockPos computer, UUID team) { }

    private static UUID stableId(String key) { return UUID.nameUUIDFromBytes(("antos-smoke/" + key).getBytes(StandardCharsets.UTF_8)); }
    public static UUID crewId() { return stableId("group/crew"); }
    public static UUID oldRaidId() { return stableId("group/old-raid"); }
    public static UUID newsId() { return stableId("list/news"); }

    private static UUID account(ComputerWorkspaceData data, String username, List<String> lines) {
        var existing = data.accountByUsername(username);
        if (existing != null) return existing.accountId();
        var created = data.createAccount(username, PASSWORD);
        if (!created.successful()) throw new IllegalStateException("could not create " + username + ": " + created.failure());
        lines.add("created account " + username + "@antmail.com");
        return created.account().accountId();
    }

    private static AntmailMessage mail(MinecraftServer server, AntmailServerData mail, UUID actor, String username, String key,
                                       String recipients, String subject, String body) {
        return AntmailDispatch.send(server, mail, actor, AntmailAddress.ofUsername(username), stableId("mail/" + key),
                recipients, subject, body, List.of(), count -> true);
    }

    /**
     * Seeds accounts, a team (lead, scout, medic), groups, an announcement list and mail, then places a computer two
     * blocks east of {@code playerId} and turns the player to face it. Safe to run more than once.
     */
    public static Seed seed(MinecraftServer server, UUID playerId) {
        List<String> lines = new ArrayList<>();
        var data = ComputerWorkspaceData.access(server);
        var mail = AntmailServerData.access(server);
        var directory = mail.directory();
        var accounts = AntmailDispatch.accounts(server);
        long now = server.overworld().getGameTime();
        UUID lead = account(data, LEAD, lines), scout = account(data, SCOUT, lines), medic = account(data, MEDIC, lines), outsider = account(data, OUTSIDER, lines);

        if (data.teamIdOf(lead) == null) data.createTeam(lead);
        UUID team = data.teamIdOf(lead);
        for (var member : List.of(new Object[] {scout, SCOUT}, new Object[] {medic, MEDIC})) {
            UUID id = (UUID) member[0];
            if (team.equals(data.teamIdOf(id))) continue;
            var invite = data.inviteToTeam(lead, (String) member[1], now);
            if (invite == null || !data.acceptTeamInvite(id, invite.id())) throw new IllegalStateException(member[1] + " could not join the team");
        }
        lines.add("team " + team + " has " + data.teamMailAudience(team).epochs().size() + " members");

        var crew = directory.create(scout, crewId(), "conversation", CREW);
        for (String member : List.of(LEAD, MEDIC)) crew = directory.change(scout, crew.id(), crew.revision(), "add", member, accounts);
        var news = directory.create(lead, newsId(), "mailing", NEWS);
        news = directory.change(lead, news.id(), news.revision(), "add", SCOUT, accounts);
        var oldRaid = directory.create(scout, oldRaidId(), "conversation", OLD_RAID);
        if (!directory.currentConversation(lead, oldRaid.id(), AntmailDirectory.Teams.NONE) && mail.mailboxOrCreate(AntmailAddress.ofUsername(LEAD)).conversation(oldRaid.id()).isEmpty()) {
            oldRaid = directory.change(scout, oldRaid.id(), oldRaid.revision(), "add", LEAD, accounts);
        }
        mail.setDirty();

        mail(server, mail, scout, SCOUT, "team-supply", "team:" + team, "Supply run tonight", "Meet at the ant farm at dusk. Bring torches.");
        mail(server, mail, medic, MEDIC, "team-potions", "team:" + team, "Bring potions", "I have healing potions for everyone.");
        mail(server, mail, scout, SCOUT, "crew-checkin", "conversation:" + crew.id(), "Crew check-in", "Everyone ready for the build?");
        mail(server, mail, outsider, OUTSIDER, "outside-hello", LEAD + "@antmail.com", "Hello from outside", "Just saying hi.");
        if (directory.currentConversation(lead, oldRaid.id(), AntmailDirectory.Teams.NONE)) {
            mail(server, mail, scout, SCOUT, "old-raid-recap", "conversation:" + oldRaid.id(), "Old raid recap", "Thanks for coming on the raid.");
            directory.leave(lead, oldRaid.id());
            mail.setDirty();
        }
        lines.add("seeded groups '" + CREW + "', '" + OLD_RAID + "' (left), list '" + NEWS + "' and 5 messages");

        BlockPos computer = null;
        ServerPlayer player = playerId == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            var level = player.serverLevel();
            BlockPos feet = player.blockPosition();
            computer = feet.east(2);
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "computer"));
            level.setBlockAndUpdate(computer.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(computer, block.defaultBlockState());
            level.setBlockAndUpdate(computer.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(feet.east(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(feet.east().above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, -90.0F, 35.0F);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            lines.add("placed a computer at " + computer.toShortString());
        }
        lines.forEach(line -> AntOS.LOGGER.info(PREFIX + line));
        return new Seed(lines, computer, team);
    }

    /**
     * Checks the saved data. Stages: {@code seed} after seeding, {@code reply} after the lead replies to the team
     * thread from the UI, {@code mailteam} after the lead mails the team from Tasks.
     */
    public static List<String> check(MinecraftServer server, String stage) {
        List<String> results = new ArrayList<>();
        var data = ComputerWorkspaceData.access(server);
        var mail = AntmailServerData.access(server);
        var directory = mail.directory();
        var teams = AntmailDispatch.teams(server);
        var lead = data.accountByUsername(LEAD);
        if (lead == null) { results.add("FAIL accounts: run seed first"); return log(results); }
        UUID team = data.teamIdOf(lead.accountId());
        var leadBox = mail.mailboxOrCreate(AntmailAddress.ofUsername(LEAD));
        switch (stage) {
            case "seed" -> {
                expect(results, "team has lead, scout and medic", team != null && data.teamMailAudience(team).epochs().size() == 3);
                long teamMail = team == null ? 0 : leadBox.inbox().stream().filter(m -> team.equals(m.conversationId())).count();
                expect(results, "lead received both team messages", teamMail >= 2);
                expect(results, "team copies are team-kind and named after the owner", team != null && leadBox.inbox().stream().filter(m -> team.equals(m.conversationId()))
                        .allMatch(m -> AntmailDirectory.CONVERSATION_TEAM.equals(m.conversationKind()) && LEAD.equals(m.conversationName())));
                var raid = leadBox.conversationHeads().get(oldRaidId());
                expect(results, "left group stays in history with its name", raid != null && OLD_RAID.equals(raid.conversationName())
                        && !directory.currentConversation(lead.accountId(), oldRaidId(), teams));
                expect(results, "lead's Groups page lists " + CREW, directory.page(lead.accountId(), "conversation", "", 0, AntmailDispatch.accounts(server)).lists().stream().anyMatch(v -> CREW.equals(v.name())));
                var scout = data.accountByUsername(SCOUT);
                expect(results, "announcement roster is hidden from plain members", scout != null && directory.view(scout.accountId(), newsId()).members().isEmpty());
                var outsider = data.accountByUsername(OUTSIDER);
                boolean rejected = false;
                try { AntmailDispatch.send(server, mail, outsider.accountId(), AntmailAddress.ofUsername(OUTSIDER), UUID.randomUUID(), "team:" + team, "Let me in", "Body", List.of(), count -> true); }
                catch (IllegalArgumentException exception) { rejected = "forbidden".equals(exception.getMessage()); }
                expect(results, "outsiders cannot mail the team", rejected);
            }
            case "reply" -> {
                for (String member : List.of(SCOUT, MEDIC)) {
                    var box = mail.mailboxOrCreate(AntmailAddress.ofUsername(member));
                    expect(results, member + " got the lead's Reply All in the team thread", box.inbox().stream().anyMatch(m -> team != null && team.equals(m.conversationId())
                            && m.sender().username().equals(LEAD) && m.subject().startsWith("Re: ")));
                }
            }
            case "mailteam" -> {
                for (String member : List.of(SCOUT, MEDIC)) {
                    var box = mail.mailboxOrCreate(AntmailAddress.ofUsername(member));
                    expect(results, member + " got the lead's MAIL TEAM message", box.inbox().stream().anyMatch(m -> team != null && team.equals(m.conversationId())
                            && m.sender().username().equals(LEAD) && MAIL_TEAM_SUBJECT.equals(m.subject())));
                }
            }
            default -> results.add("FAIL unknown stage " + stage);
        }
        return log(results);
    }

    private static void expect(List<String> results, String name, boolean condition) { results.add((condition ? "PASS " : "FAIL ") + name); }
    private static List<String> log(List<String> results) { results.forEach(line -> AntOS.LOGGER.info(PREFIX + line)); return results; }
}
