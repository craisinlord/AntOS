package com.craisinlord.antos.content.computer;

import java.util.UUID;

public final class ComputerWorkspaceDataAccountTest {
    private ComputerWorkspaceDataAccountTest() { }

    public static void main(String[] args) {
        accountCreationAndLogin();
        shortPasswordsWorkAndEmptyPasswordsAreRejected();
        passwordRotationRevokesOldCredential();
        emptyPasswordRotationPreservesCurrentCredential();
        System.out.println("Account tests passed");
    }

    private static void accountCreationAndLogin() {
        ComputerWorkspaceData data = new ComputerWorkspaceData();
        var created = data.createAccount("Test_User", "correct horse battery");
        check(created.successful(), "valid account creation should succeed");
        check(data.authenticate("test_user", "correct horse battery").successful(), "username matching should ignore case");
        check(!data.authenticate("test_user", "incorrect password").successful(), "incorrect credentials should fail");
        check(data.createAccount("TEST_USER", "another valid password").failure()
                == ComputerWorkspaceData.AccountFailure.USERNAME_TAKEN, "normalized usernames should be unique");
    }

    private static void passwordRotationRevokesOldCredential() {
        ComputerWorkspaceData data = new ComputerWorkspaceData();
        var created = data.createAccount("rotate_me", "first secure password");
        UUID accountId = created.account().accountId();
        UUID workspaceId = created.account().workspaceId();
        var changed = data.changePassword(accountId, "second secure password");
        check(changed.successful(), "authenticated account password rotation should succeed");
        check(!data.authenticate("rotate_me", "first secure password").successful(), "old shared password must stop authenticating");
        var authenticated = data.authenticate("rotate_me", "second secure password");
        check(authenticated.successful(), "new password should authenticate");
        check(accountId.equals(authenticated.account().accountId()), "password rotation must preserve account identity");
        check(workspaceId.equals(authenticated.account().workspaceId()), "password rotation must preserve workspace data identity");
        check(!data.changePassword(UUID.randomUUID(), "third secure password").successful(), "unknown account must not rotate a password");
    }

    private static void shortPasswordsWorkAndEmptyPasswordsAreRejected() {
        ComputerWorkspaceData data = new ComputerWorkspaceData();
        var created = data.createAccount("short_pass", "x");
        check(created.successful(), "one-character passwords should be accepted");
        check(data.authenticate("short_pass", "x").successful(), "short passwords should authenticate");
        var changed = data.changePassword(created.account().accountId(), "y");
        check(changed.successful(), "short replacement passwords should be accepted");
        check(!data.authenticate("short_pass", "x").successful(), "rotated short passwords should invalidate the old credential");
        check(data.authenticate("short_pass", "y").successful(), "rotated short passwords should authenticate");
        check(data.createAccount("empty_pass", "").failure() == ComputerWorkspaceData.AccountFailure.INVALID_PASSWORD,
                "empty passwords should be rejected");
        check(data.createAccount("long_pass", "x".repeat(129)).failure() == ComputerWorkspaceData.AccountFailure.INVALID_PASSWORD,
                "passwords longer than 128 characters should be rejected");
        check(data.changePassword(created.account().accountId(), "x".repeat(129)).failure()
                        == ComputerWorkspaceData.AccountFailure.INVALID_PASSWORD,
                "password changes longer than 128 characters should be rejected");
    }

    private static void emptyPasswordRotationPreservesCurrentCredential() {
        ComputerWorkspaceData data = new ComputerWorkspaceData();
        var created = data.createAccount("keep_me", "existing secure password");
        var changed = data.changePassword(created.account().accountId(), "");
        check(changed.failure() == ComputerWorkspaceData.AccountFailure.INVALID_PASSWORD,
                "empty replacement passwords should be rejected");
        check(data.authenticate("keep_me", "existing secure password").successful(),
                "rejected password rotation must leave the old credential intact");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
