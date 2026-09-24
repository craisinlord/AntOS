package com.craisinlord.antos.content.client;


public final class AntOSClientHooks {
    private static Runnable computerOpener = () -> {};
    private static Runnable phoneOpener = () -> {};
    private AntOSClientHooks() {}
    public static void setComputerOpener(Runnable opener) { computerOpener = opener == null ? () -> {} : opener; }
    public static void openComputer() { computerOpener.run(); }
    public static void setPhoneOpener(Runnable opener) { phoneOpener = opener == null ? () -> {} : opener; }
    public static void openPhone() { phoneOpener.run(); }
    public static void clearConnectionState() {
        AnternetAccountClientState.clear();
        AntmailClientState.clear();
    }
}

