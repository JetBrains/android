/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.adtui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import com.intellij.ui.JBColor
import com.intellij.ui.JBColor.marker
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * Gets a color from the current Swing theme, looking it up by name. It then remembers it, keying it
 * on the current [theme name][JewelTheme.name] and [isDark][JewelTheme.isDark] values.
 *
 * If there is no corresponding key in the theme, it looks up a fallback key (which is generally a
 * `ColorPalette.*` entry), and if that is also missing, it falls back to the hardcoded defaults.
 */
@Composable
fun rememberColor(
  key: String,
  darkFallbackKey: String?,
  darkDefault: Color,
  lightFallbackKey: String?,
  lightDefault: Color,
): Color {
  val isDark = JewelTheme.isDark

  return remember(JewelTheme.name, isDark) {
    if (isDark) {
      retrieveColor(key, darkFallbackKey, darkDefault)
    } else {
      retrieveColor(key, lightFallbackKey, lightDefault)
    }
  }
}

/**
 * Gets a color from the current Swing theme, looking it up by name.
 *
 * If there is no corresponding key in the theme, it looks up a fallback key (which is generally a
 * `ColorPalette.*` entry), and if that is also missing, it falls back to the hardcoded default.
 */
private fun retrieveColor(key: String, fallbackKey: String?, default: Color) =
retrieveColorStrictOrUnspecified(key).takeOrElse {
  if (fallbackKey != null) {
    val fallbackColor = retrieveColorStrictOrUnspecified(fallbackKey)
    if (fallbackColor.isSpecified) return@takeOrElse fallbackColor
  }

  default
}

// Differs from Jewel's retrieveColorOrUnspecified in that it uses
// JBColor.get instead of JBColor.namedColor. The former does not
// fall victim to the theming's system penchant to use the wildcard
// attributes to do partial matching, when it should really just say
// "the key is not defined". This is only needed for this case since
// these keys are controlled by us and normally not defined in the
// theme, so we definitely do not want the "*" values.
private fun retrieveColorStrictOrUnspecified(key: String): Color {
  val color =
    try {
      JBColor.get(key, marker("JEWEL_JBCOLOR_MARKER")).toComposeColor()
    } catch (_: AssertionError) {
      // JBColor.marker will throw AssertionError on getRGB/any other color
      // for now there is no way to handle non-existing key.
      // The way should be introduced in platform
      null
    }
  return color ?: Color.Unspecified
}

