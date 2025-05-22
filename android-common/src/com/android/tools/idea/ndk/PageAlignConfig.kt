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

import com.android.tools.idea.ndk.PageAlignConfig.Type.SO_UNALIGNED_IN_APK
import com.android.tools.idea.ndk.PageAlignConfig.Type.SO_UNALIGNED_LOAD_SEGMENTS
import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.PageAlign16kb
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.util.text.StringUtil
import java.io.File

/**
 * Configuration of 16 KB page alignment handling.
 */
object PageAlignConfig {
  enum class Type {
    SO_UNALIGNED_IN_APK,
    SO_UNALIGNED_LOAD_SEGMENTS
  }

  @VisibleForTesting
  val PROTO_TEMPLATE = PageAlign16kb.newBuilder().build()!!

  /**
   * Return true if the 16 KB page alignment feature is enabled by server flag.
   */
  fun isPageAlignMessageEnabled() = readServerFlag() != null

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
  private fun readServerFlag() =
    ServerFlagService.instance.getProtoOrNull<PageAlign16kb>("cxx/page_align_16kb", PROTO_TEMPLATE)

  /**
   * Create a warning message given a list of SO files that have alignment problems.
   */
  fun createMessage(apkFile: File, soFiles: List<String>, type: Type): String {
    if (soFiles.isEmpty()) error("Don't call create*Message() functions with empty SO-file list")
    val flag = readServerFlag() ?: error("Check isPageAlignMessageEnabled() before calling create*Message() functions")
    val date = flag.playStoreDeadlineDate
    val url = "<a href=\"https://${flag.messageUrl}\">${flag.messageUrl}</a>"
    val shortApk = StringUtil.shortenPathWithEllipsis(apkFile.path, 80)

    val prefix = when(type) {
      SO_UNALIGNED_IN_APK -> flag.soUnalignedInApkMessage
      SO_UNALIGNED_LOAD_SEGMENTS -> flag.unalignedLoadSegmentsMessage
    }
    val postscript = flag.messagePostscript
    return """
    |$prefix <ul>
    |  ${soFiles.sorted().joinToString(separator = "</li><li>", prefix = "<li>")}
    | </ul>
    |$postscript
    """.trimMargin()
      .replace("[APK]", shortApk)
      .replace("[DATE]", date)
      .replace("[URL]", url)
  }
}