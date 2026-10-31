package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

internal enum class GuideAction { PAYMENT, TRANSMISSION, OTHER }

internal enum class ConsequencePhase { INPUT, EXPLANATION, TARGET, RECONFIRM }

internal data class ConsequenceStep(
    val id: String,
    val action: String,
    val result: String,
    val facts: List<String>,
    val important: Boolean,
    val confirmed: Boolean,
    val target: Rect,
    val version: Int,
    val kind: GuideAction = GuideAction.OTHER,
) {
    val needsExplanation: Boolean get() = kind == GuideAction.PAYMENT || kind == GuideAction.TRANSMISSION
    val fullText: String get() = (listOf(result) + facts + action).joinToString("\n")
    val coreText: String get() = "$result\n$action"
}

internal class ConsequenceState {
    var phase = ConsequencePhase.INPUT
    var step: ConsequenceStep? = null
    var canLocate = false
    var measured = false

    fun finish() {
        phase = ConsequencePhase.INPUT
        step = null
        canLocate = false
        measured = false
    }

    fun begin(value: ConsequenceStep) {
        step = value
        canLocate = false
        measured = false
        phase = if (value.needsExplanation) ConsequencePhase.EXPLANATION else ConsequencePhase.TARGET
    }

    fun replace(value: ConsequenceStep) {
        if (step == value) return
        step = value
        measured = false
        canLocate = false
        phase = if (value.needsExplanation) ConsequencePhase.RECONFIRM else ConsequencePhase.TARGET
    }

    fun fit(fits: Boolean) {
        measured = true
        canLocate = fits && step != null
    }

    fun locate() {
        if (canLocate && measured && step != null) phase = ConsequencePhase.TARGET
    }

    val targetOpen: Boolean get() = phase == ConsequencePhase.TARGET && measured && canLocate
}

/** In-process bridge only: no external intents can supply a real transaction or target. */
internal object ConsequencePreviewBridge {
    var onStart: ((ConsequenceStep) -> Unit)? = null
    var onUpdate: ((ConsequenceStep) -> Unit)? = null
    var onVisibility: ((Boolean) -> Unit)? = null
    var onLeave: (() -> Unit)? = null
    var onSimulation: (() -> Unit)? = null
    var onPicker: (() -> Unit)? = null
    var ready by mutableStateOf(false)
    var active = false
    var touchPolicy by mutableStateOf(0) // 0: unrestricted, 1: explanation, 2: target only
}

internal fun consequenceExample(
    index: Int,
    target: Rect,
    version: Int,
): ConsequenceStep {
    val amount = if (version == 0) "15,000원" else "18,000원"
    return when (index) {
        1 -> {
            ConsequenceStep(
                "send",
                "보내기 버튼을 눌러 주세요.",
                "누르면 가족에게 사진 3장이 전송돼요.",
                listOf(
                    "받는 사람: 가족 대화방",
                    "사진: 여행 사진 3장",
                    "이 테스트에서는 전송하지 않아요. 실제 전송 전에는 받는 사람과 사진을 다시 확인해야 해요.",
                    "전송 후에는 상대방이 사진을 저장하거나 다른 사람에게 전달할 수 있어요.",
                    "상대방이 저장한 사진까지 회수할 수 있는지는 확인되지 않았어요.",
                    "개인정보가 담긴 사진이 아닌지 확인해 주세요.",
                ),
                true,
                true,
                target,
                version,
                GuideAction.TRANSMISSION,
            )
        }

        2 -> {
            ConsequenceStep(
                "delete",
                "삭제 버튼을 눌러 주세요.",
                "누르면 선택한 사진 3장이 삭제돼요.",
                listOf("대상: 선택한 사진 3장", "이 예시의 삭제는 되돌릴 수 없는 상황이에요.", "테스트 화면이라 실제 사진은 삭제하지 않아요."),
                true,
                true,
                target,
                version,
            )
        }

        3 -> {
            ConsequenceStep(
                "unknown",
                "계속 버튼",
                "버튼을 누른 뒤의 결과를 확인하지 못했어요.",
                listOf("확인된 정보: 버튼 이름은 ‘계속’이에요.", "결제 여부와 금액은 확인되지 않았어요."),
                true,
                false,
                target,
                version,
            )
        }

        4 -> {
            ConsequenceStep(
                "search",
                "검색 버튼을 눌러 주세요.",
                "검색어를 입력하는 화면이 열려요.",
                emptyList(),
                false,
                true,
                target,
                version,
            )
        }

        else -> {
            ConsequenceStep(
                "pay",
                "결제 버튼을 눌러 주세요.",
                "누르면 ${amount}이 결제돼요.",
                listOf("금액: $amount", "받는 곳: 예시 상점", "취소 조건: 확인되지 않았어요.", "실제 결제가 없는 테스트예요."),
                true,
                true,
                target,
                version,
                GuideAction.PAYMENT,
            )
        }
    }
}

/** Claim before speaking so layout/recomposition cannot enqueue the same narration twice. */
internal class ConsequenceNarration {
    var spokenStep: ConsequenceStep? = null

    fun claim(
        step: ConsequenceStep,
        enabled: Boolean,
        ready: Boolean,
        screenReader: Boolean,
        menu: Boolean,
    ): Boolean {
        if (!enabled || !ready || screenReader || menu || spokenStep == step) return false
        spokenStep = step
        return true
    }
}

internal fun isConsequenceTestRequest(request: String): Boolean = request.trim() == "테스트"
