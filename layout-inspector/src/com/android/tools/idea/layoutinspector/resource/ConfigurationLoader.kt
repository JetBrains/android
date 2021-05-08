/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.resource

import com.android.ide.common.resources.configuration.CountryCodeQualifier
import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.HighDynamicRangeQualifier
import com.android.ide.common.resources.configuration.KeyboardStateQualifier
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier
import com.android.ide.common.resources.configuration.NavigationMethodQualifier
import com.android.ide.common.resources.configuration.NavigationStateQualifier
import com.android.ide.common.resources.configuration.NetworkCodeQualifier
import com.android.ide.common.resources.configuration.NightModeQualifier
import com.android.ide.common.resources.configuration.ScreenHeightQualifier
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier
import com.android.ide.common.resources.configuration.ScreenRatioQualifier
import com.android.ide.common.resources.configuration.ScreenRoundQualifier
import com.android.ide.common.resources.configuration.ScreenSizeQualifier
import com.android.ide.common.resources.configuration.ScreenWidthQualifier
import com.android.ide.common.resources.configuration.SmallestScreenWidthQualifier
import com.android.ide.common.resources.configuration.TextInputMethodQualifier
import com.android.ide.common.resources.configuration.TouchScreenQualifier
import com.android.ide.common.resources.configuration.UiModeQualifier
import com.android.ide.common.resources.configuration.VersionQualifier
import com.android.ide.common.resources.configuration.WideGamutColorQualifier
import com.android.resources.Density
import com.android.resources.HighDynamicRange
import com.android.resources.Keyboard
import com.android.resources.KeyboardState
import com.android.resources.LayoutDirection
import com.android.resources.Navigation
import com.android.resources.NavigationState
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenRound
import com.android.resources.ScreenSize
import com.android.resources.TouchScreen
import com.android.resources.UiMode
import com.android.resources.WideGamutColor
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.layoutinspector.common.StringTable
import com.android.tools.idea.layoutinspector.resource.data.Configuration
import com.android.tools.idea.layoutinspector.resource.data.AppContext

/**
 * Load theme, version, and [FolderConfiguration] from a [AppContext] usually received from a device.
 */
class ConfigurationLoader(appContext: AppContext, stringTable: StringTable, apiLevel: Int) {
  val theme = appContext.theme.createReference(stringTable)
  val folderConfiguration = loadFolderConfiguration(appContext.configuration, apiLevel)

  private fun loadFolderConfiguration(configuration: Configuration, apiLevel: Int): FolderConfiguration {
    val config = FolderConfiguration()
    config.countryCodeQualifier = CountryCodeQualifier(configuration.countryCode)
    config.networkCodeQualifier = NetworkCodeQualifier(configuration.networkCode)
    config.layoutDirectionQualifier = layoutDirectionFromRawValue(configuration.screenLayout)
    config.screenRatioQualifier = ScreenRatioQualifier(screenRatioFromRawValue(configuration.screenLayout))
    config.screenRoundQualifier = ScreenRoundQualifier(screenRoundFromRawValue(configuration.screenLayout))
    config.screenSizeQualifier = ScreenSizeQualifier(screenSizeFromRawValue(configuration.screenLayout))
    config.smallestScreenWidthQualifier = SmallestScreenWidthQualifier(configuration.smallestScreenWidth)
    config.screenWidthQualifier = ScreenWidthQualifier(configuration.screenWidth)
    config.screenHeightQualifier = ScreenHeightQualifier(configuration.screenHeight)
    config.wideColorGamutQualifier = wideColorGamutFromRawValue(configuration.colorMode)
    config.highDynamicRangeQualifier = highDynamicRangeFromRawValue(configuration.colorMode)
    config.screenOrientationQualifier = orientationFromRawValue(configuration.orientation)
    config.uiModeQualifier = uiModeFromRawValue(configuration.uiMode)
    config.nightModeQualifier = nightModeFromRawValue(configuration.uiMode)
    config.densityQualifier = Density.getEnum(configuration.density)?.let { DensityQualifier(it) }
    config.touchTypeQualifier = touchScreenFromRawValue(configuration.touchScreen)
    config.textInputMethodQualifier = keyboardFromRawValue(configuration.keyboard)
    config.keyboardStateQualifier = keyboardStateFromRawValue(configuration.keyboardHidden, configuration.hardKeyboardHidden)
    config.navigationMethodQualifier = navigationMethodFromRawValue(configuration.navigation)
    config.navigationStateQualifier = navigationStateFromRawValue(configuration.navigationHidden)
    config.screenWidthQualifier = ScreenWidthQualifier(configuration.screenWidth)
    config.screenHeightQualifier = ScreenHeightQualifier(configuration.screenHeight)
    config.versionQualifier = VersionQualifier(apiLevel)
    return config
  }

  private fun layoutDirectionFromRawValue(value: Int): LayoutDirectionQualifier? =
    when {
      value.and(SCREENLAYOUT_LAYOUTDIR_LTR) != 0 -> LayoutDirection.LTR
      value.and(SCREENLAYOUT_LAYOUTDIR_RTL) != 0 -> LayoutDirection.RTL
      else -> null
    }?.let { LayoutDirectionQualifier(it) }

  private fun screenRatioFromRawValue(value: Int): ScreenRatio? =
    when {
      value.and(SCREENLAYOUT_LONG_YES) != 0 -> ScreenRatio.LONG
      value.and(SCREENLAYOUT_LONG_NO) != 0 -> ScreenRatio.NOTLONG
      else -> null
    }

  private fun screenRoundFromRawValue(value: Int): ScreenRound? =
    when {
      value.and(SCREENLAYOUT_ROUND_YES) != 0 -> ScreenRound.ROUND
      value.and(SCREENLAYOUT_ROUND_NO) != 0 -> ScreenRound.NOTROUND
      else -> null
    }

  private fun screenSizeFromRawValue(value: Int): ScreenSize? =
    when (value.and(SCREENLAYOUT_SIZE_MASK)) {
      SCREENLAYOUT_SIZE_SMALL -> ScreenSize.SMALL
      SCREENLAYOUT_SIZE_NORMAL -> ScreenSize.NORMAL
      SCREENLAYOUT_SIZE_LARGE -> ScreenSize.LARGE
      SCREENLAYOUT_SIZE_XLARGE -> ScreenSize.XLARGE
      else -> null
    }

  private fun wideColorGamutFromRawValue(value: Int): WideGamutColorQualifier? =
    when (value.and(COLOR_MODE_WIDE_COLOR_GAMUT_MASK)) {
      COLOR_MODE_WIDE_COLOR_GAMUT_YES -> WideGamutColor.WIDECG
      COLOR_MODE_WIDE_COLOR_GAMUT_NO -> WideGamutColor.NOWIDECG
      else -> null
    }?.let { WideGamutColorQualifier(it) }

  private fun highDynamicRangeFromRawValue(value: Int): HighDynamicRangeQualifier? =
    when (value.and(COLOR_MODE_HDR_MASK)) {
      COLOR_MODE_HDR_YES -> HighDynamicRange.HIGHDR
      COLOR_MODE_HDR_NO -> HighDynamicRange.LOWDR
      else -> null
    }?.let { HighDynamicRangeQualifier(it) }

  private fun orientationFromRawValue(value: Int): ScreenOrientationQualifier? =
    when (value) {
      ORIENTATION_PORTRAIT -> ScreenOrientation.PORTRAIT
      ORIENTATION_LANDSCAPE -> ScreenOrientation.LANDSCAPE
      else -> null
    }?.let { ScreenOrientationQualifier(it) }

  private fun uiModeFromRawValue(value: Int): UiModeQualifier? =
    when (value.and(UI_MODE_TYPE_MASK)) {
      UI_MODE_TYPE_NORMAL -> UiMode.NORMAL
      UI_MODE_TYPE_DESK -> UiMode.DESK
      UI_MODE_TYPE_CAR -> UiMode.CAR
      UI_MODE_TYPE_TELEVISION -> UiMode.TELEVISION
      UI_MODE_TYPE_VR_HEADSET -> UiMode.VR_HEADSET
      UI_MODE_TYPE_APPLIANCE -> UiMode.APPLIANCE
      UI_MODE_TYPE_WATCH -> UiMode.WATCH
      else -> null
    }?.let { UiModeQualifier(it) }

  private fun nightModeFromRawValue(value: Int): NightModeQualifier? =
    when (value.and(UI_MODE_NIGHT_MASK)) {
      UI_MODE_NIGHT_YES -> NightMode.NIGHT
      UI_MODE_NIGHT_NO -> NightMode.NOTNIGHT
      else -> null
    }?.let { NightModeQualifier(it) }

  private fun touchScreenFromRawValue(value: Int): TouchScreenQualifier? =
    when (value) {
      TOUCHSCREEN_NOTOUCH -> TouchScreen.NOTOUCH
      TOUCHSCREEN_STYLUS -> TouchScreen.STYLUS
      TOUCHSCREEN_FINGER -> TouchScreen.FINGER
      else -> null
    }?.let { TouchScreenQualifier(it) }

  private fun keyboardFromRawValue(value: Int): TextInputMethodQualifier? =
    when (value) {
      KEYBOARD_NOKEYS -> Keyboard.NOKEY
      KEYBOARD_QWERTY -> Keyboard.QWERTY
      KEYBOARD_12KEY -> Keyboard.TWELVEKEY
      else -> null
    }?.let { TextInputMethodQualifier(it) }

  private fun keyboardStateFromRawValue(value: Int, hardValue: Int): KeyboardStateQualifier? =
    when {
      hardValue == HARDKEYBOARDHIDDEN_NO -> KeyboardState.EXPOSED
      value == KEYBOARDHIDDEN_YES -> KeyboardState.HIDDEN
      hardValue == HARDKEYBOARDHIDDEN_YES && value == KEYBOARDHIDDEN_NO -> KeyboardState.SOFT
      else -> null
    }?.let { KeyboardStateQualifier(it) }

  private fun navigationStateFromRawValue(value: Int): NavigationStateQualifier? =
    when (value) {
      NAVIGATIONHIDDEN_NO -> NavigationState.EXPOSED
      NAVIGATIONHIDDEN_YES -> NavigationState.HIDDEN
      else -> null
    }?.let { NavigationStateQualifier(it) }

  private fun navigationMethodFromRawValue(value: Int): NavigationMethodQualifier? =
    when (value) {
      NAVIGATION_NONAV -> Navigation.NONAV
      NAVIGATION_DPAD -> Navigation.DPAD
      NAVIGATION_TRACKBALL -> Navigation.TRACKBALL
      NAVIGATION_WHEEL -> Navigation.WHEEL
      else -> null
    }?.let { NavigationMethodQualifier(it) }
}
