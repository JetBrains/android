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

package com.android.tools.fonts

import com.android.ide.common.fonts.FontProvider
import java.net.URL

class Fonts {
  companion object {
    // List of available font families extracted from framework's fonts.xml
    // Used to provide completion for values of android:fontFamily attribute
    // https://android.googlesource.com/platform/frameworks/base/+/android-6.0.0_r5/data/fonts/fonts.xml
    @JvmField
    val AVAILABLE_FAMILIES: List<String> = listOf(
      "sans-serif", "sans-serif-thin", "sans-serif-light", "sans-serif-medium", "sans-serif-black",
      "sans-serif-condensed", "sans-serif-condensed-light", "sans-serif-condensed-medium",
      "serif", "monospace", "serif-monospace", "casual", "cursive", "sans-serif-smallcaps")

    @JvmStatic
    fun getFallbackResourceUrl(provider: FontProvider): URL {
      val filename = if (provider.equals(FontProvider.GOOGLE_PROVIDER)) "google_font_directory.xml" else "empty_font_directory.xml"
      return Fonts::class.java.classLoader.getResource("fonts/$filename")!!
    }
  }
}