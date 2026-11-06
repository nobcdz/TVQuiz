package com.nobcdz.tvquiz

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.KeyEvent

@Suppress("DEPRECATION")
internal class GateAudio(context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val recovery = context.getSharedPreferences("gate_audio", Context.MODE_PRIVATE)
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }
    private var blocking = false
    private var ownsMute = false
    private var hasFocus = false

    fun update(block: Boolean) {
        try {
            if (block) {
                if (!blocking) {
                    blocking = true
                    if (audio.isMusicActive) {
                        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
                        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
                    }
                    hasFocus = audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                }
                muteMedia()
            } else {
                blocking = false
                restoreMute()
                if (hasFocus) {
                    audio.abandonAudioFocus(focusListener)
                    hasFocus = false
                }
            }
        } catch (error: RuntimeException) {
            Log.w("TVQuizAudio", "Unable to update media audio", error)
        }
    }

    private fun muteMedia() {
        if (audio.isVolumeFixed) return
        val stream = AudioManager.STREAM_MUSIC
        if (Build.VERSION.SDK_INT >= 23) {
            if (audio.isStreamMute(stream)) return
            // Record ownership before changing a system-wide mute that can outlive this process.
            if (!recovery.edit().putBoolean("restore_media", true).commit()) return
            audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
        } else if (!ownsMute) {
            audio.setStreamMute(stream, true)
            ownsMute = true
        }
    }

    private fun restoreMute() {
        val stream = AudioManager.STREAM_MUSIC
        if (Build.VERSION.SDK_INT >= 23) {
            if (!recovery.getBoolean("restore_media", false)) return
            audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
            recovery.edit().remove("restore_media").commit()
        } else if (ownsMute) {
            audio.setStreamMute(stream, false)
            ownsMute = false
        }
    }
}
