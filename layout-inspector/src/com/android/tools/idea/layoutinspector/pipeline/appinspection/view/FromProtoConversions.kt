/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.view

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
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.properties.PropertyType
import com.android.tools.idea.layoutinspector.resource.COLOR_MODE_HDR_MASK
import com.android.tools.idea.layoutinspector.resource.COLOR_MODE_HDR_NO
import com.android.tools.idea.layoutinspector.resource.COLOR_MODE_HDR_YES
import com.android.tools.idea.layoutinspector.resource.COLOR_MODE_WIDE_COLOR_GAMUT_MASK
import com.android.tools.idea.layoutinspector.resource.COLOR_MODE_WIDE_COLOR_GAMUT_NO
import com.android.tools.idea.layoutinspector.resource.COLOR_MODE_WIDE_COLOR_GAMUT_YES
import com.android.tools.idea.layoutinspector.resource.HARDKEYBOARDHIDDEN_NO
import com.android.tools.idea.layoutinspector.resource.HARDKEYBOARDHIDDEN_YES
import com.android.tools.idea.layoutinspector.resource.KEYBOARDHIDDEN_NO
import com.android.tools.idea.layoutinspector.resource.KEYBOARDHIDDEN_YES
import com.android.tools.idea.layoutinspector.resource.KEYBOARD_12KEY
import com.android.tools.idea.layoutinspector.resource.KEYBOARD_NOKEYS
import com.android.tools.idea.layoutinspector.resource.KEYBOARD_QWERTY
import com.android.tools.idea.layoutinspector.resource.NAVIGATIONHIDDEN_NO
import com.android.tools.idea.layoutinspector.resource.NAVIGATIONHIDDEN_YES
import com.android.tools.idea.layoutinspector.resource.NAVIGATION_DPAD
import com.android.tools.idea.layoutinspector.resource.NAVIGATION_NONAV
import com.android.tools.idea.layoutinspector.resource.NAVIGATION_TRACKBALL
import com.android.tools.idea.layoutinspector.resource.NAVIGATION_WHEEL
import com.android.tools.idea.layoutinspector.resource.ORIENTATION_LANDSCAPE
import com.android.tools.idea.layoutinspector.resource.ORIENTATION_PORTRAIT
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_LAYOUTDIR_LTR
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_LAYOUTDIR_RTL
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_LONG_NO
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_LONG_YES
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_ROUND_NO
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_ROUND_YES
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_SIZE_LARGE
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_SIZE_MASK
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_SIZE_NORMAL
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_SIZE_SMALL
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_SIZE_XLARGE
import com.android.tools.idea.layoutinspector.resource.TOUCHSCREEN_FINGER
import com.android.tools.idea.layoutinspector.resource.TOUCHSCREEN_NOTOUCH
import com.android.tools.idea.layoutinspector.resource.TOUCHSCREEN_STYLUS
import com.android.tools.idea.layoutinspector.resource.UI_MODE_NIGHT_MASK
import com.android.tools.idea.layoutinspector.resource.UI_MODE_NIGHT_NO
import com.android.tools.idea.layoutinspector.resource.UI_MODE_NIGHT_YES
import com.android.tools.idea.layoutinspector.resource.UI_MODE_TYPE_APPLIANCE
import com.android.tools.idea.layoutinspector.resource.UI_MODE_TYPE_CAR
import com.android.tools.idea.layoutinspector.resource.UI_MODE_TYPE_DESK
import com.android.tools.idea.layoutinspector.resource.UI_MODE_TYPE_MASK
import com.android.tools.idea.layoutinspector.resource.UI_MODE_TYPE_NORMAL
import com.android.tools.idea.layoutinspector.resource.UI_MODE_TYPE_TELEVISION
import com.android.tools.idea.layoutinspector.resource.UI_MODE_TYPE_VR_HEADSET
import com.android.tools.idea.layoutinspector.resource.UI_MODE_TYPE_WATCH
import com.android.tools.idea.layoutinspector.resource.data.AppContext
import com.android.tools.idea.layoutinspector.resource.data.Locale
import com.android.tools.idea.layoutinspector.resource.data.Resource
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.FoldEvent.SpecialAngles.NO_FOLD_ANGLE_VALUE
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import java.awt.Polygon
import java.awt.Shape

fun LayoutInspectorViewProtocol.Screenshot.Type.toImageType(): AndroidWindow.ImageType {
  return when (this) {
    LayoutInspectorViewProtocol.Screenshot.Type.SKP -> AndroidWindow.ImageType.SKP_PENDING
    LayoutInspectorViewProtocol.Screenshot.Type.BITMAP -> AndroidWindow.ImageType.BITMAP_AS_REQUESTED
    else -> AndroidWindow.ImageType.UNKNOWN
  }
}

fun LayoutInspectorViewProtocol.Resource.convert(): Resource {
  return Resource(type, namespace, name)
}

fun LayoutInspectorViewProtocol.Locale.convert(): Locale {
  return Locale(language, country, variant, script)
}

fun LayoutInspectorViewProtocol.AppContext.convert(): AppContext {
  return AppContext(
    theme.convert(),
    screenWidth,
    screenHeight
  )
}

fun LayoutInspectorViewProtocol.Property.Type.convert(): PropertyType {
  return when (this) {
    LayoutInspectorViewProtocol.Property.Type.STRING -> PropertyType.STRING
    LayoutInspectorViewProtocol.Property.Type.BOOLEAN -> PropertyType.BOOLEAN
    LayoutInspectorViewProtocol.Property.Type.BYTE -> PropertyType.BYTE
    LayoutInspectorViewProtocol.Property.Type.CHAR -> PropertyType.CHAR
    LayoutInspectorViewProtocol.Property.Type.DOUBLE -> PropertyType.DOUBLE
    LayoutInspectorViewProtocol.Property.Type.FLOAT -> PropertyType.FLOAT
    LayoutInspectorViewProtocol.Property.Type.INT16 -> PropertyType.INT16
    LayoutInspectorViewProtocol.Property.Type.INT32 -> PropertyType.INT32
    LayoutInspectorViewProtocol.Property.Type.INT64 -> PropertyType.INT64
    LayoutInspectorViewProtocol.Property.Type.OBJECT -> PropertyType.OBJECT
    LayoutInspectorViewProtocol.Property.Type.COLOR -> PropertyType.COLOR
    LayoutInspectorViewProtocol.Property.Type.GRAVITY -> PropertyType.GRAVITY
    LayoutInspectorViewProtocol.Property.Type.INT_ENUM -> PropertyType.INT_ENUM
    LayoutInspectorViewProtocol.Property.Type.INT_FLAG -> PropertyType.INT_FLAG
    LayoutInspectorViewProtocol.Property.Type.RESOURCE -> PropertyType.RESOURCE
    LayoutInspectorViewProtocol.Property.Type.DRAWABLE -> PropertyType.DRAWABLE
    LayoutInspectorViewProtocol.Property.Type.ANIM -> PropertyType.ANIM
    LayoutInspectorViewProtocol.Property.Type.ANIMATOR -> PropertyType.ANIMATOR
    LayoutInspectorViewProtocol.Property.Type.INTERPOLATOR -> PropertyType.INTERPOLATOR
    LayoutInspectorViewProtocol.Property.Type.DIMENSION -> PropertyType.DIMENSION
    else -> error { "Unhandled property type $this" }
  }
}

fun LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint.toAttachErrorState() =
  when(this) {
    LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint.START_RECEIVED -> AttachErrorState.START_RECEIVED
    LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint.STARTED -> AttachErrorState.STARTED
    LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint.VIEW_INVALIDATION_CALLBACK -> AttachErrorState.VIEW_INVALIDATION_CALLBACK
    LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint.ROOTS_EVENT_SENT -> AttachErrorState.ROOTS_EVENT_SENT
    LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint.SCREENSHOT_CAPTURED -> AttachErrorState.SCREENSHOT_CAPTURED
    LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint.VIEW_HIERARCHY_CAPTURED -> AttachErrorState.VIEW_HIERARCHY_CAPTURED
    LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint.RESPONSE_SENT -> AttachErrorState.RESPONSE_SENT
    else -> AttachErrorState.UNKNOWN_ATTACH_ERROR_STATE
  }

fun LayoutInspectorViewProtocol.ErrorCode.toAttachErrorCode() =
  when (this) {
    LayoutInspectorViewProtocol.ErrorCode.NO_HARDWARE_ACCELERATION -> AttachErrorCode.NO_HARDWARE_ACCELERATION
    LayoutInspectorViewProtocol.ErrorCode.NO_ROOT_VIEWS_FOUND -> AttachErrorCode.NO_ROOT_VIEWS_FOUND
    else -> AttachErrorCode.UNKNOWN_VIEW_AGENT_ERROR
  }

fun LayoutInspectorViewProtocol.Quad.toShape(): Shape {
  return Polygon(intArrayOf(x0, x1, x2, x3), intArrayOf(y0, y1, y2, y3), 4)
}

/**
 * Create a [FolderConfiguration] based on a [LayoutInspectorViewProtocol.Configuration] proto received from a device.
 */
fun LayoutInspectorViewProtocol.Configuration.convert(apiLevel: Int): FolderConfiguration {
  val config = FolderConfiguration()
  config.countryCodeQualifier = CountryCodeQualifier(countryCode)
  config.networkCodeQualifier = NetworkCodeQualifier(networkCode)
  config.layoutDirectionQualifier = layoutDirectionFromRawValue(screenLayout)
  config.screenRatioQualifier = ScreenRatioQualifier(screenRatioFromRawValue(screenLayout))
  config.screenRoundQualifier = ScreenRoundQualifier(screenRoundFromRawValue(screenLayout))
  config.screenSizeQualifier = ScreenSizeQualifier(screenSizeFromRawValue(screenLayout))
  config.smallestScreenWidthQualifier = SmallestScreenWidthQualifier(smallestScreenWidthDp)
  config.screenWidthQualifier = ScreenWidthQualifier(screenWidthDp)
  config.screenHeightQualifier = ScreenHeightQualifier(screenHeightDp)
  config.wideColorGamutQualifier = wideColorGamutFromRawValue(colorMode)
  config.highDynamicRangeQualifier = highDynamicRangeFromRawValue(colorMode)
  config.screenOrientationQualifier = orientationFromRawValue(orientation)
  config.uiModeQualifier = uiModeFromRawValue(uiMode)
  config.nightModeQualifier = nightModeFromRawValue(uiMode)
  config.densityQualifier = Density.getEnum(density)?.let { DensityQualifier(it) }
  config.touchTypeQualifier = touchScreenFromRawValue(touchScreen)
  config.textInputMethodQualifier = keyboardFromRawValue(keyboard)
  config.keyboardStateQualifier = keyboardStateFromRawValue(keyboardHidden, hardKeyboardHidden)
  config.navigationMethodQualifier = navigationMethodFromRawValue(navigation)
  config.navigationStateQualifier = navigationStateFromRawValue(navigationHidden)
  config.versionQualifier = VersionQualifier(apiLevel)
  return config
}

fun LayoutInspectorViewProtocol.FoldEvent.convert(): InspectorModel.FoldInfo? {
  val angle = if (angle == NO_FOLD_ANGLE_VALUE) null else angle
  val orientation = orientation.convert()
  return orientation?.let { o -> InspectorModel.FoldInfo(angle, foldState.convert(), o) }
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

private fun LayoutInspectorViewProtocol.FoldEvent.FoldState.convert() = when (this) {
  LayoutInspectorViewProtocol.FoldEvent.FoldState.FLAT -> InspectorModel.Posture.FLAT
  LayoutInspectorViewProtocol.FoldEvent.FoldState.HALF_OPEN -> InspectorModel.Posture.HALF_OPEN
  else -> null
}

private fun LayoutInspectorViewProtocol.FoldEvent.FoldOrientation.convert() = when (this) {
  LayoutInspectorViewProtocol.FoldEvent.FoldOrientation.HORIZONTAL -> InspectorModel.FoldOrientation.HORIZONTAL
  LayoutInspectorViewProtocol.FoldEvent.FoldOrientation.VERTICAL -> InspectorModel.FoldOrientation.VERTICAL
  else -> null
}