// This file should not be edited manually! See go/template-diff-tests
package template.test.`in`

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class AudioInterface(
    private val context: Context,
    private val initializationMessage: String
) : DefaultLifecycleObserver {
    private lateinit var tts: TextToSpeech
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                speak(initializationMessage)
            } else {
                Log.e(TAG, "Initialization failed with status: $status")
            }
        }
    }

    fun speak(textToSpeak: String) {
        tts.speak(
            textToSpeak,
            TextToSpeech.QUEUE_ADD,
            null,
            initializationMessage.lowercase().replace(" ", "_")
        )
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        tts.shutdown()
    }

    companion object {
        private const val TAG = "AudioInterface"
    }
}