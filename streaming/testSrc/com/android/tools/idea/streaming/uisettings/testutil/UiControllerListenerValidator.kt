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

import com.android.tools.idea.streaming.emulator.APPLICATION_ID1
import com.android.tools.idea.streaming.emulator.APPLICATION_ID2
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

internal val DANISH_LANGUAGE = AppLanguage("da", "Danish")
internal val SPANISH_LANGUAGE = AppLanguage("es", "Spanish")
internal val RUSSIAN_LANGUAGE = AppLanguage("ru", "Russian")

/**
 * Sets up model property listeners in order to validate listeners in the device and emulator controller tests.
 *
 * The [ListenerState.lastValue] for each listener will be initialized to:
 * - a default value if customValues is false
 * - a predefined custom value different from the default value if customValues is true
 */
internal class UiControllerListenerValidator(private val model: UiSettingsModel, customValues: Boolean) {
  var localesInitialized = false
  val darkMode = createAndAddListener(model.inDarkMode, customValues)
  val locales = mutableMapOf<String, ListenerState<AppLanguage?>>()
  val appId = createAppIdAddListener(model.appIds.selection, if (customValues) APPLICATION_ID2 else APPLICATION_ID1, customValues)
  val talkBackInstalled = createAndAddListener(model.talkBackInstalled, customValues)
  val talkBackOn = createAndAddListener(model.talkBackOn, customValues)
  val selectToSpeakOn = createAndAddListener(model.selectToSpeakOn, customValues)
  val fontSize = createAndAddListener(model.fontSizeInPercent, if (customValues) CUSTOM_FONT_SIZE else DEFAULT_FONT_SIZE)
  val density = createAndAddListener(model.screenDensity, if (customValues) CUSTOM_DENSITY else DEFAULT_DENSITY)

  /**
   * Check the lastValue and number of changes for each property listener, and make sure they match the property value.
   * [expectedChanges] are the expected number of changes seen by the property listener.
   * The lastValue is expected to be:
   * - a default value if [expectedCustomValues] is false
   * - a predefined custom value different from the default value if [expectedCustomValues] is true
   */
  fun checkValues(expectedChanges: Int, expectedCustomValues: Boolean) {
    assertThat(model.inDarkMode.value).isEqualTo(expectedCustomValues)
    assertThat(darkMode.changes).isEqualTo(expectedChanges)
    assertThat(darkMode.lastValue).isEqualTo(expectedCustomValues)
    assertThat(model.appIds.size).isEqualTo(2)
    assertThat(model.appIds.getElementAt(0)).isEqualTo(APPLICATION_ID1)
    assertThat(model.appIds.getElementAt(1)).isEqualTo(APPLICATION_ID2)
    assertThat(model.appIds.selection.value).isEqualTo(APPLICATION_ID1)
    assertThat(appId.changes).isEqualTo(expectedChanges)
    assertThat(appId.lastValue).isEqualTo(APPLICATION_ID1)
    assertThat(localesInitialized).isTrue()
    assertThat(model.appLanguage.keys).containsExactly(APPLICATION_ID1, APPLICATION_ID2)
    assertThat(model.appLanguage[APPLICATION_ID1]!!.size).isEqualTo(3)
    assertThat(model.appLanguage[APPLICATION_ID1]!!.getElementAt(0)).isEqualTo(DEFAULT_LANGUAGE)
    assertThat(model.appLanguage[APPLICATION_ID1]!!.getElementAt(1)).isEqualTo(DANISH_LANGUAGE)
    assertThat(model.appLanguage[APPLICATION_ID1]!!.getElementAt(2)).isEqualTo(SPANISH_LANGUAGE)
    assertThat(model.appLanguage[APPLICATION_ID2]!!.size).isEqualTo(2)
    assertThat(model.appLanguage[APPLICATION_ID2]!!.getElementAt(0)).isEqualTo(DEFAULT_LANGUAGE)
    assertThat(model.appLanguage[APPLICATION_ID2]!!.getElementAt(1)).isEqualTo(RUSSIAN_LANGUAGE)
    assertThat(model.appLanguage[APPLICATION_ID1]!!.selection.value).isEqualTo(if (expectedCustomValues) DANISH_LANGUAGE else DEFAULT_LANGUAGE)
    assertThat(model.appLanguage[APPLICATION_ID2]!!.selection.value).isEqualTo(if (expectedCustomValues) RUSSIAN_LANGUAGE else DEFAULT_LANGUAGE)
    assertThat(locales[APPLICATION_ID1]!!.changes).isEqualTo(expectedChanges)
    assertThat(locales[APPLICATION_ID2]!!.changes).isEqualTo(expectedChanges)
    assertThat(locales[APPLICATION_ID1]!!.lastValue).isEqualTo(if (expectedCustomValues) DANISH_LANGUAGE else DEFAULT_LANGUAGE)
    assertThat(locales[APPLICATION_ID2]!!.lastValue).isEqualTo(if (expectedCustomValues) RUSSIAN_LANGUAGE else DEFAULT_LANGUAGE)
    assertThat(model.talkBackInstalled.value).isEqualTo(expectedCustomValues)
    assertThat(talkBackInstalled.changes).isEqualTo(expectedChanges)
    assertThat(talkBackInstalled.lastValue).isEqualTo(expectedCustomValues)
    assertThat(model.talkBackOn.value).isEqualTo(expectedCustomValues)
    assertThat(talkBackOn.changes).isEqualTo(expectedChanges)
    assertThat(talkBackOn.lastValue).isEqualTo(expectedCustomValues)
    assertThat(model.selectToSpeakOn.value).isEqualTo(expectedCustomValues)
    assertThat(selectToSpeakOn.changes).isEqualTo(expectedChanges)
    assertThat(selectToSpeakOn.lastValue).isEqualTo(expectedCustomValues)
    assertThat(model.fontSizeInPercent.value).isEqualTo(if (expectedCustomValues) CUSTOM_FONT_SIZE else DEFAULT_FONT_SIZE)
    assertThat(fontSize.changes).isEqualTo(expectedChanges)
    assertThat(fontSize.lastValue).isEqualTo(if (expectedCustomValues) CUSTOM_FONT_SIZE else DEFAULT_FONT_SIZE)
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

  private fun <T> createAppIdAddListener(property: ReadOnlyProperty<T>, initialValue: T, customValues: Boolean): ListenerState<T> {
    val listener = createAndAddListener(property, initialValue)
    addLanguageListeners(customValues, changesDuringInit = 0)
    property.addControllerListener { _ ->
      addLanguageListeners(customValues, changesDuringInit = 1)
    }
    return listener
  }

  private fun addLanguageListeners(customValues: Boolean, changesDuringInit: Int) {
    if (model.appLanguage.isEmpty() || localesInitialized) {
      return
    }
    localesInitialized = true
    model.appLanguage.forEach { (applicationId, localeModel) ->
      val initialLanguage: AppLanguage = when(applicationId) {
        APPLICATION_ID1 -> if (customValues) DANISH_LANGUAGE else DEFAULT_LANGUAGE
        APPLICATION_ID2 -> if (customValues) RUSSIAN_LANGUAGE else DEFAULT_LANGUAGE
        else -> error("Missing test setup")
      }
      locales[applicationId] = createAndAddListener(localeModel.selection, initialLanguage).apply { changes += changesDuringInit }
    }
  }

  /**
   * The state of a property listener containing:
   * - changes: the number of changes seen by the listener
   * - lastValue: the last value seen by the listener
   */
  internal data class ListenerState<T>(var changes: Int, var lastValue: T)
}
