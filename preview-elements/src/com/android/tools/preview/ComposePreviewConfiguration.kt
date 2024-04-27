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
package com.android.tools.preview

import com.android.tools.preview.config.PARAMETER_API_LEVEL
import com.android.tools.preview.config.PARAMETER_DEVICE
import com.android.tools.preview.config.PARAMETER_FONT_SCALE
import com.android.tools.preview.config.PARAMETER_HEIGHT
import com.android.tools.preview.config.PARAMETER_HEIGHT_DP
import com.android.tools.preview.config.PARAMETER_LOCALE
import com.android.tools.preview.config.PARAMETER_THEME
import com.android.tools.preview.config.PARAMETER_UI_MODE
import com.android.tools.preview.config.PARAMETER_WALLPAPER
import com.android.tools.preview.config.PARAMETER_WIDTH
import com.android.tools.preview.config.PARAMETER_WIDTH_DP

/**
 * Reads the `@Preview` annotation parameters and returns a [PreviewConfiguration] containing the
 * values.
 */
fun attributesToConfiguration(
  attributesProvider: AnnotationAttributesProvider
): PreviewConfiguration {
  val apiLevel = attributesProvider.getIntAttribute(PARAMETER_API_LEVEL)
  val theme = attributesProvider.getStringAttribute(PARAMETER_THEME)
  // Both width and height have to support old ("width") and new ("widthDp") conventions
  val width =
    attributesProvider.getIntAttribute(PARAMETER_WIDTH)
    ?: attributesProvider.getIntAttribute(PARAMETER_WIDTH_DP)
  val height =
    attributesProvider.getIntAttribute(PARAMETER_HEIGHT)
    ?: attributesProvider.getIntAttribute(PARAMETER_HEIGHT_DP)
  val fontScale = attributesProvider.getFloatAttribute(PARAMETER_FONT_SCALE)
  val uiMode = attributesProvider.getIntAttribute(PARAMETER_UI_MODE)
  val device = attributesProvider.getStringAttribute(PARAMETER_DEVICE)
  val locale = attributesProvider.getStringAttribute(PARAMETER_LOCALE)
  val wallpaper = attributesProvider.getIntAttribute(PARAMETER_WALLPAPER)

  return PreviewConfiguration.cleanAndGet(
    apiLevel,
    theme,
    width,
    height,
    locale,
    fontScale,
    uiMode,
    device,
    wallpaper,
  )
}