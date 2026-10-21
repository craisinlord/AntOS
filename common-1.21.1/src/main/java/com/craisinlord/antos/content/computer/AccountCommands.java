package com.craisinlord.antos.content.computer;

import com.craisinlord.antos.content.antmail.AntmailAddress;
import com.craisinlord.antos.content.antmail.AntmailMessage;
import com.craisinlord.antos.content.antmail.AntmailServerData;
import com.craisinlord.antos.content.network.AnternetAccountHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.security.SecureRandom;
import java.util.List;

public final class AccountCommands {
    private static final String TEMPORARY_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int TEMPORARY_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AntmailAddress SECURITY_SENDER = AntmailAddress.ofUsername("anternetsecurity");

    private AccountCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        com.craisinlord.antos.content.merchant.MerchantCommands.register(dispatcher);
        dispatcher.register(Commands.literal("antos").requires(source -> source.hasPermission(2))
                .then(Commands.literal("account")
                        .then(Commands.literal("resetpassword")
                                .then(Commands.argument("username", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(usernames(context.getSource().getServer()), builder))
                                        .executes(context -> reset(context, temporaryPassword(), true))
                                        .then(Commands.argument("password", StringArgumentType.greedyString())
                                                .executes(context -> reset(context, StringArgumentType.getString(context, "password"), false)))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("username", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(usernames(context.getSource().getServer()), builder))
                                        .executes(AccountCommands::info)))));
    }

    private static int reset(CommandContext<CommandSourceStack> context, String password, boolean temporary) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        String username = StringArgumentType.getString(context, "username");
        ComputerWorkspaceData data = ComputerWorkspaceData.access(server);
        ComputerWorkspaceData.AccountInfo account = data.accountByUsername(username);
        if (account == null) {
            source.sendFailure(Component.translatable("computer.antos.command.account.not_found", username));
            return 0;
        }
        if (!ComputerWorkspaceData.validPassword(password)) {
            source.sendFailure(Component.translatable("computer.antos.command.account.invalid_password"));
            return 0;
        }
        AnternetAccountHandler.adminResetPassword(server, account.accountId(), password, temporary, changed -> {
            if (!changed) {
                source.sendFailure(Component.translatable("computer.antos.command.account.reset_failed", account.displayUsername()));
                return;
            }
            notifyAccount(server, account);
            if (temporary) {
                Component shown = Component.literal(password).withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, password)));
                source.sendSuccess(() -> Component.translatable("computer.antos.command.account.reset_temporary", account.displayUsername(), shown), false);
            } else {
                source.sendSuccess(() -> Component.translatable("computer.antos.command.account.reset_set", account.displayUsername()), false);
            }
        });
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        String username = StringArgumentType.getString(context, "username");
        ComputerWorkspaceData data = ComputerWorkspaceData.access(server);
        ComputerWorkspaceData.AccountInfo account = data.accountByUsername(username);
        if (account == null) {
            source.sendFailure(Component.translatable("computer.antos.command.account.not_found", username));
            return 0;
        }
        Component yes = Component.translatable("computer.antos.command.account.yes");
        Component no = Component.translatable("computer.antos.command.account.no");
        boolean hint = !data.hint(account.accountId()).isEmpty();
        boolean faceId = data.faceIdPlayer(account.accountId()) != null;
        boolean mustChange = data.mustChangePassword(account.accountId());
        int sessions = AnternetAccountHandler.activeSessions(account.accountId());
        source.sendSuccess(() -> Component.translatable("computer.antos.command.account.info", account.displayUsername(),
                hint ? yes : no, faceId ? yes : no, mustChange ? yes : no, sessions), false);
        return 1;
    }

    private static void notifyAccount(MinecraftServer server, ComputerWorkspaceData.AccountInfo account) {
        AntmailMessage message = AntmailMessage.create(SECURITY_SENDER, AntmailAddress.ofUsername(account.username()),
                "Your password was reset",
                "Hi " + account.displayUsername() + ",\n\nA server administrator reset the password for your Anternet account. "
                        + "Every device signed in to this account was signed out.\n\n"
                        + "If you asked for this, sign in with the password the administrator gave you. You will be asked to choose a new one.\n\n"
                        + "If you did not ask for this, contact a server administrator.\n\nAnternet Security",
                server.overworld().getGameTime(), List.of());
        message.setPresentation("", "Anternet Security", false, "");
        AntmailServerData.access(server).deliver(server, message);
    }

    private static List<String> usernames(MinecraftServer server) {
        return ComputerWorkspaceData.access(server).accounts().stream().map(ComputerWorkspaceData.AccountInfo::username).sorted().toList();
    }

    private static String temporaryPassword() {
        StringBuilder password = new StringBuilder(TEMPORARY_LENGTH);
        for (int index = 0; index < TEMPORARY_LENGTH; index++) {
            password.append(TEMPORARY_ALPHABET.charAt(RANDOM.nextInt(TEMPORARY_ALPHABET.length())));
        }
        return password.toString();
    }
}
