// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.permissions.PermissionsUtil
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.latin.voice.SttTextUtils
import helium314.keyboard.settings.preferences.TextInputPreference
import helium314.keyboard.latin.utils.previewDark

@Composable
fun CustomExtensionsScreen(onClickBack: () -> Unit) {
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_custom_extensions),
        settings = listOf(
            Settings.PREF_VOICE_INPUT_PROVIDER,
            Settings.PREF_VOICE_KEY_PLACEMENT,
            Settings.PREF_SONIOX_API_KEY,
            Settings.PREF_SONIOX_MODEL,
            Settings.PREF_ALLOW_VOICE_ON_PASSWORD,
            Settings.PREF_SONIOX_LISTENING_SOUND,
            Settings.PREF_SONIOX_LISTENING_VIBRATE,
            Settings.PREF_SONIOX_STRIP_PUNCTUATION,
            Settings.PREF_SONIOX_STRIP_PUNCTUATION_CHARS,
            Settings.PREF_SONIOX_PARTIAL_IN_INPUT,
            Settings.PREF_SONIOX_SILENCE_TIMEOUT,
        ),
    )
}

fun createCustomExtensionsSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_VOICE_INPUT_PROVIDER, R.string.voice_input_provider) { setting ->
        val activity = LocalContext.current.getActivity() ?: return@Setting
        var micGranted by remember {
            mutableStateOf(PermissionsUtil.checkAllPermissionsGranted(activity, Manifest.permission.RECORD_AUDIO))
        }
        val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            micGranted = it
        }
        val items = listOf(
            stringResource(R.string.voice_input_provider_system) to Settings.VOICE_INPUT_PROVIDER_SYSTEM,
            stringResource(R.string.voice_input_provider_soniox) to Settings.VOICE_INPUT_PROVIDER_SONIOX,
        )
        ListPreference(setting, items, Defaults.PREF_VOICE_INPUT_PROVIDER) { selected ->
            if (selected == Settings.VOICE_INPUT_PROVIDER_SONIOX && !micGranted) {
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
    },
    Setting(context, Settings.PREF_VOICE_KEY_PLACEMENT, R.string.voice_key_placement) { setting ->
        val items = listOf(
            stringResource(R.string.voice_key_placement_dedicated) to Settings.VOICE_KEY_PLACEMENT_DEDICATED,
            stringResource(R.string.voice_key_placement_period_long_press) to Settings.VOICE_KEY_PLACEMENT_PERIOD_LONG_PRESS,
            stringResource(R.string.voice_key_placement_toolbar) to Settings.VOICE_KEY_PLACEMENT_TOOLBAR,
        )
        ListPreference(setting, items, Defaults.PREF_VOICE_KEY_PLACEMENT) {
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
    },
    Setting(context, Settings.PREF_SONIOX_API_KEY,
        R.string.soniox_api_key, R.string.soniox_api_key_summary)
    { setting ->
        TextInputPreference(setting, Defaults.PREF_SONIOX_API_KEY) { it.isNotBlank() }
    },
    Setting(context, Settings.PREF_SONIOX_MODEL,
        R.string.soniox_model, R.string.soniox_model_summary)
    { setting ->
        TextInputPreference(setting, Defaults.PREF_SONIOX_MODEL) { it.isNotBlank() }
    },
    Setting(context, Settings.PREF_ALLOW_VOICE_ON_PASSWORD,
        R.string.allow_voice_on_password, R.string.allow_voice_on_password_summary)
    {
        SwitchPreference(it, Defaults.PREF_ALLOW_VOICE_ON_PASSWORD) {
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
    },
    Setting(context, Settings.PREF_SONIOX_LISTENING_SOUND,
        R.string.voice_listening_start_sound, R.string.voice_listening_start_sound_summary)
    {
        SwitchPreference(it, Defaults.PREF_SONIOX_LISTENING_SOUND)
    },
    Setting(context, Settings.PREF_SONIOX_LISTENING_VIBRATE,
        R.string.voice_listening_start_vibrate, R.string.voice_listening_start_vibrate_summary)
    {
        SwitchPreference(it, Defaults.PREF_SONIOX_LISTENING_VIBRATE)
    },
    Setting(context, Settings.PREF_SONIOX_STRIP_PUNCTUATION,
        R.string.soniox_strip_punctuation, R.string.soniox_strip_punctuation_summary)
    {
        SwitchPreference(it, Defaults.PREF_SONIOX_STRIP_PUNCTUATION) {
            KeyboardSwitcher.getInstance().mainKeyboardView?.refreshVoiceKeyVisuals()
        }
    },
    Setting(context, Settings.PREF_SONIOX_STRIP_PUNCTUATION_CHARS,
        R.string.soniox_strip_punctuation_chars, R.string.soniox_strip_punctuation_chars_summary)
    { setting ->
        TextInputPreference(setting, Defaults.PREF_SONIOX_STRIP_PUNCTUATION_CHARS) { spec ->
            spec.isNotBlank() && SttTextUtils.parseStripChars(spec).isNotEmpty()
        }
    },
    Setting(context, Settings.PREF_SONIOX_PARTIAL_IN_INPUT,
        R.string.soniox_partial_in_input, R.string.soniox_partial_in_input_summary)
    {
        SwitchPreference(it, Defaults.PREF_SONIOX_PARTIAL_IN_INPUT)
    },
    Setting(context, Settings.PREF_SONIOX_SILENCE_TIMEOUT,
        R.string.soniox_silence_timeout, R.string.soniox_silence_timeout_summary) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_SONIOX_SILENCE_TIMEOUT,
            description = {
                if (it > Settings.SONIOX_SILENCE_TIMEOUT_MAX) stringResource(R.string.settings_no_limit)
                else stringResource(R.string.abbreviation_unit_seconds, it.toString())
            },
            range = Settings.SONIOX_SILENCE_TIMEOUT_MIN.toFloat()..Settings.SONIOX_SILENCE_TIMEOUT_INFINITE.toFloat(),
            stepSize = 5,
        )
    },
)

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            CustomExtensionsScreen { }
        }
    }
}
