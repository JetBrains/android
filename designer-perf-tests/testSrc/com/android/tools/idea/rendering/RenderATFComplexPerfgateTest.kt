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

import com.android.tools.configurations.Configuration
import com.android.tools.idea.testing.virtualFile
import com.android.tools.idea.validator.LayoutValidator
import com.android.tools.rendering.RenderResult
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Test

class RenderATFComplexPerfgateTest : ComposeRenderTestBase(PERFGATE_COMPLEX_LAYOUT) {

  private lateinit var facet: AndroidFacet
  private lateinit var layoutFile: VirtualFile
  private lateinit var layoutConfiguration: Configuration

  @Before
  override fun setUp() {
    super.setUp()
    // Enabling this will retrieve text character locations from TextView to improve the
    // accuracy of TextContrastCheck in ATF.
    LayoutValidator.setObtainCharacterLocations(true)
    facet = projectRule.mainAndroidFacet(":app")
    layoutFile = facet.virtualFile("src/main/res/layout/activity_main.xml")
    layoutConfiguration = RenderTestUtil.getConfiguration(facet.module, layoutFile)
  }

  @Test
  fun testATFComplexRender() {
    val computable: ThrowableComputable<PerfgateRenderMetric, Exception> = ThrowableComputable {
      var metric: PerfgateRenderMetric? = null
      RenderTestUtil.withRenderTask(facet, layoutFile, layoutConfiguration, true) {
        metric = getRenderMetric(it, { _ -> }, ::checkATFComplexLayoutRenderResult)
      }
      metric!!
    }

    computeAndRecordMetric("render_time_atf_complex", "render_memory_atf_complex", computable)
  }

  /** Asserts that the given result matches the [.SIMPLE_LAYOUT] structure */
  fun checkATFComplexLayoutRenderResult(result: RenderResult) {
    checkComplexLayoutRenderResult(result)
    verifyValidatorResult(result)
  }
}
