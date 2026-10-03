package com.oflayn.domain.voice

import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceStateMachineTest {
    @Test fun fullTurnReturnsToIdle() {
        val m = VoiceStateMachine()
        assertEquals(VoiceAction.START_LISTENING, m.on(VoiceEvent.TapMic).action)
        assertEquals(VoiceAction.SEND_TO_AI, m.on(VoiceEvent.SpeechResult("hi")).action)
        assertEquals(VoiceAction.SPEAK_RESPONSE, m.on(VoiceEvent.ResponseReady).action)
        m.on(VoiceEvent.SpeakingDone)
        assertEquals(VoiceState.IDLE, m.state)
    }

    @Test fun continuousModeReturnsToListening() {
        val m = VoiceStateMachine(continuous = true)
        m.on(VoiceEvent.TapMic); m.on(VoiceEvent.SpeechResult("x")); m.on(VoiceEvent.ResponseReady)
        assertEquals(VoiceAction.START_LISTENING, m.on(VoiceEvent.SpeakingDone).action)
        assertEquals(VoiceState.LISTENING, m.state)
    }

    @Test fun bargeInStopsSpeakingBeforeListening() {
        val m = VoiceStateMachine(continuous = true)
        m.on(VoiceEvent.TapMic); m.on(VoiceEvent.SpeechResult("x")); m.on(VoiceEvent.ResponseReady)
        val t = m.on(VoiceEvent.UserBargeIn)
        assertEquals(VoiceAction.STOP_SPEAKING, t.action)
        assertEquals(VoiceState.LISTENING, t.state)
    }

    @Test fun blankSpeechIsUnclear() {
        val m = VoiceStateMachine()
        m.on(VoiceEvent.TapMic)
        assertEquals(VoiceError.UNCLEAR_SPEECH, m.on(VoiceEvent.SpeechResult("  ")).error)
    }

    @Test fun permissionDeniedIsErrorNotCrash() {
        val m = VoiceStateMachine()
        m.on(VoiceEvent.TapMic)
        assertEquals(VoiceState.ERROR, m.on(VoiceEvent.Failed(VoiceError.PERMISSION_DENIED)).state)
        assertEquals(VoiceState.LISTENING, m.on(VoiceEvent.TapMic).state)
    }

    @Test fun ttsFailureKeepsTextAndGoesIdle() {
        val m = VoiceStateMachine()
        m.on(VoiceEvent.TapMic); m.on(VoiceEvent.SpeechResult("x")); m.on(VoiceEvent.ResponseReady)
        assertEquals(VoiceState.IDLE, m.on(VoiceEvent.Failed(VoiceError.TTS_FAILURE)).state)
    }
}
