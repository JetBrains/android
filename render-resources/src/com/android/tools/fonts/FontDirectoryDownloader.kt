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

/** Responsible for downloading the font directory. */
interface FontDirectoryDownloader {
  /** Downloads the fonts in the font directory if needed, executing [success] on success and [failure] otherwise. */
  fun refreshFonts(success: Runnable?, failure: Runnable?)

  companion object {
    @JvmField
    val NOOP_FONT_DIRECTORY_DOWNLOADER = object : FontDirectoryDownloader {
      override fun refreshFonts(success: Runnable?, failure: Runnable?) {}
    }
  }
}