#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Exercises the compiled production mock state, without the optional ngrok native test jar.
stdlib=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib" -name '*.jar' | head -1)
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
cat > "$scratch/EntryCheck.java" <<'JAVA'
import com.danielealbano.androidremotecontrolmcp.ui.helper.*;
class EntryCheck {
    static void require(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        var intro = new HelperEntryModel();
        require(intro.getPage() == HelperEntryPage.WELCOME && !intro.getPermission(), "First launch");
        require(intro.help().back().equals(intro), "Help returns to intro");
        var setup = intro.primary();
        require(setup.getIntroSeen() && setup.getPage() == HelperEntryPage.SETUP, "Intro saved separately");
        var prompt = setup.primary();
        require(prompt.getPage() == HelperEntryPage.PERMISSION && !prompt.getPermission(), "No grant on visit");
        require(!prompt.back().getPermission(), "Back does not grant");
        var denied = prompt.permissionResult(false);
        require(!denied.back().getPermission() && denied.back().getPage() == HelperEntryPage.HOME, "Skip setup");
        var ready = prompt.permissionResult(true);
        var pending = ready.primary();
        require(pending.getBubble() == HelperEntryBubble.STARTING, "Pending");
        require(pending.primary().equals(pending), "Duplicate start ignored");
        require(pending.stop().finishStart().getBubble() == HelperEntryBubble.OFF, "Late start after stop ignored");
        require(pending.revoke().finishStart().getBubble() == HelperEntryBubble.OFF, "Revocation cancels start");
        var running = pending.finishStart();
        require(running.getBubble() == HelperEntryBubble.ON && running.getPage() == HelperEntryPage.HOME, "Stay home on start");
        require(running.primary().getPage() == HelperEntryPage.REQUEST, "Explicit request opening");
        require(running.primary().back().getPage() == HelperEntryPage.HOME, "Return to bubble preview");
        require(running.stop().getPermission(), "Stop preserves mock setup");
        var fail = new HelperEntryModel(HelperEntryPage.HOME, HelperEntryPage.HOME, true, true, HelperEntryBubble.OFF, true);
        var failed = fail.primary().finishStart();
        require(failed.getBubble() == HelperEntryBubble.FAILED, "Failure");
        require(failed.primary().finishStart().getBubble() == HelperEntryBubble.ON, "Retry succeeds");
        require(running.revoke().primary().getPage() == HelperEntryPage.SETUP, "Revoked permission requires setup");
        System.out.println("PASS: first entry, help return, skip/deny, explicit grant, pending dedup, stop/revoke race, failure/retry, fresh request");
    }
}
JAVA
classes=app/build/intermediates/built_in_kotlinc/gmsDebug/compileGmsDebugKotlin/classes
"$JAVA_HOME/bin/javac" -cp "$classes:$stdlib" "$scratch/EntryCheck.java"
"$JAVA_HOME/bin/java" -cp "$scratch:$classes:$stdlib" EntryCheck
