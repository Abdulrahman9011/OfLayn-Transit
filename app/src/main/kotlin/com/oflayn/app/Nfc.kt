package com.oflayn.app

import android.app.Activity
import android.media.AudioManager
import android.media.ToneGenerator
import android.nfc.NfcAdapter
import android.os.VibrationEffect
import android.os.Vibrator
import java.security.MessageDigest

enum class NfcState { UNSUPPORTED, DISABLED, READY, DETECTED }

/**
 * Reads only the tag identifier, which Android exposes without authentication, and stores a SHA-256 hash of it
 * to recognise the same card locally. No DESFire access, no key handling, nothing is written to Logcat.
 */
class NfcController(private val activity: Activity, private val settings: Settings, private val onState: (NfcState) -> Unit) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    fun currentState(): NfcState = when {
        adapter == null -> NfcState.UNSUPPORTED
        !adapter.isEnabled -> NfcState.DISABLED
        else -> NfcState.READY
    }

    @Suppress("DEPRECATION")
    fun start() {
        val a = adapter ?: return onState(NfcState.UNSUPPORTED)
        onState(currentState())
        if (!a.isEnabled) return
        a.enableReaderMode(
            activity,
            { tag ->
                val hash = MessageDigest.getInstance("SHA-256").digest(tag.id).joinToString("") { "%02x".format(it) }
                activity.runOnUiThread {
                    settings.setLinkedCardHash(hash)
                    if (settings.nfcSound) runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 150) }
                    if (settings.vibration) runCatching {
                        (activity.getSystemService(Activity.VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    onState(NfcState.DETECTED)
                }
            },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null,
        )
    }

    fun stop() { runCatching { adapter?.disableReaderMode(activity) } }
}
