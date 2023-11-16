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

import com.android.ide.common.fonts.FontFamily
import com.android.ide.common.fonts.FontLoader
import com.android.ide.common.fonts.FontProvider
import com.android.tools.fonts.FontDirectoryDownloader.Companion.NOOP_FONT_DIRECTORY_DOWNLOADER
import java.io.File

/** Manages network fonts downloading. */
interface FontDownloader {
  /** Asynchronously downloads [fontsToDownload], calling [success] if succeeded and [failure] otherwise. */
  fun download(fontsToDownload: List<FontFamily>, menuFontsOnly: Boolean, success: Runnable?, failure: Runnable?)

  /** Creates an instance of [FontDirectoryDownloader]. */
  fun createFontDirectoryDownloader(fontLoader: FontLoader, provider: FontProvider, fontCachePath: File): FontDirectoryDownloader

  companion object {
    @JvmField
    val NOOP_FONT_DOWNLOADER = object : FontDownloader {
      override fun download(fontsToDownload: List<FontFamily>, menuFontsOnly: Boolean, success: Runnable?, failure: Runnable?) { }

      override fun createFontDirectoryDownloader(
        fontLoader: FontLoader,
        provider: FontProvider,
        fontCachePath: File
      ): FontDirectoryDownloader = NOOP_FONT_DIRECTORY_DOWNLOADER
    }
  }
}