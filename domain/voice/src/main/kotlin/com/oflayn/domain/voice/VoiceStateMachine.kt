package com.oflayn.domain.voice

enum class VoiceState { IDLE, LISTENING, PROCESSING, SPEAKING, ERROR }

enum class VoiceError { PERMISSION_DENIED, NO_MICROPHONE, STT_FAILURE, UNCLEAR_SPEECH, OFFLINE_STT_UNAVAILABLE, TTS_FAILURE, NETWORK, CANCELLED }

sealed interface VoiceEvent {
    data object TapMic : VoiceEvent
    data object SpeechStarted : VoiceEvent
    data class SpeechResult(val text: String) : VoiceEvent
    data object ResponseReady : VoiceEvent
    data object SpeakingDone : VoiceEvent
    /** User began talking while TTS is playing (barge-in). */
    data object UserBargeIn : VoiceEvent
    data class Failed(val error: VoiceError) : VoiceEvent
    data object Stop : VoiceEvent
}

/** Side effects the host must perform after a transition. */
enum class VoiceAction { START_LISTENING, STOP_LISTENING, STOP_SPEAKING, SEND_TO_AI, SPEAK_RESPONSE, NONE }

data class VoiceTransition(val state: VoiceState, val action: VoiceAction, val error: VoiceError? = null)

/**
 * Pure state machine. Invariant: STT and TTS are never active at the same time (prevents audio feedback loops).
 * In continuous mode, after SPEAKING completes the machine goes straight back to LISTENING.
 */
class VoiceStateMachine(var continuous: Boolean = false) {
    var state: VoiceState = VoiceState.IDLE
        private set

    fun on(event: VoiceEvent): VoiceTransition {
        val t = reduce(event)
        state = t.state
        return t
    }

    private fun reduce(e: VoiceEvent): VoiceTransition = when (e) {
        VoiceEvent.TapMic -> when (state) {
            VoiceState.IDLE, VoiceState.ERROR -> VoiceTransition(VoiceState.LISTENING, VoiceAction.START_LISTENING)
            VoiceState.LISTENING -> VoiceTransition(VoiceState.IDLE, VoiceAction.STOP_LISTENING)
            VoiceState.SPEAKING -> VoiceTransition(VoiceState.LISTENING, VoiceAction.STOP_SPEAKING)
            VoiceState.PROCESSING -> VoiceTransition(state, VoiceAction.NONE)
        }
        VoiceEvent.SpeechStarted -> VoiceTransition(state, VoiceAction.NONE)
        is VoiceEvent.SpeechResult -> when {
            state != VoiceState.LISTENING -> VoiceTransition(state, VoiceAction.NONE)
            e.text.isBlank() -> VoiceTransition(VoiceState.ERROR, VoiceAction.NONE, VoiceError.UNCLEAR_SPEECH)
            else -> VoiceTransition(VoiceState.PROCESSING, VoiceAction.SEND_TO_AI)
        }
        VoiceEvent.ResponseReady ->
            if (state == VoiceState.PROCESSING) VoiceTransition(VoiceState.SPEAKING, VoiceAction.SPEAK_RESPONSE) else VoiceTransition(state, VoiceAction.NONE)
        VoiceEvent.SpeakingDone -> when {
            state != VoiceState.SPEAKING -> VoiceTransition(state, VoiceAction.NONE)
            continuous -> VoiceTransition(VoiceState.LISTENING, VoiceAction.START_LISTENING)
            else -> VoiceTransition(VoiceState.IDLE, VoiceAction.NONE)
        }
        VoiceEvent.UserBargeIn ->
            if (state == VoiceState.SPEAKING) VoiceTransition(VoiceState.LISTENING, VoiceAction.STOP_SPEAKING) else VoiceTransition(state, VoiceAction.NONE)
        is VoiceEvent.Failed -> when {
            e.error == VoiceError.CANCELLED -> VoiceTransition(VoiceState.IDLE, VoiceAction.NONE)
            e.error == VoiceError.TTS_FAILURE -> VoiceTransition(VoiceState.IDLE, VoiceAction.NONE, e.error) // text is still shown
            else -> VoiceTransition(VoiceState.ERROR, VoiceAction.NONE, e.error)
        }
        VoiceEvent.Stop -> VoiceTransition(
            VoiceState.IDLE,
            if (state == VoiceState.SPEAKING) VoiceAction.STOP_SPEAKING else if (state == VoiceState.LISTENING) VoiceAction.STOP_LISTENING else VoiceAction.NONE,
        )
    }
}
