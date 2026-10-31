#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
stdlib=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib" -name '*.jar' | head -1)
geometry=$(find "$HOME/.gradle/caches" -name ui-geometry-runtime.jar | head -1)
classes=app/build/intermediates/built_in_kotlinc/gmsDebug/compileGmsDebugKotlin/classes
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
cat > "$scratch/ConsequenceCheck.java" <<'JAVA'
import com.danielealbano.androidremotecontrolmcp.ui.helper.*;
import androidx.compose.ui.geometry.Rect;
import java.util.List;
class ConsequenceCheck {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static ConsequenceStep step(boolean important, boolean confirmed, int version) {
        return new ConsequenceStep("test", "action", "result", List.of("facts"),
            important, confirmed, new Rect(160f,80f,220f,140f), version, important ? GuideAction.PAYMENT : GuideAction.OTHER);
    }
    public static void main(String[] args) {
        check(ConsequenceGuideModelKt.isConsequenceTestRequest("테스트"),"test request accepted");
        check(ConsequenceGuideModelKt.isConsequenceTestRequest("  테스트  "),"trim test request");
        check(!ConsequenceGuideModelKt.isConsequenceTestRequest("테스트 결제"),"no partial match");
        check(!ConsequenceGuideModelKt.isConsequenceTestRequest(""),"empty request does not launch");
        var a = new ConsequenceState();
        a.begin(step(true,true,0));
        check(a.getPhase()==ConsequencePhase.EXPLANATION,"payment starts with explanation");
        a.locate();
        check(!a.getTargetOpen(),"no target before measurement");
        a.fit(true); a.locate();
        check(a.getTargetOpen(),"verified target available");
        a.replace(step(true,true,1));
        check(a.getPhase()==ConsequencePhase.RECONFIRM && !a.getTargetOpen(),"changed payment requires reconfirmation");
        var b = new ConsequenceState();
        b.begin(step(false,true,0));
        check(b.getPhase()==ConsequencePhase.TARGET && !b.getTargetOpen(),"ordinary begins directly but needs measurement");
        b.fit(false);
        check(!b.getTargetOpen(),"insufficient space cannot highlight");
        b.fit(true);
        check(b.getTargetOpen(),"ordinary guidance skips long panel");
        b.replace(step(false,true,1));
        check(b.getPhase()==ConsequencePhase.TARGET && !b.getTargetOpen(),"ordinary replacement invalidates measurement");
        for (int index=0; index<5; index++) {
            var example=ConsequenceGuideModelKt.consequenceExample(index,new Rect(160f,80f,220f,140f),0);
            b.begin(example);
            check(example.getNeedsExplanation()==(index==0 || index==1),"only payment and send fixtures explain");
            check((b.getPhase()==ConsequencePhase.EXPLANATION)==(index==0 || index==1),"fixture routing");
        }
        var overflow = HelperCaptionPlacementKt.placeHelperCaption(320,400,100,160,180,220,280,350,16,16);
        check(!overflow.getFits(),"overflow must not overlap target");
        b.finish();
        check(b.getStep()==null && b.getPhase()==ConsequencePhase.INPUT && !b.getTargetOpen(),"exit discards previous guidance");
        b.fit(true); b.locate();
        check(!b.getTargetOpen(),"late measurement cannot reopen finished target");
        b.begin(step(true,true,0));
        check(!b.getTargetOpen() && !b.getMeasured(),"new request must be measured again");
        check(HelperCaptionPlacementKt.helperControlsTop(600,40,100,120,16,false)==464,"bottom controls");
        check(HelperCaptionPlacementKt.helperControlsTop(600,400,460,180,16,false)==16,"move controls to opposite side");
        check(HelperCaptionPlacementKt.helperControlsTop(300,100,160,180,16,false)==null,"no room requires explanation panel");
        System.out.println("PASS: finished guide cannot reopen, new request resets, control collision/fallback");
        var voice = new ConsequenceNarration();
        var first = step(true,true,0);
        check(!voice.claim(first,true,false,false,false),"wait for TTS readiness");
        check(voice.claim(first,true,true,false,false),"first narration");
        check(!voice.claim(first,true,true,false,false),"no duplicate on redraw");
        check(!voice.claim(step(true,true,1),false,true,false,false),"respect mute");
        check(!voice.claim(step(true,true,1),true,true,true,false),"avoid screen reader overlap");
        check(!voice.claim(step(true,true,1),true,true,false,true),"no automatic read in menu");
        check(voice.claim(step(true,true,1),true,true,false,false),"new information is read");
        System.out.println("PASS: automatic narration readiness, deduplication, mute, screen reader, menu, changed step");
        System.out.println("PASS: payment/send-only explanation, ordinary/deletion/unknown compact guidance, invalidation");
    }
}
JAVA
"$JAVA_HOME/bin/javac" -cp "$classes:$stdlib:$geometry" "$scratch/ConsequenceCheck.java"
"$JAVA_HOME/bin/java" -cp "$scratch:$classes:$stdlib:$geometry" ConsequenceCheck
