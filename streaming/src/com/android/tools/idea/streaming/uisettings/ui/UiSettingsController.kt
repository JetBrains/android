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
package com.android.tools.idea.streaming.uisettings.ui

import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.tools.idea.streaming.uisettings.data.AppLanguage
import com.android.tools.idea.streaming.uisettings.data.DEFAULT_LANGUAGE
import com.android.tools.idea.streaming.uisettings.data.convertFromLocaleConfig
import com.android.tools.idea.streaming.uisettings.stats.LoggingChangeListener
import com.android.tools.idea.streaming.uisettings.stats.UiSettingsStats

/**
 * A controller for the [UiSettingsPanel] that populates the model and reacts to changes to the model initiated by the UI.
 */
internal abstract class UiSettingsController(
  /**
   * The model that this controller is interacting with.
   */
  protected val model: UiSettingsModel,

  /**
   * Logger for statistics
   */
  private val stats: UiSettingsStats
) {

  init {
    model.inDarkMode.uiChangeListener = LoggingChangeListener(::setDarkMode, stats::setDarkMode)
    model.fontScaleInPercent.uiChangeListener = LoggingChangeListener(::setFontScale, stats::setFontScale)
    model.screenDensity.uiChangeListener = LoggingChangeListener(::setScreenDensity, stats::setScreenDensity)
    model.talkBackOn.uiChangeListener = LoggingChangeListener(::setTalkBack, stats::setTalkBack)
    model.selectToSpeakOn.uiChangeListener = LoggingChangeListener(::setSelectToSpeak, stats::setSelectToSpeak)
    model.gestureNavigation.uiChangeListener = LoggingChangeListener(::setGestureNavigation, stats::setGestureNavigation)
    model.debugLayout.uiChangeListener =  LoggingChangeListener(::setDebugLayout, stats::setDebugLayout)
    model.resetAction = { reset(); stats.reset() }
  }

  /**
   * Populate all settings in the model.
   */
  abstract suspend fun populateModel()

  fun addLanguage(applicationId: String, localeConfig: Set<LocaleQualifier>, selectedLocaleTag: String): Boolean {
    val languages = convertFromLocaleConfig(localeConfig)
    if (applicationId.isEmpty() || languages.size < 2) {
      return false
    }
    model.appLanguage.removeAllElements()
    model.appLanguage.addAll(languages)
    model.appLanguage.selection.setFromController(languages.find { it.tag == selectedLocaleTag } ?: DEFAULT_LANGUAGE)
    model.appLanguage.selection.clearUiChangeListener()
    model.appLanguage.selection.uiChangeListener = LoggingChangeListener({setAppLanguage(applicationId, it)}, stats::setAppLanguage)
    return true
  }

  /**
   * Changes the dark mode on the device/emulator.
   */
  protected abstract fun setDarkMode(on: Boolean)

  /**
   * Changes the font scale on the device/emulator.
   */
  protected abstract fun setFontScale(percent: Int)

  /**
   * Changes the screen density on the device/emulator.
   */
  protected abstract fun setScreenDensity(density: Int)

  /**
   * Turns TackBack on or off.
   */
  protected abstract fun setTalkBack(on: Boolean)

  /**
   * Turns Select to Speak on or off.
   */
  protected abstract fun setSelectToSpeak(on: Boolean)

  /**
   * Changes the navigation mode on the device to use gestures instead of buttons.
   */
  protected abstract fun setGestureNavigation(on: Boolean)

  /**
   * Turns debug layout boxes on or off.
   */
  protected abstract fun setDebugLayout(on: Boolean)

  /**
   * Changes the application language of the project application on the device/emulator.
   * A null language means the same as the default AppLanguage.
   */
  protected abstract fun setAppLanguage(applicationId: String, language: AppLanguage?)

  /**
   * Reset UI settings to factory defaults.
   */
  protected abstract fun reset()
}
