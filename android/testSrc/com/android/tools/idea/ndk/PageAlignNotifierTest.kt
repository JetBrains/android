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

import com.android.ide.common.pagealign.PageAlignUtilsTest.ZipBuilder
import com.android.ide.common.pagealign.PageAlignUtilsTest.ZipBuilder.ZipEntryOptions
import com.android.ide.common.pagealign.PageAlignUtilsTest.ZipBuilder.ZipEntryOptions.*
import com.android.ide.common.pagealign.SO_FILE_16K_ALIGNED
import com.android.ide.common.pagealign.SO_FILE_NOT_16K_ALIGNED
import com.android.tools.idea.run.ApkFileUnit
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.PageAlign16kb
import com.android.tools.idea.testing.registerServiceInstance
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.Align16kbEvent.AlignNative16kbEventType
import com.google.wireless.android.sdk.stats.Align16kbEvent.AlignNative16kbEventType.ALIGN_NATIVE_BUBBLE_LOAD_SECTIONS_DEPLOYED
import com.google.wireless.android.sdk.stats.Align16kbEvent.AlignNative16kbEventType.ALIGN_NATIVE_BUBBLE_ZIP_OFFSET_DEPLOYED
import com.google.wireless.android.sdk.stats.Align16kbEvent.AlignNative16kbEventType.ALIGN_NATIVE_COMPLIANT_APP_DEPLOYED
import com.google.wireless.android.sdk.stats.Align16kbEvent.AlignNative16kbEventType.ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.mockito.kotlin.whenever

class PageAlignNotifierTest {
  @get:Rule val temporaryFolder = TemporaryFolder()
  @get:Rule val appRule = ApplicationRule()
  @get:Rule val disposableRule = DisposableRule()

  @Test
  fun `multiple APKs are reported`() {
    testNotifier(
      serverFlag = true,
      productCpuAbiList = "arm64-v8a",
      buildCharacteristics = null,
      Apk(SO_FILE_NOT_16K_ALIGNED to AlignedUncompressed),
      Apk(SO_FILE_16K_ALIGNED to UnalignedUncompressed))
      .assertHasBalloonCount(2)
      .assertHasLoadSegmentBalloon()
      .assertHasUnalignedInZipBalloon()
      .assertHasEventCount(4)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
      .assertHasEvent(ALIGN_NATIVE_BUBBLE_LOAD_SECTIONS_DEPLOYED)
  }

  @Test
  fun `APK with no so files`() {
    testNotifier(
      serverFlag = true,
      productCpuAbiList = "arm64-v8a",
      buildCharacteristics = null,
      Apk())
      .assertHasBalloonCount(0)
      .assertHasEventCount(0)
  }

  @Test
  fun `16 KB compliant so with server flag off`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "arm64-v8a")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_COMPLIANT_APP_DEPLOYED, productCpuAbilist = "arm64-v8a")
  }

  @Test
  fun `16 KB non-compliant so with server flag off`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "arm64-v8a")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant so with server flag on`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "arm64-v8a")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB non-compliant so with server flag on`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "arm64-v8a")
      .assertHasBalloonCount(1)
      .assertHasLoadSegmentBalloon()
      .assertHasEventCount(2)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
      .assertHasEvent(ALIGN_NATIVE_BUBBLE_LOAD_SECTIONS_DEPLOYED)
  }

  @Test
  fun `16 KB compliant so unaligned in APK with server flag off`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "arm64-v8a")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB non-compliant so unaligned in APK with server flag off`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "arm64-v8a")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant so unaligned in APK with server flag on`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "arm64-v8a")
      .assertHasBalloonCount(1)
      .assertHasUnalignedInZipBalloon()
      .assertHasEventCount(2)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
      .assertHasEvent(ALIGN_NATIVE_BUBBLE_ZIP_OFFSET_DEPLOYED)
  }

  @Test
  fun `16 KB non-compliant so unaligned in APK with server flag on`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "arm64-v8a")
      .assertHasBalloonCount(2)
      .assertHasLoadSegmentBalloon()
      .assertHasUnalignedInZipBalloon()
      .assertHasEventCount(3)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
      .assertHasEvent(ALIGN_NATIVE_BUBBLE_ZIP_OFFSET_DEPLOYED)
      .assertHasEvent(ALIGN_NATIVE_BUBBLE_LOAD_SECTIONS_DEPLOYED)
  }

  @Test
  fun `16 KB compliant x86_64 so with server flag off`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "x86_64")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_COMPLIANT_APP_DEPLOYED, productCpuAbilist = "x86_64")
  }

  @Test
  fun `16 KB non-compliant x86_64 so with server flag off`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "x86_64")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant x86_64 so with server flag on`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "x86_64")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB non-compliant x86_64 so with server flag on`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "x86_64")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant x86_64 so unaligned in APK with server flag off`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "x86_64")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB non-compliant x86_64 so unaligned in APK with server flag off`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "x86_64")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant x86_64 so unaligned in APK with server flag on`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "x86_64")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB non-compliant x86_64 so unaligned in APK with server flag on`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "x86_64")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  // =======================================================================================================
  @Test
  fun `16 KB compliant so with server flag off on Wear OS`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "arm64-v8a",
      buildCharacteristics = "watch")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_COMPLIANT_APP_DEPLOYED, productCpuAbilist = "arm64-v8a")
  }

  @Test
  fun `16 KB non-compliant so with server flag off on Wear OS`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "arm64-v8a",
      buildCharacteristics = "watch")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant so with server flag on on Wear OS`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "arm64-v8a",
      buildCharacteristics = "watch")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB non-compliant so with server flag on on Wear OS`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "arm64-v8a",
      buildCharacteristics = "watch")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant so unaligned in APK with server flag off on Wear OS`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "arm64-v8a",
      buildCharacteristics = "watch")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB non-compliant so unaligned in APK with server flag off on Wear OS`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "arm64-v8a",
      buildCharacteristics = "watch")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant so unaligned in APK with server flag on on Wear OS`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "arm64-v8a",
      buildCharacteristics = "watch")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB non-compliant so unaligned in APK with server flag on on Wear OS`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "arm64-v8a",
      buildCharacteristics = "watch")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant x86_64 so with server flag off on Wear OS`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "x86_64",
      buildCharacteristics = "watch")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_COMPLIANT_APP_DEPLOYED, productCpuAbilist = "x86_64")
  }

  @Test
  fun `16 KB non-compliant x86_64 so with server flag off on Wear OS`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "x86_64",
      buildCharacteristics = "watch")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant x86_64 so with server flag on on Wear OS`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "x86_64",
      buildCharacteristics = "watch")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB non-compliant x86_64 so with server flag on on Wear OS`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "x86_64",
      buildCharacteristics = "watch")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant x86_64 so unaligned in APK with server flag off on Wear OS`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "x86_64",
      buildCharacteristics = "watch")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB non-compliant x86_64 so unaligned in APK with server flag off on Wear OS`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "x86_64",
      buildCharacteristics = "watch")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant x86_64 so unaligned in APK with server flag on on Wear OS`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "x86_64",
      buildCharacteristics = "watch")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB non-compliant x86_64 so unaligned in APK with server flag on on Wear OS`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "x86_64",
      buildCharacteristics = "watch")
      .assertHasBalloonCount(0)
      .assertHasEventCount(1)
      .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  // =======================================================================================================
  @Test
  fun `16 KB compliant so with server flag off on Android Auto`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "arm64-v8a",
      buildCharacteristics = "automotive")
        .assertHasBalloonCount(0)
        .assertHasEventCount(1)
        .assertHasEvent(ALIGN_NATIVE_COMPLIANT_APP_DEPLOYED, productCpuAbilist = "arm64-v8a")
  }

  @Test
  fun `16 KB non-compliant so with server flag off on Android Auto`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "arm64-v8a",
      buildCharacteristics = "automotive")
        .assertHasBalloonCount(0)
        .assertHasEventCount(1)
        .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant so with server flag on on Android Auto`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "arm64-v8a",
      buildCharacteristics = "automotive")
        .assertHasBalloonCount(0)
        .assertHasEventCount(1)
        .assertHasEvent(ALIGN_NATIVE_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB non-compliant so with server flag on on Android Auto`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "arm64-v8a",
      buildCharacteristics = "automotive")
        .assertHasBalloonCount(0)
        .assertHasEventCount(1)
        .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant so unaligned in APK with server flag off on Android Auto`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "arm64-v8a",
      buildCharacteristics = "automotive")
        .assertHasBalloonCount(0)
        .assertHasEventCount(1)
        .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB non-compliant so unaligned in APK with server flag off on Android Auto`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "arm64-v8a",
      buildCharacteristics = "automotive")
        .assertHasBalloonCount(0)
        .assertHasEventCount(1)
        .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant so unaligned in APK with server flag on on Android Auto`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "arm64-v8a",
      buildCharacteristics = "automotive")
        .assertHasBalloonCount(0)
        .assertHasEventCount(1)
        .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB non-compliant so unaligned in APK with server flag on on Android Auto`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "arm64-v8a",
      buildCharacteristics = "automotive")
        .assertHasBalloonCount(0)
        .assertHasEventCount(1)
        .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant x86_64 so with server flag off on Android Auto`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "x86_64",
      buildCharacteristics = "automotive")
        .assertHasBalloonCount(0)
        .assertHasEventCount(1)
        .assertHasEvent(ALIGN_NATIVE_COMPLIANT_APP_DEPLOYED, productCpuAbilist = "x86_64")
  }

  @Test
  fun `16 KB non-compliant x86_64 so with server flag off on Android Auto`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "x86_64",
      buildCharacteristics = "automotive")
        .assertHasBalloonCount(0)
        .assertHasEventCount(1)
        .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant x86_64 so with server flag on on Android Auto`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "x86_64",
      buildCharacteristics = "automotive")
        .assertHasBalloonCount(0)
        .assertHasEventCount(1)
        .assertHasEvent(ALIGN_NATIVE_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB non-compliant x86_64 so with server flag on on Android Auto`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = AlignedUncompressed,
      productCpuAbiList = "x86_64",
      buildCharacteristics = "automotive")
        .assertHasBalloonCount(0)
        .assertHasEventCount(1)
        .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant x86_64 so unaligned in APK with server flag off on Android Auto`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "x86_64",
      buildCharacteristics = "automotive")
        .assertHasBalloonCount(0)
        .assertHasEventCount(1)
        .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB non-compliant x86_64 so unaligned in APK with server flag off on Android Auto`() {
    testNotifier(
      serverFlag = false,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "x86_64",
      buildCharacteristics = "automotive")
        .assertHasBalloonCount(0)
        .assertHasEventCount(1)
        .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB compliant x86_64 so unaligned in APK with server flag on on Android Auto`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "x86_64",
      buildCharacteristics = "automotive")
        .assertHasBalloonCount(0)
        .assertHasEventCount(1)
        .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  @Test
  fun `16 KB non-compliant x86_64 so unaligned in APK with server flag on on Android Auto`() {
    testNotifier(
      serverFlag = true,
      so = SO_FILE_NOT_16K_ALIGNED,
      zipLayout = UnalignedUncompressed,
      productCpuAbiList = "x86_64",
      buildCharacteristics = "automotive")
        .assertHasBalloonCount(0)
        .assertHasEventCount(1)
        .assertHasEvent(ALIGN_NATIVE_NON_COMPLIANT_APP_DEPLOYED)
  }

  class Apk(
    vararg val sos : Pair<ByteArray, ZipEntryOptions>
  )

  fun testNotifier(
    serverFlag : Boolean,
    productCpuAbiList : String,
    buildCharacteristics : String? = null,
    vararg apks : Apk)  : TestablePageAlignNotifier {

    val apkFiles = apks.mapIndexed { apkIndex, apk ->
      val builder = ZipBuilder()
      apk.sos.forEachIndexed { index, (so, zipLayout) ->
        builder.addFile("elf-${index+1}.so", so, zipLayout)
      }
      ApkFileUnit("module.name-${apkIndex+1}", builder.toByteArray().toFile())
    }
    val apkInfo = ApkInfo(apkFiles, "application.id")

    if (serverFlag) {
      val config = PageAlign16kb.newBuilder()
        .setMessageUrl("test.url")
        .setPlayStoreDeadlineDate("test.deadline")
        .build()
      val service = Mockito.mock(ServerFlagService::class.java)
      whenever(service.getProtoOrNull<PageAlign16kb>("cxx/page_align_16kb", PageAlignConfig.PROTO_TEMPLATE)).thenReturn(config)
      ApplicationManager.getApplication()
        .registerServiceInstance(ServerFlagService::class.java, service, disposableRule.disposable)
    }
    val notifier = TestablePageAlignNotifier()
    notifier.notify16kbAlignmentViolations(apkInfo, productCpuAbiList.split(","), buildCharacteristics)
    return notifier
  }

  fun testNotifier(
    so : ByteArray,
    serverFlag : Boolean,
    zipLayout : ZipEntryOptions,
    productCpuAbiList : String,
    buildCharacteristics : String? = null
  ) : TestablePageAlignNotifier {
    return testNotifier(
      serverFlag = serverFlag,
      productCpuAbiList = productCpuAbiList,
      buildCharacteristics = buildCharacteristics,
      Apk(so to zipLayout)
    )
  }

  fun ByteArray.toFile() : File {
    val file = temporaryFolder.newFolder().resolve("test.apk")
    file.writeBytes(this)
    return file
  }

  class TestablePageAlignNotifier() : PageAlignNotifier() {
    val events = mutableListOf<AndroidStudioEvent>()
    val balloons = mutableListOf<String>()

    override fun showBalloon(text: String) { balloons.add(text) }
    override fun logUsage(event: AndroidStudioEvent.Builder) { events.add(event.build()) }

    fun assertHasBalloonCount(count : Int) : TestablePageAlignNotifier {
      assertThat(balloons).hasSize(count)
      return this
    }

    fun assertHasLoadSegmentBalloon() : TestablePageAlignNotifier {
      assertThat(balloons.any { it.contains("native libraries have segments that are not aligned at 16 KB boundary")})
        .isTrue()
      return this
    }

    fun assertHasUnalignedInZipBalloon() : TestablePageAlignNotifier {
      assertThat(balloons.any { it.contains("native libraries are not aligned at 16 KB boundary")})
        .isTrue()
      return this
    }

    fun assertHasEventCount(count : Int) : TestablePageAlignNotifier {
      assertThat(events).hasSize(count)
      return this
    }

    fun assertHasEvent(
      type : AlignNative16kbEventType,
      productCpuAbilist : String = "ignore") : TestablePageAlignNotifier {
      assertThat(events).isNotEmpty()
      var matched = false
      for(event in events) {
        assertThat(event.kind == AndroidStudioEvent.EventKind.ALIGN16KB_EVENT).isTrue()
        val alignEvent = event.align16KbEvent
        if (alignEvent.type != type) continue
        if (productCpuAbilist != "ignore" && alignEvent.productCpuAbilist != productCpuAbilist) continue
        matched = true
      }
      assertThat(matched)
        .named(events.joinToString("\n"))
        .isTrue()
      return this
    }
  }
}