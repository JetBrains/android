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

import com.android.testutils.waitForCondition
import com.android.tools.adtui.stdui.ResizableImage
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.LightPlatformTestCase
import kotlin.time.Duration.Companion.seconds
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import javax.swing.JLabel

private val TIMEOUT = 5.seconds

@RunWith(JUnit4::class)
class IntellijImageDataViewerTest : LightPlatformTestCase() {
  @get:Rule val disposableRule = DisposableRule()

  @Test
  fun testCanCreateImageViewer() {
    val viewer =
      IntellijImageDataViewer(
        IntellijImageDataViewerTest::class.java.getResource("/image.png")!!.readBytes(),
        disposableRule.disposable
      )

    waitForCondition(TIMEOUT) { viewer.component.components.isNotEmpty() }
    assertThat(viewer.component.components.map { it::class.java }).containsExactly(ResizableImage::class.java)
  }

  @Test
  fun testInvalidImageViewerReturnsInvalid() {
    val viewer =  IntellijImageDataViewer("invalid".toByteArray(), disposableRule.disposable)

    waitForCondition(TIMEOUT) { viewer.component.components.isNotEmpty() }
    assertThat(viewer.component.components.map { it::class.java }).containsExactly(JLabel::class.java)
  }
}
