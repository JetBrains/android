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
import com.android.tools.idea.testing.registerServiceInstance
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.TextFormat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import org.junit.Rule
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import java.io.File

class PageAlignConfigTest {
  @get:Rule val appRule = ApplicationRule()
  @get:Rule val disposableRule = DisposableRule()


  @Test
  fun `check server flag off by default`() {
    val service = Mockito.mock(ServerFlagService::class.java)
    whenever(service.getProtoOrNull<PageAlign16kb>("cxx/page_align_16kb", PageAlignConfig.PROTO_TEMPLATE)).thenReturn(null)
    ApplicationManager.getApplication()
      .registerServiceInstance(ServerFlagService::class.java, service, disposableRule.disposable)

    assertThat(PageAlignConfig.isPageAlignMessageEnabled()).isFalse()
  }

  @Test
  fun `check server flag enabled`() {
    // This is the currently planned server flag.
    val textProto = """
      play_store_deadline_date: "November 2026"
      message_url: "developer.android.com/16kb-page-size"
      so_unaligned_in_apk_message: "The following native libraries are not aligned at 16 KB boundary inside [APK]:"
      unaligned_load_segments_message: "The following native libraries have segments that are not aligned at 16 KB boundary inside [APK]:"
      message_postscript: "Beginning [DATE] the Google Play Store requires that all apps must be 16 KB compatible. For more information, visit [URL]."
    """.trimIndent()
    val builder = PageAlign16kb.newBuilder()
    TextFormat.getParser().merge(textProto, builder)
    val service = Mockito.mock(ServerFlagService::class.java)
    whenever(service.getProtoOrNull<PageAlign16kb>("cxx/page_align_16kb", PageAlignConfig.PROTO_TEMPLATE))
      .thenReturn(builder.build())
    ApplicationManager.getApplication()
      .registerServiceInstance(ServerFlagService::class.java, service, disposableRule.disposable)

    assertThat(PageAlignConfig.isPageAlignMessageEnabled()).isTrue()
    val message = PageAlignConfig.createSoNotAlignedInZipMessage(
      File("example.apk"),
      listOf("example.so"))
    assertThat(message).contains("November 2026")
    assertThat(message).contains("example.apk")
    assertThat(message).contains("example.so")
  }
}