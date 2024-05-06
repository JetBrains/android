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
package com.android.tools.idea.wearwhs.view

import com.android.testutils.ImageDiffUtil
import com.android.test.testutils.TestUtils
import com.android.tools.adtui.swing.FakeUi
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Test
import java.awt.Dimension
import java.nio.file.Path


class WearHealthServicesToolWindowTest : LightPlatformTestCase() {

  private val testDataPath: Path
    get() = TestUtils.resolveWorkspacePathUnchecked("tools/adt/idea/wear-whs/testData")

  @Test
  fun `test panel screenshot matches expectation for current platform`() {
    val fakeUi = FakeUi(WearHealthServicesToolWindow())
    fakeUi.root.size = Dimension(500, 400)
    fakeUi.layoutAndDispatchEvents()

    ImageDiffUtil.assertImageSimilarPerPlatform(
      testDataPath = testDataPath,
      fileNameBase = "screens/whs-panel",
      actual = fakeUi.render(),
      maxPercentDifferent = 3.0)
  }
}
