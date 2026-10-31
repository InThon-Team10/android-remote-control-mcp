#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Run after :app:compileGmsDebugKotlin, without the optional native ngrok test dependency.
stdlib=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib" -name '*.jar' | head -1)
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
cat > "$scratch/CaptionCheck.java" <<'JAVA'
import static com.danielealbano.androidremotecontrolmcp.ui.helper.HelperCaptionPlacementKt.placeHelperCaption;
class CaptionCheck {
    public static void main(String[] args) {
        var below = placeHelperCaption(400,800,160,80,220,140,280,100,16,16);
        if (!below.getBelow() || below.getTop()!=156) throw new AssertionError("Below target");
        var above = placeHelperCaption(400,800,160,660,220,720,280,100,16,16);
        if (above.getBelow() || above.getTop()!=544) throw new AssertionError("Above target");
        var large = placeHelperCaption(400,800,160,400,220,460,280,350,16,16);
        if (large.getBelow() || large.getTop()!=34) throw new AssertionError("Font-scaled height");
        if (placeHelperCaption(400,800,0,80,40,140,280,100,16,16).getLeft()!=16)
            throw new AssertionError("Left edge");
        if (placeHelperCaption(400,800,360,80,400,140,280,100,16,16).getLeft()!=104)
            throw new AssertionError("Right edge");
        System.out.println("PASS: below, above, large text, left edge, right edge");
    }
}
JAVA
"$JAVA_HOME/bin/javac" -cp "app/build/intermediates/built_in_kotlinc/gmsDebug/compileGmsDebugKotlin/classes:$stdlib" "$scratch/CaptionCheck.java"
"$JAVA_HOME/bin/java" -cp "$scratch:app/build/intermediates/built_in_kotlinc/gmsDebug/compileGmsDebugKotlin/classes:$stdlib" CaptionCheck
