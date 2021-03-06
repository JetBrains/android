/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.inspectors.common.ui.dataviewer

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.LightPlatformTestCase

class IntellijImageDataViewerTest : LightPlatformTestCase() {
  fun testCanCreateImageViewer() {
    val viewer = IntellijImageDataViewer.createImageViewer(
      IntellijImageDataViewerTest::class.java.getResource("/image.png").readBytes())!!

    assertThat(viewer.style).isEqualTo(DataViewer.Style.RAW)
    assertThat(viewer.image.width).isGreaterThan(0)
    assertThat(viewer.image.height).isGreaterThan(0)
    assertThat(viewer.component.preferredSize.height).isGreaterThan(0)
  }

  fun testInvalidImageViewerReturnsNull() {
    val invalidViewer = IntellijImageDataViewer.createImageViewer("invalid".toByteArray())

    assertThat(invalidViewer).isNull()
  }
}