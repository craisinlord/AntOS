package com.craisinlord.antos.content.computer;

import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Method;

public final class AccountRecoveryTest {
    public static void main(String[] args) throws Exception {
        ComputerWorkspaceData data = new ComputerWorkspaceData();
        ComputerWorkspaceData.AccountResult created = data.completeAccountCreation("Alice", ComputerWorkspaceData.hashPassword("correct horse"), "  favorite pet  ");
        check(created.successful(), "account is created with a hint");
        var account = created.account();
        check(data.hintForUsername("ALICE").equals("favorite pet"), "hint is trimmed and found by any username case");
        check(!data.mustChangePassword(account.accountId()), "new accounts don't need a password change");
        check(data.authenticate("alice", "correct horse").successful(), "original password signs in");

        String temporary = "Tmp4Kq9Zxw";
        check(data.resetPassword(account.accountId(), ComputerWorkspaceData.hashPassword(temporary), true).successful(), "admin reset succeeds");
        check(!data.authenticate("alice", "correct horse").successful(), "old password stops working after a reset");
        check(data.authenticate("alice", temporary).successful(), "temporary password signs in");
        check(data.mustChangePassword(account.accountId()), "temporary password must be changed");
        check(data.hint(account.accountId()).equals("favorite pet"), "reset keeps the hint");

        CompoundTag saved = data.save(new CompoundTag(), null);
        Method load = ComputerWorkspaceData.class.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        ComputerWorkspaceData reloaded = (ComputerWorkspaceData) load.invoke(null, saved, null);
        check(reloaded.mustChangePassword(account.accountId()), "must-change flag survives a save");
        check(reloaded.hint(account.accountId()).equals("favorite pet"), "hint survives a save");
        check(reloaded.authenticate("alice", temporary).successful(), "temporary password survives a save");

        ComputerWorkspaceData.CredentialCheck check = reloaded.prepareAuthentication("alice");
        ComputerWorkspaceData.PasswordChange change = ComputerWorkspaceData.derivePasswordChange(check, temporary, "brand new");
        check(change.verified() && change.validPassword(), "player can replace the temporary password");
        check(reloaded.completePasswordChange(account.accountId(), check.verifier(), change.hash()).successful(), "password change is stored");
        check(!reloaded.mustChangePassword(account.accountId()), "changing the password clears the must-change flag");
        check(reloaded.authenticate("alice", "brand new").successful(), "new password signs in");
        check(reloaded.hint(account.accountId()).equals("favorite pet"), "password change keeps the hint");

        check(!ComputerWorkspaceData.validHint("my CORRECT HORSE", "correct horse"), "hints can't contain the password");
        check(ComputerWorkspaceData.validHint("", "anything"), "an empty hint is allowed");
        check(!ComputerWorkspaceData.validHint("x".repeat(ComputerWorkspaceData.MAX_HINT_LENGTH + 1), "secret"), "long hints are rejected");
        check(reloaded.setHint(account.accountId(), ""), "hint can be removed");
        check(reloaded.hintForUsername("alice").isEmpty(), "removed hint isn't shown");
        check(reloaded.hintForUsername("nobody").isEmpty(), "unknown usernames have no hint");
        check(!ComputerWorkspaceData.validPassword("") && ComputerWorkspaceData.validPassword("a"), "command password validation matches sign-in");
        System.out.println("AccountRecoveryTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
