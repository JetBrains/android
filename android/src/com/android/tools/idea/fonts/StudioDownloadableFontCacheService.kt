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
package com.android.tools.idea.fonts

import com.android.ide.common.fonts.FontFamily
import com.android.ide.common.fonts.FontLoader
import com.android.ide.common.fonts.FontProvider
import com.android.tools.fonts.DownloadableFontCacheService
import com.android.tools.fonts.DownloadableFontCacheServiceImpl
import com.android.tools.fonts.FontDirectoryDownloader
import com.android.tools.fonts.FontDownloader
import com.android.tools.idea.sdk.AndroidSdks
import com.intellij.openapi.application.ApplicationManager
import java.io.File

/** Studio specific version of [DownloadableFontCacheService]. */
class StudioDownloadableFontCacheService : DownloadableFontCacheServiceImpl(
  object : FontDownloader {
    override fun download(fontsToDownload: List<FontFamily>, menuFontsOnly: Boolean, success: Runnable?, failure: Runnable?) =
      FontDownloadService.download(fontsToDownload, menuFontsOnly, success, failure)

    override fun createFontDirectoryDownloader(fontLoader: FontLoader,
                                               provider: FontProvider,
                                               fontCachePath: File): FontDirectoryDownloader =
      FontDirectoryDownloadService(fontLoader, provider, fontCachePath)
  },
  { AndroidSdks.getInstance().tryToChooseSdkHandler().location?.toFile() }
) {
  companion object {
    @JvmStatic
    fun getInstance(): DownloadableFontCacheServiceImpl =
      ApplicationManager.getApplication().getService(DownloadableFontCacheService::class.java) as DownloadableFontCacheServiceImpl
  }
}