/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.streaming.uisettings.testutil

import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.tools.idea.streaming.emulator.CUSTOM_DENSITY
import com.android.tools.idea.streaming.emulator.CUSTOM_FONT_SIZE
import com.android.tools.idea.streaming.emulator.DEFAULT_DENSITY
import com.android.tools.idea.streaming.emulator.DEFAULT_FONT_SIZE
import com.android.tools.idea.streaming.uisettings.binding.ReadOnlyProperty
import com.android.tools.idea.streaming.uisettings.data.AppLanguage
import com.android.tools.idea.streaming.uisettings.data.DEFAULT_LANGUAGE
import com.android.tools.idea.streaming.uisettings.testutil.UiControllerListenerValidator.ListenerState
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsModel
import com.google.common.truth.Truth.assertThat

internal val DANISH_LANGUAGE = AppLanguage(LocaleQualifier(null, "da", null, null), "Danish")
internal val RUSSIAN_LANGUAGE = AppLanguage(LocaleQualifier(null, "ru", null, null), "Russian")

/**
 * Sets up model property listeners in order to validate listeners in the device and emulator controller tests.
 *
 * The [ListenerState.lastValue] for each listener will be initialized to:
 * - a default value if customValues is false
 * - a predefined custom value different from the default value if customValues is true
 */
internal class UiControllerListenerValidator(private val model: UiSettingsModel, customValues: Boolean, settable: Boolean) {
  val darkMode = createAndAddListener(model.inDarkMode, customValues)
  val gestureNavigation = createAndAddListener(model.gestureNavigation, customValues)
  val appLanguage = createAndAddListener(model.appLanguage.selection, if (customValues) DANISH_LANGUAGE else DEFAULT_LANGUAGE)
  val talkBackInstalled = createAndAddListener(model.talkBackInstalled, customValues)
  val talkBackOn = createAndAddListener(model.talkBackOn, customValues)
  val selectToSpeakOn = createAndAddListener(model.selectToSpeakOn, customValues)
  val fontSizeSettable = createAndAddListener(model.fontSizeSettable, settable)
  val fontSize = createAndAddListener(model.fontSizeInPercent, if (customValues) CUSTOM_FONT_SIZE else DEFAULT_FONT_SIZE)
  val densitySettable = createAndAddListener(model.screenDensitySettable, settable)
  val density = createAndAddListener(model.screenDensity, if (customValues) CUSTOM_DENSITY else DEFAULT_DENSITY)

  /**
   * Check the lastValue and number of changes for each property listener, and make sure they match the property value.
   * [expectedChanges] are the expected number of changes seen by the property listener.
   * The lastValue is expected to be:
   * - a default value if [expectedCustomValues] is false
   * - a predefined custom value different from the default value if [expectedCustomValues] is true
   */
  fun checkValues(expectedChanges: Int, expectedCustomValues: Boolean, expectedSettable: Boolean) {
    assertThat(model.inDarkMode.value).isEqualTo(expectedCustomValues)
    assertThat(darkMode.changes).isEqualTo(expectedChanges)
    assertThat(darkMode.lastValue).isEqualTo(expectedCustomValues)
    assertThat(model.gestureNavigation.value).isEqualTo(expectedCustomValues)
    assertThat(gestureNavigation.changes).isEqualTo(expectedChanges)
    assertThat(gestureNavigation.lastValue).isEqualTo(expectedCustomValues)
    assertThat(model.appLanguage.size).isEqualTo(3)
    assertThat(model.appLanguage.getElementAt(0)).isEqualTo(DEFAULT_LANGUAGE)
    assertThat(model.appLanguage.getElementAt(1)).isEqualTo(DANISH_LANGUAGE)
    assertThat(model.appLanguage.getElementAt(2)).isEqualTo(RUSSIAN_LANGUAGE)
    assertThat(model.appLanguage.selection.value).isEqualTo(if (expectedCustomValues) DANISH_LANGUAGE else DEFAULT_LANGUAGE)
    assertThat(appLanguage.changes).isEqualTo(expectedChanges)
    assertThat(appLanguage.lastValue).isEqualTo(if (expectedCustomValues) DANISH_LANGUAGE else DEFAULT_LANGUAGE)
    assertThat(model.talkBackInstalled.value).isEqualTo(expectedCustomValues)
    assertThat(talkBackInstalled.changes).isEqualTo(expectedChanges)
    assertThat(talkBackInstalled.lastValue).isEqualTo(expectedCustomValues)
    assertThat(model.talkBackOn.value).isEqualTo(expectedCustomValues)
    assertThat(talkBackOn.changes).isEqualTo(expectedChanges)
    assertThat(talkBackOn.lastValue).isEqualTo(expectedCustomValues)
    assertThat(model.selectToSpeakOn.value).isEqualTo(expectedCustomValues)
    assertThat(selectToSpeakOn.changes).isEqualTo(expectedChanges)
    assertThat(selectToSpeakOn.lastValue).isEqualTo(expectedCustomValues)
    assertThat(model.fontSizeSettable.value).isEqualTo(expectedSettable)
    assertThat(fontSizeSettable.changes).isEqualTo(expectedChanges)
    assertThat(fontSizeSettable.lastValue).isEqualTo(expectedSettable)
    assertThat(model.fontSizeInPercent.value).isEqualTo(if (expectedCustomValues) CUSTOM_FONT_SIZE else DEFAULT_FONT_SIZE)
    assertThat(fontSize.changes).isEqualTo(expectedChanges)
    assertThat(fontSize.lastValue).isEqualTo(if (expectedCustomValues) CUSTOM_FONT_SIZE else DEFAULT_FONT_SIZE)
    assertThat(model.screenDensitySettable.value).isEqualTo(expectedSettable)
    assertThat(densitySettable.changes).isEqualTo(expectedChanges)
    assertThat(densitySettable.lastValue).isEqualTo(expectedSettable)
    assertThat(model.screenDensity.value).isEqualTo(if (expectedCustomValues) CUSTOM_DENSITY else DEFAULT_DENSITY)
    assertThat(density.changes).isEqualTo(expectedChanges)
    assertThat(density.lastValue).isEqualTo(if (expectedCustomValues) CUSTOM_DENSITY else DEFAULT_DENSITY)
  }

  private fun <T> createAndAddListener(property: ReadOnlyProperty<T>, initialValue: T): ListenerState<T> {
    val state = ListenerState(0, initialValue)
    property.addControllerListener { newValue ->
      state.changes++
      state.lastValue = newValue
    }
    return state
  }

  /**
   * The state of a property listener containing:
   * - changes: the number of changes seen by the listener
   * - lastValue: the last value seen by the listener
   */
  internal data class ListenerState<T>(var changes: Int, var lastValue: T)
}
