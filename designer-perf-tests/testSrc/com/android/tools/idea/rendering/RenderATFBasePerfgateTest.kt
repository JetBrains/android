/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.validator.LayoutValidator
import com.android.tools.res.FrameworkResourceRepositoryManager
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.AndroidTestCase

class RenderATFBasePerfgateTest : AndroidTestCase() {

  private lateinit var layoutFile: VirtualFile
  private lateinit var layoutConfiguration: Configuration

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    // Enabling this will retrieve text character locations from TextView to improve the
    // accuracy of TextContrastCheck in ATF.
    LayoutValidator.setObtainCharacterLocations(true)
    RenderTestUtil.beforeRenderTestCase()

    layoutFile = myFixture.addFileToProject("res/layout/layout.xml", SIMPLE_LAYOUT).virtualFile
    layoutConfiguration = RenderTestUtil.getConfiguration(myModule, layoutFile)
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      RenderTestUtil.afterRenderTestCase()
    }
    finally {
      FrameworkResourceRepositoryManager.getInstance().clearCache()
      super.tearDown()
    }
  }

  @Throws(Exception::class)
  fun testATFBaseRender() {
    val computable = ThrowableComputable<PerfgateRenderMetric, Exception> {
      var metric: PerfgateRenderMetric? = null
      RenderTestUtil.withRenderTask(myFacet, layoutFile, layoutConfiguration, true) {
        metric = getRenderMetric(it, {_ -> }, ::checkATFSimpleLayoutResult)
      }
      metric!!
    }
    computeAndRecordMetric("render_time_atf_base", "render_memory_atf_base", computable)
  }

  /**
   * Asserts that the given result matches the [.SIMPLE_LAYOUT] structure
   */
  private fun checkATFSimpleLayoutResult(result: RenderResult) {
    checkSimpleLayoutResult(result)
    verifyValidatorResult(result)
  }
}