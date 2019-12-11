/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.ide.common.rendering.api.Result
import com.android.tools.idea.rendering.PerfgateRenderUtil.computeAndRecordMetric
import com.android.tools.idea.rendering.PerfgateRenderUtil.getInflateMetric
import com.android.tools.idea.rendering.PerfgateRenderUtil.getRenderMetric
import com.android.tools.idea.res.FrameworkResourceRepositoryManager
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.PERFGATE_COMPLEX_LAYOUT
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.facet.AndroidFacet
import org.mockito.Mockito.mock
import java.util.concurrent.TimeUnit

class RenderComplexPerfgateTest : AndroidGradleTestCase() {

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    RenderTestUtil.beforeRenderTestCase()
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

  fun testComplexInflate() {
    loadProject(PERFGATE_COMPLEX_LAYOUT)

    val module = getModule("app")
    val facet: AndroidFacet = module.androidFacet!!
    val xmlPath = AndroidTestBase.getTestDataPath() +
                  "/projects/perfgateComplexLayout/app/src/main/res/layout/activity_main.xml"
    val file =  LocalFileSystem.getInstance().findFileByPath(xmlPath)!!
    val configuration = RenderTestUtil.getConfiguration(module, file)
    val logger = mock<RenderLogger>(RenderLogger::class.java)

    val computable: ThrowableComputable<PerfgateRenderMetric, Exception> = ThrowableComputable {
      val task = RenderTestUtil.createRenderTask(facet, file, configuration, logger)
      val metric = getInflateMetric(task, this::checkComplexLayoutInflateResult)
      task.dispose().get(5, TimeUnit.SECONDS)
      metric
    }

    computeAndRecordMetric("inflate_time_complex", "inflate_memory_complex", computable)
  }

  fun testComplexRender() {
    loadProject(PERFGATE_COMPLEX_LAYOUT)

    val module = getModule("app")
    val facet: AndroidFacet = module.androidFacet!!
    val xmlPath = AndroidTestBase.getTestDataPath() +
                  "/projects/perfgateComplexLayout/app/src/main/res/layout/activity_main.xml"
    val file =  LocalFileSystem.getInstance().findFileByPath(xmlPath)!!
    val configuration = RenderTestUtil.getConfiguration(module, file)
    val logger = mock<RenderLogger>(RenderLogger::class.java)

    val computable: ThrowableComputable<PerfgateRenderMetric, Exception> = ThrowableComputable{
      val task = RenderTestUtil.createRenderTask(facet, file, configuration, logger)
      val metric = getRenderMetric(task, this::checkComplexLayoutInflateResult, this::checkComplexLayoutRenderResult)
      task.dispose().get(5, TimeUnit.SECONDS)
      metric
    }

    computeAndRecordMetric("render_time_complex", "render_memory_complex", computable)
  }

  private fun checkComplexLayoutInflateResult(result: RenderResult) {
    assertEquals(Result.Status.SUCCESS, result.renderResult.status)
  }

  /**
   * Asserts that the given result matches the [.SIMPLE_LAYOUT] structure
   */
  private fun checkComplexLayoutRenderResult(result: RenderResult) {
    assertEquals(Result.Status.SUCCESS, result.renderResult.status)

    assertNotNull(result.renderedImage)
  }
}