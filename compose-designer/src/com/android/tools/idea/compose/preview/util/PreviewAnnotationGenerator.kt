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

import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_FQN
import com.android.tools.configurations.Configuration
import com.android.tools.preview.ComposePreviewElementInstance
import com.android.tools.preview.ConfigurablePreviewElement
import com.android.tools.preview.NO_DEVICE_SPEC
import com.android.tools.preview.UNDEFINED_API_LEVEL
import com.android.tools.preview.UNSET_UI_MODE_VALUE
import com.android.tools.preview.config.*
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_CHIN_SIZE_ZERO
import java.util.Locale as JavaUtilLocale

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
  builder.appendSeparator()
  builder.appendParamValue(Preview.DeviceSpec.PARAMETER_DPI, dpi.toString())
  builder.appendSeparator()
  builder.appendParamValue(Preview.DeviceSpec.PARAMETER_ORIENTATION, orientation)

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
      val colorNonNull = displaySettings.backgroundColor!!
      params.add(
        "$PARAMETER_BACKGROUND_COLOR = ${if (colorNonNull.startsWith("0x")) colorNonNull else "0x$colorNonNull"}"
      )
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
      params.add("$PARAMETER_UI_MODE = ${previewConfig.uiMode}")
    }
    if (displaySettings.showDecoration) {
      params.add("$PARAMETER_SHOW_SYSTEM_UI = true")
    }

    val targetDevice = configuration.device
    if (targetDevice != null && targetDevice.id != Configuration.CUSTOM_DEVICE_ID) {
      // If the current configuration's device is a known, non-custom device, use its ID.
      if (targetDevice.id != DEFAULT_DEVICE_ID) {
        // If device is default we can omit device parameter
        params.add("$PARAMETER_DEVICE = \"id:${targetDevice.id}\"")
      }
    } else {
      if (displaySettings.showDecoration) {
        val deviceSpec = createDeviceSpec(configuration)
        params.add("$PARAMETER_DEVICE = \"$deviceSpec\"")
      } else {
        if (
          previewConfig.deviceSpec != NO_DEVICE_SPEC &&
            previewConfig.deviceSpec != "Devices.DEFAULT"
        ) {
          // if original configuration had device non-default spec we should update it
          params.add("$PARAMETER_DEVICE = \"${previewConfig.deviceSpec}\"")
        }
        params.add("$PARAMETER_WIDTH_DP = $currentWidthDp")
        params.add("$PARAMETER_HEIGHT_DP = $currentHeightDp")
      }
    }

    params.joinTo(this, separator = ",\n    ", prefix = "    ")
    append("\n)")
  }
}
