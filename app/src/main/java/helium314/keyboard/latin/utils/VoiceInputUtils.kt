// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings

object VoiceInputUtils {
    private const val TAG = "VoiceInputUtils"

    @JvmStatic
    fun isSonioxConfigured(context: Context): Boolean {
        val prefs = context.prefs()
        val provider = prefs.getString(Settings.PREF_VOICE_INPUT_PROVIDER, Defaults.PREF_VOICE_INPUT_PROVIDER)
        val apiKey = prefs.getString(Settings.PREF_SONIOX_API_KEY, Defaults.PREF_SONIOX_API_KEY)
        val configured = provider == Settings.VOICE_INPUT_PROVIDER_SONIOX && !apiKey.isNullOrBlank()
        Log.d(TAG, "isSonioxConfigured: provider=$provider, hasApiKey=${!apiKey.isNullOrBlank()}, result=$configured")
        return configured
    }

    @JvmStatic
    fun isVoiceInputAvailable(context: Context, richImm: RichInputMethodManager): Boolean {
        return richImm.isShortcutImeReady || isSonioxConfigured(context)
    }
}
