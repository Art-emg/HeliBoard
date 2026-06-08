// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.SimplePopups
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.toolbarKeyStrings

/** Applies voice-key placement to parsed keyboard rows. */
object VoiceKeyboardLayout {
    private const val DEDICATED_VOICE_KEY_WIDTH = 0.10f
    /** Extra shrink on the space bar so the mic key does not crowd Enter. */
    private const val SPACE_EXTRA_SHRINK = 0.015f

    /** Long-press popup on the dedicated mic key: keep vs strip STT punctuation. */
    private val voicePunctPopupKeys = listOf(
        "!fixedColumnOrder!2",
        "!icon/soniox_keep_punct|!code/key_soniox_keep_punct",
        "!icon/soniox_strip_punct|!code/key_soniox_strip_punct",
    )

    fun apply(keysInRows: ArrayList<ArrayList<Key.KeyParams>>, params: KeyboardParams) {
        val settings = Settings.getValues()
        if (!settings.mShowsVoiceInputKey) return
        when (settings.mVoiceKeyPlacement) {
            Settings.VOICE_KEY_PLACEMENT_DEDICATED -> injectDedicatedVoiceKey(keysInRows, params)
            Settings.VOICE_KEY_PLACEMENT_PERIOD_LONG_PRESS -> markPeriodVoiceLongPress(keysInRows)
        }
    }

    private fun injectDedicatedVoiceKey(
        keysInRows: ArrayList<ArrayList<Key.KeyParams>>,
        params: KeyboardParams,
    ) {
        val bottomRow = keysInRows.lastOrNull() ?: return
        if (bottomRow.any { it.mCode == KeyCode.VOICE_INPUT }) return
        val actionIndex = bottomRow.indexOfFirst { it.mBackgroundType == Key.BACKGROUND_TYPE_ACTION }
        if (actionIndex < 0) return
        bottomRow.add(
            actionIndex,
            Key.KeyParams(
                "!icon/${toolbarKeyStrings[ToolbarKey.VOICE]}|!code/key_voice_input",
                KeyCode.VOICE_INPUT,
                params,
                DEDICATED_VOICE_KEY_WIDTH,
                Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR,
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                SimplePopups(voicePunctPopupKeys),
            ),
        )
        // Voice key is inserted after row width balancing; shrink space so the row fits.
        rebalanceBottomRowForVoiceKey(bottomRow)
    }

    private fun rebalanceBottomRowForVoiceKey(bottomRow: ArrayList<Key.KeyParams>) {
        var totalWidth = 0f
        bottomRow.forEach { totalWidth += it.mWidth }
        if (totalWidth <= 1f) return
        val overflow = totalWidth - 1f + SPACE_EXTRA_SHRINK
        val spaceIndex = bottomRow.indexOfFirst { it.mBackgroundType == Key.BACKGROUND_TYPE_SPACEBAR }
        if (spaceIndex >= 0) {
            val space = bottomRow[spaceIndex]
            space.mWidth = (space.mWidth - overflow).coerceAtLeast(space.mWidth * 0.72f)
        } else {
            val scale = 1f / totalWidth
            bottomRow.forEach { if (it.mWidth > 0 && !it.isSpacer) it.mWidth *= scale }
        }
    }

    private fun markPeriodVoiceLongPress(keysInRows: ArrayList<ArrayList<Key.KeyParams>>) {
        val bottomRow = keysInRows.lastOrNull() ?: return
        val actionIndex = bottomRow.indexOfFirst { it.mBackgroundType == Key.BACKGROUND_TYPE_ACTION }
        if (actionIndex <= 0) return
        val periodIndex = actionIndex - 1
        val period = bottomRow[periodIndex]
        if (period.isSpacer || period.mBackgroundType == Key.BACKGROUND_TYPE_SPACEBAR) return
        bottomRow[periodIndex] = period.withVoiceLongPressMicHint()
    }
}

fun SettingsValues.isVoiceKeyOnToolbar(): Boolean =
    mShowsVoiceInputKey && mVoiceKeyPlacement == Settings.VOICE_KEY_PLACEMENT_TOOLBAR
