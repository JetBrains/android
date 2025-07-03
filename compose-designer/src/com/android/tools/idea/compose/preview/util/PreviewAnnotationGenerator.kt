/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.compose.preview.util

import com.android.SdkConstants
import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_FQN
import com.android.tools.compose.COMPOSE_WALLPAPERS_CLASS_FQN
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.deviceSizeDp
import com.android.tools.idea.compose.pickers.preview.enumsupport.UiMode
import com.android.tools.idea.compose.pickers.preview.enumsupport.Wallpaper
import com.android.tools.idea.configurations.ReferenceDevice
import com.android.tools.preview.ComposePreviewElementInstance
import com.android.tools.preview.ConfigurablePreviewElement
import com.android.tools.preview.NO_DEVICE_SPEC
import com.android.tools.preview.UNDEFINED_API_LEVEL
import com.android.tools.preview.UNSET_UI_MODE_VALUE
import com.android.tools.preview.config.*
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_CHIN_SIZE_ZERO
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_DPI
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_ORIENTATION
import java.util.Locale as JavaUtilLocale

/**
 * A set of device IDs corresponding to `ReferenceDevice`s. These devices are for tooling and should
 * not be saved by their ID.
 */
private val referenceDeviceIds = ReferenceDevice.getWindowSizeDevices().map { it.id }.toSet()

// region uiModeToString conversion

// Constants mirroring those in android.content.res.Configuration for uiMode bitmasks
private const val UI_MODE_NIGHT_MASK = 0x30
private const val UI_MODE_TYPE_MASK = 0x0f

/** Enum representing the `UI_MODE_NIGHT_*` constants. */
private enum class NightMode(val classConstant: String, val resolvedValue: Int) {
  NIGHT_NO("UI_MODE_NIGHT_NO", 16),
  NIGHT_YES("UI_MODE_NIGHT_YES", 32);

  companion object {
    private val valueMap = entries.associateBy(NightMode::resolvedValue)

    fun fromInt(value: Int): NightMode? = valueMap[value]
  }
}

/**
 * Converts a `uiMode` integer value into a human-readable string of its constant parts by using the
 * [UiMode] and [NightMode] enums.
 */
private fun uiModeToString(uiMode: Int): String {
  if (uiMode == UNSET_UI_MODE_VALUE) return ""

  val night = uiMode and UI_MODE_NIGHT_MASK
  val type = uiMode and UI_MODE_TYPE_MASK

  // If there are other bits set besides night/type, we can't represent it cleanly.
  if ((uiMode and (night or type).inv()) != 0) {
    return uiMode.toString()
  }

  val nightString =
    if (night != 0) {
      NightMode.fromInt(night)?.classConstant?.let { "${SdkConstants.CLASS_CONFIGURATION}.$it" }
    } else null
  val typeString =
    if (type != 0) {
      UiMode.fromInt(type)?.classConstant?.let { "${SdkConstants.CLASS_CONFIGURATION}.$it" }
    } else null

  val parts = listOfNotNull(nightString, typeString)

  // Fallback to integer if we found parts of the bitmask that don't map to our enums.
  if ((night != 0 && nightString == null) || (type != 0 && typeString == null)) {
    return uiMode.toString()
  }

  return if (parts.isEmpty()) uiMode.toString() else parts.joinToString(" or ")
}

// endregion

// region wallpaperToString conversion

private fun wallpaperToString(wallpaperValue: Int): String {
  val wallpaper = Wallpaper.entries.find { it.resolvedValue == wallpaperValue.toString() }
  // We assume the FQN for Wallpapers will be imported, so we just need the enum class name.
  return if (wallpaper != null && wallpaper != Wallpaper.NONE) {
    "$COMPOSE_WALLPAPERS_CLASS_FQN.${wallpaper.classConstant}"
  } else {
    wallpaperValue.toString() // Fallback for unknown values
  }
}

// endregion

/** Appends a parameter-value pair to the StringBuilder. */
private fun StringBuilder.appendParamValue(parameterName: String, value: String): StringBuilder =
  append("$parameterName=${value}")

/** Appends a separator to the StringBuilder. */
private fun StringBuilder.appendSeparator(): StringBuilder = append(Preview.DeviceSpec.SEPARATOR)

/** Creates a device spec string for the @Preview annotation based on the current configuration. */
internal fun createDeviceSpec(configuration: Configuration): String {
  // device is guaranteed to be non-null in this context as a preview is being rendered.
  val device = configuration.device!!
  val deviceState = configuration.deviceState!!
  val orientation = deviceState.orientation.name.lowercase(JavaUtilLocale.getDefault())
  val screen = deviceState.hardware.screen
  val dpi = screen.pixelDensity.dpiValue

  val (widthDp, heightDp) = configuration.deviceSizeDp()

  val builder = StringBuilder(Preview.DeviceSpec.PREFIX)

  builder.appendParamValue(Preview.DeviceSpec.PARAMETER_WIDTH, "${widthDp}dp")
  builder.appendSeparator()
  builder.appendParamValue(Preview.DeviceSpec.PARAMETER_HEIGHT, "${heightDp}dp")
  if (dpi != DEFAULT_DPI) {
    builder.appendSeparator()
    builder.appendParamValue(Preview.DeviceSpec.PARAMETER_DPI, dpi.toString())
  }
  if (orientation != DEFAULT_ORIENTATION.name) {
    builder.appendSeparator()
    builder.appendParamValue(Preview.DeviceSpec.PARAMETER_ORIENTATION, orientation)
  }

  val currentDeviceConfig = device.toDeviceConfig()

  if (currentDeviceConfig.isRound) {
    builder.appendSeparator()
    builder.appendParamValue(Preview.DeviceSpec.PARAMETER_IS_ROUND, "true")
    if (currentDeviceConfig.chinSize != DEFAULT_CHIN_SIZE_ZERO.toFloat()) {
      builder.appendSeparator()
      builder.appendParamValue(
        Preview.DeviceSpec.PARAMETER_CHIN_SIZE,
        "${currentDeviceConfig.chinSize.toInt()}dp",
      )
    }
  }
  if (currentDeviceConfig.cutout != Cutout.none) {
    builder.appendSeparator()
    builder.appendParamValue(Preview.DeviceSpec.PARAMETER_CUTOUT, currentDeviceConfig.cutout.name)
  }
  if (currentDeviceConfig.navigation != Navigation.gesture) {
    builder.appendSeparator()
    builder.appendParamValue(
      Preview.DeviceSpec.PARAMETER_NAVIGATION,
      currentDeviceConfig.navigation.name,
    )
  }

  return builder.toString()
}

/**
 * Converts the given [ConfigurablePreviewElement]'s display settings and configuration into a
 * `@Preview` annotation string, incorporating the current configuration's dimensions and a new
 * [name].
 *
 * This function always returns the @Preview annotation with its Fully Qualified Name (i.e.,
 * `@androidx.compose.ui.tooling.preview.Preview(...)`). Callers should be aware that they might
 * need to use facilities like `ShortenReferencesFacility` or string manipulation if they intend to
 * use a shorter, unqualified name for the annotation.
 *
 * Parameters are only added if their value is different from their default.
 *
 * @param previewElement The [ComposePreviewElementInstance] for which to generate the annotation.
 * @param configuration The [Configuration] containing device and display settings.
 * @param name The desired name for the new preview.
 */
internal fun toPreviewAnnotationText(
  previewElement: ComposePreviewElementInstance<*>,
  configuration: Configuration,
  name: String,
): String {
  val displaySettings = previewElement.displaySettings
  val previewConfig = previewElement.configuration

  val (currentWidthDp, currentHeightDp) = configuration.deviceSizeDp()

  return buildString {
    append("@$COMPOSE_PREVIEW_ANNOTATION_FQN(\n")

    val params = mutableListOf<String>()

    params.add("$PARAMETER_NAME = \"$name\"")
    if (!displaySettings.group.isNullOrBlank()) {
      params.add("$PARAMETER_GROUP = \"${displaySettings.group}\"")
    }

    if (displaySettings.showBackground) {
      params.add("$PARAMETER_SHOW_BACKGROUND = true")
    }
    if (!displaySettings.backgroundColor.isNullOrBlank()) {
      var colorValue = displaySettings.backgroundColor!!
      if (colorValue.startsWith("#")) {
        colorValue = colorValue.substring(1)
      } else if (colorValue.startsWith("0x", ignoreCase = true)) {
        colorValue = colorValue.substring(2)
      }
      // Ensure the hex value is uppercase to match test expectations and improve consistency.
      params.add("$PARAMETER_BACKGROUND_COLOR = 0x${colorValue.uppercase()}")
    }

    if (previewConfig.apiLevel != UNDEFINED_API_LEVEL) {
      params.add("$PARAMETER_API_LEVEL = ${previewConfig.apiLevel}")
    }
    if (previewConfig.locale.isNotBlank()) {
      params.add("$PARAMETER_LOCALE = \"${previewConfig.locale}\"")
    }
    if (previewConfig.fontScale != 1.0f) {
      params.add("$PARAMETER_FONT_SCALE = ${previewConfig.fontScale}f")
    }
    if (previewConfig.uiMode != UNSET_UI_MODE_VALUE) {
      params.add("$PARAMETER_UI_MODE = ${uiModeToString(previewConfig.uiMode)}")
    }
    if (previewConfig.wallpaper.toString() != Wallpaper.NONE.resolvedValue) {
      params.add("$PARAMETER_WALLPAPER = ${wallpaperToString(previewConfig.wallpaper)}")
    }
    if (displaySettings.showDecoration) {
      params.add("$PARAMETER_SHOW_SYSTEM_UI = true")
    }

    val targetDevice = configuration.device
    val isReferenceDevice = targetDevice != null && referenceDeviceIds.contains(targetDevice.id)

    if (
      targetDevice != null &&
        targetDevice.id != Configuration.CUSTOM_DEVICE_ID &&
        !isReferenceDevice
    ) {
      // If the current configuration's device is a known, non-custom device and non-reference
      // device, use its ID.
      if (targetDevice.id != DEFAULT_DEVICE_ID) {
        // If device is default we can omit device parameter
        params.add("$PARAMETER_DEVICE = \"id:${targetDevice.id}\"")
      }
    } else {
      val deviceSpec = createDeviceSpec(configuration)

      if (displaySettings.showDecoration || isReferenceDevice) {
        params.add("$PARAMETER_DEVICE = \"$deviceSpec\"")
      } else {
        if (
          previewConfig.deviceSpec != NO_DEVICE_SPEC &&
            previewConfig.deviceSpec != "Devices.DEFAULT"
        ) {
          // if original configuration had device non-default spec we should update it
          params.add("$PARAMETER_DEVICE = \"$deviceSpec\"")
        }
        params.add("$PARAMETER_WIDTH_DP = $currentWidthDp")
        params.add("$PARAMETER_HEIGHT_DP = $currentHeightDp")
      }
    }

    params.joinTo(this, separator = ",\n    ", prefix = "    ")
    append("\n)")
  }
}
