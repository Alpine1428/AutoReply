package com.holyworld.autoreply.config;

public class ModConfig {

    // === Check State ===
    public enum CheckState {
        IDLE,               // Nothing happening
        WAITING_FOR_CHECK,  // /hm spyfrz sent, waiting for [CHECK] message
        CHECK_ACTIVE        // Got first [CHECK] message, actively responding
    }

    // === Core Toggle ===
    private boolean enabled = false;

    // === Feature Toggles ===
    private boolean autoReply = true;
    private boolean autoBanInsult = true;
    private boolean autoBanRefusal = true;
    private boolean autoBanConfession = true;
    private boolean autoReports = false;
    private boolean autoCheckout = false;
    private boolean autoOut = false;

    // === State ===
    private CheckState checkState = CheckState.IDLE;
    private String checkedPlayerName = "";
    private String lastSpyNick = null;

    // --- Core ---
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public void toggleEnabled() { enabled = !enabled; }

    // --- Auto Reply ---
    public boolean isAutoReply() { return autoReply; }
    public void toggleAutoReply() { autoReply = !autoReply; }

    // --- Auto Ban: Insult ---
    public boolean isAutoBanInsult() { return autoBanInsult; }
    public void toggleAutoBanInsult() { autoBanInsult = !autoBanInsult; }

    // --- Auto Ban: Refusal ---
    public boolean isAutoBanRefusal() { return autoBanRefusal; }
    public void toggleAutoBanRefusal() { autoBanRefusal = !autoBanRefusal; }

    // --- Auto Ban: Confession ---
    public boolean isAutoBanConfession() { return autoBanConfession; }
    public void toggleAutoBanConfession() { autoBanConfession = !autoBanConfession; }

    // --- Auto Reports ---
    public boolean isAutoReports() { return autoReports; }
    public void toggleAutoReports() {
        autoReports = !autoReports;
        if (autoReports) autoCheckout = false;
    }

    // --- Auto Checkout ---
    public boolean isAutoCheckout() { return autoCheckout; }
    public void toggleAutoCheckout() {
        autoCheckout = !autoCheckout;
        if (autoCheckout) autoReports = false;
    }

    // --- Auto Out ---
    public boolean isAutoOut() { return autoOut; }
    public void toggleAutoOut() { autoOut = !autoOut; }

    // --- Check State ---
    public CheckState getCheckState() { return checkState; }
    public String getCheckedPlayerName() { return checkedPlayerName; }

    public boolean isIdle() { return checkState == CheckState.IDLE; }
    public boolean isWaiting() { return checkState == CheckState.WAITING_FOR_CHECK; }
    public boolean isCheckActive() { return checkState == CheckState.CHECK_ACTIVE; }

    public void startWaiting() {
        checkState = CheckState.WAITING_FOR_CHECK;
        checkedPlayerName = "";
    }

    public void activateCheck(String name) {
        checkState = CheckState.CHECK_ACTIVE;
        checkedPlayerName = name;
    }

    public void endCheck() {
        checkState = CheckState.IDLE;
        checkedPlayerName = "";
    }

    // --- Spy Nick ---
    public String getLastSpyNick() { return lastSpyNick; }
    public void setLastSpyNick(String nick) { lastSpyNick = nick; }

    // --- Disable all auto ---
    public void disableAllAuto() {
        autoReports = false;
        autoCheckout = false;
    }
}
