package com.holyworld.autoreply.config;

public class ModConfig {
    public enum CheckState { IDLE, WAITING_FOR_CHECK, CHECK_ACTIVE }

    private boolean autoReply = true;
    private boolean autoBan = true;
    private boolean autoReports = false;
    private boolean autoOut = false;

    private CheckState checkState = CheckState.IDLE;
    private String checkedPlayerName = "";

    public boolean isAutoReply() { return autoReply; }
    public void toggleAutoReply() { autoReply = !autoReply; }

    public boolean isAutoBan() { return autoBan; }
    public void toggleAutoBan() { autoBan = !autoBan; }

    public boolean isAutoReports() { return autoReports; }
    public void toggleAutoReports() { autoReports = !autoReports; }

    public boolean isAutoOut() { return autoOut; }
    public void toggleAutoOut() { autoOut = !autoOut; }

    public CheckState getCheckState() { return checkState; }
    public String getCheckedPlayerName() { return checkedPlayerName; }

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

    public boolean isCheckActive() { return checkState == CheckState.CHECK_ACTIVE; }
    public boolean isWaiting() { return checkState == CheckState.WAITING_FOR_CHECK; }
    public boolean isIdle() { return checkState == CheckState.IDLE; }
}
