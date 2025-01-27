/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.ndk

import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.PageAlign16kb
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.util.text.StringUtil
import java.io.File

/**
 * Configuration of 16 KB page alignment handling.
 */
object PageAlignConfig {
  // TODO(382091362) these warning messages follow the form we used to report Play Store rejection of APKs that didn't have 64-bit
  // implementations of SO files. It should still be reviewed before it's considered finalized.
  private const val SO_UNALIGNED_IN_APK = "The following native libraries are not aligned at 16 KB boundary inside [APK]:"
  private const val SO_UNALIGNED_LOAD_SEGMENTS = "The following native libraries have segments that are not aligned at 16 KB boundary inside [APK]:"
  private const val MESSAGE_POSTSCRIPT = "Beginning [DATE] the Google Play Store requires that all apps must be 16 KB compatible. For more information, visit [URL]."
  private var config : PageAlign16kb? = PageAlign16kb.newBuilder()
    .setMessageUrl("URL PLACEHOLDER")
    .setPlayStoreDeadlineDate("DEADLINE PLACEHOLDER")
    .build()

  /**
   * Whether the server config proto has been read yet.
   * You can manually set this to true to debug the feature. You'll get a test failure if you try to check it in that way.
   */
  @VisibleForTesting
  const val CONFIG_INITIALIZED_DEFAULT = false

  private var configInitialized = CONFIG_INITIALIZED_DEFAULT


  /**
   * Return true if the 16 KB page alignment feature is enabled by server flag.
   */
  fun isPageAlignMessageEnabled() = getServerFlagOrNull() != null

  /**
   * Create the text body of a message to alert the user that their APK has some SO files that aren't aligned at a 16 KB boundary within
   * the APK.
   */
  fun createSoNotAlignedInZipMessage(apkFile: File, files: List<String>) = createMessage(apkFile, files, SO_UNALIGNED_IN_APK)

  /**
   * Create the text body of a message to alert the user that the APK has some SO files that have LOAD segments that aren't aligned on 16 KB boundaries.
   */
  fun createSoUnalignedLoadSegmentsMessage(apkFile: File, files: List<String>) = createMessage(apkFile, files, SO_UNALIGNED_LOAD_SEGMENTS)

  /**
   * Helper to read the server flag. Will return null if 16 KB alignment feature is not enabled on this build.
   */
  private fun getServerFlagOrNull() : PageAlign16kb? {
    if (configInitialized) return config
    configInitialized = true
    val defaultConfig = PageAlign16kb.getDefaultInstance()
    val service = ServerFlagService.instance
    config = service.getProtoOrNull<PageAlign16kb>("cxx/page_align_16kb", defaultConfig)
    return config
  }

  /**
   * Create a warning message given a list of SO files that have alignment problems.
   */
  private fun createMessage(apkFile: File, soFiles: List<String>, prefix : String): String {
    if (soFiles.isEmpty()) error("Don't call create*Message() functions with empty SO-file list")
    val flag = getServerFlagOrNull() ?: error("Check isPageAlignMessageEnabled() before calling create*Message() functions")
    val date = flag.playStoreDeadlineDate
    val url = "<a href=\"https://${flag.messageUrl}\">${flag.messageUrl}</a>"
    val shortApk = StringUtil.shortenPathWithEllipsis(apkFile.path, 80)
    return """
    |$prefix <ul>
    |  ${soFiles.sorted().joinToString(separator = "</li><li>", prefix = "<li>")}
    | </ul>
    |$MESSAGE_POSTSCRIPT
    """.trimMargin()
      .replace("[APK]", shortApk)
      .replace("[DATE]", date)
      .replace("[URL]", url)
  }
}