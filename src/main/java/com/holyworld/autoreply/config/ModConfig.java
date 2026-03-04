package com.holyworld.autoreply.config;

public class ModConfig {
    public enum CheckState { IDLE, WAITING_FOR_CHECK, CHECK_ACTIVE }

    private boolean enabled = true;
    private boolean autoReply = true;
    private boolean autoBanInsult = true;
    private boolean autoBanRefusal = true;
    private boolean autoBanConfession = true;
    private boolean autoReports = false;
    private boolean autoCheckout = false;
    private boolean autoOut = false;
    private CheckState checkState = CheckState.IDLE;
    private String checkedPlayerName = "";
    private String lastSpyNick = null;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public void toggleEnabled() { enabled = !enabled; }
    public boolean isAutoReply() { return autoReply; }
    public void toggleAutoReply() { autoReply = !autoReply; }
    public boolean isAutoBanInsult() { return autoBanInsult; }
    public void toggleAutoBanInsult() { autoBanInsult = !autoBanInsult; }
    public boolean isAutoBanRefusal() { return autoBanRefusal; }
    public void toggleAutoBanRefusal() { autoBanRefusal = !autoBanRefusal; }
    public boolean isAutoBanConfession() { return autoBanConfession; }
    public void toggleAutoBanConfession() { autoBanConfession = !autoBanConfession; }
    public boolean isAutoReports() { return autoReports; }
    public void toggleAutoReports() { autoReports = !autoReports; if (autoReports) autoCheckout = false; }
    public boolean isAutoCheckout() { return autoCheckout; }
    public void toggleAutoCheckout() { autoCheckout = !autoCheckout; if (autoCheckout) autoReports = false; }
    public boolean isAutoOut() { return autoOut; }
    public void toggleAutoOut() { autoOut = !autoOut; }
    public CheckState getCheckState() { return checkState; }
    public String getCheckedPlayerName() { return checkedPlayerName; }
    public boolean isIdle() { return checkState == CheckState.IDLE; }
    public boolean isWaiting() { return checkState == CheckState.WAITING_FOR_CHECK; }
    public boolean isCheckActive() { return checkState == CheckState.CHECK_ACTIVE; }
    public void startWaiting() { checkState = CheckState.WAITING_FOR_CHECK; checkedPlayerName = ""; }
    public void activateCheck(String name) { checkState = CheckState.CHECK_ACTIVE; checkedPlayerName = name; }
    public void endCheck() { checkState = CheckState.IDLE; checkedPlayerName = ""; }
    public String getLastSpyNick() { return lastSpyNick; }
    public void setLastSpyNick(String nick) { lastSpyNick = nick; }
}
