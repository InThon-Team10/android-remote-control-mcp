#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Exercises the compiled isolated tutorial state, without the optional ngrok native test jar.
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
        var expected = "화면 설정은 어디에 있어요?";
        require(!HelperEntryModelKt.practiceQuestionMatches("", expected), "Empty draft blocked");
        require(!HelperEntryModelKt.practiceQuestionMatches("화면 설정", expected), "Incomplete draft blocked");
        require(HelperEntryModelKt.practiceQuestionMatches("화면설정은 어디에 있어요", expected), "Dictation spacing accepted");
        require(HelperEntryModelKt.practiceQuestionMatches(expected, expected), "Typed sentence accepted");
        require(HelperEntryModelKt.helperSetupPage(false, true, true) == HelperEntryPage.CONSENT, "Existing Android permissions cannot bypass consent");
        require(HelperEntryModelKt.helperSetupPage(true, false, false) == HelperEntryPage.PERMISSION, "Accessibility follows consent");
        require(HelperEntryModelKt.helperSetupPage(true, true, false) == HelperEntryPage.MICROPHONE, "Optional microphone follows accessibility");
        require(HelperEntryModelKt.helperSetupPage(true, true, true) == HelperEntryPage.HOME, "Completed setup is not repeated");
        var intro = new HelperEntryModel();
        require(intro.getPage() == HelperEntryPage.WELCOME, "First launch welcomes");
        require(intro.advance().equals(intro), "Non-practice cannot advance");
        var open = intro.practice();
        require(open.getStep() == HelperPracticeStep.OPEN, "Practice starts at help button");
        var ask = open.advance();
        require(ask.getStep() == HelperPracticeStep.ASK, "Help opens local question");
        var follow = ask.advance();
        require(follow.getStep() == HelperPracticeStep.FOLLOW, "Question leads to local target");
        var done = follow.advance();
        require(done.getStep() == HelperPracticeStep.DONE, "Target completes practice");
        require(done.advance().getPage() == HelperEntryPage.INVITE, "Invite only after completing steps");
        require(done.practice().equals(open), "Replay starts fresh");
        require(done.advance().advance().equals(done.advance()), "Invite does not grant permission");
        System.out.println("PASS: isolated practice sequence, replay, no simulated permissions");
    }
}
JAVA
classes=app/build/intermediates/built_in_kotlinc/gmsDebug/compileGmsDebugKotlin/classes
"$JAVA_HOME/bin/javac" -cp "$classes:$stdlib" "$scratch/EntryCheck.java"
"$JAVA_HOME/bin/java" -cp "$scratch:$classes:$stdlib" EntryCheck
