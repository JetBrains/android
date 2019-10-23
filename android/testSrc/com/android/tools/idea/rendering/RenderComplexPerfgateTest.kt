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
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.idea.rendering.PerfgateRenderUtil.NUMBER_OF_SAMPLES
import com.android.tools.idea.rendering.PerfgateRenderUtil.NUMBER_OF_WARM_UP
import com.android.tools.idea.rendering.PerfgateRenderUtil.pruneOutliers
import com.android.tools.idea.rendering.PerfgateRenderUtil.sRenderMemoryBenchMark
import com.android.tools.idea.rendering.PerfgateRenderUtil.sRenderTimeBenchMark
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.PERFGATE_COMPLEX_LAYOUT
import com.android.tools.idea.util.androidFacet
import com.android.tools.perflogger.Metric
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.facet.AndroidFacet
import org.mockito.Mockito.mock
import java.awt.image.BufferedImage
import java.io.File
import java.util.ArrayList
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

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
      //myFixture.addFileToProject("res/layout/activity_main.xml", COMPLEX_LAYOUT).virtualFile
    val configuration = RenderTestUtil.getConfiguration(module, file)
    val logger = mock<RenderLogger>(RenderLogger::class.java)

    val computable: ThrowableComputable<PerfgateRenderMetric, Exception> = ThrowableComputable{
      val task = RenderTestUtil.createRenderTask(facet, file, configuration, logger)
      val metric = getInflateMetric(task)
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
    //myFixture.addFileToProject("res/layout/activity_main.xml", COMPLEX_LAYOUT).virtualFile
    val configuration = RenderTestUtil.getConfiguration(module, file)
    val logger = mock<RenderLogger>(RenderLogger::class.java)

    val computable: ThrowableComputable<PerfgateRenderMetric, Exception> = ThrowableComputable{
      val task = RenderTestUtil.createRenderTask(facet, file, configuration, logger)
      val metric = getRenderMetric(task)
      task.dispose().get(5, TimeUnit.SECONDS)
      metric
    }

    computeAndRecordMetric("render_time_complex", "render_memory_complex", computable)
  }

  private fun computeAndRecordMetric(
    renderMetricName: String, memoryMetricName: String, computable: ThrowableComputable<PerfgateRenderMetric, Exception>) {

    System.gc()

    // LayoutLib has a large static initialization that would trigger on the first render.
    // Warm up by inflating few times before measuring.
    for (i in 0 until NUMBER_OF_WARM_UP) {
      computable.compute()
    }

    // baseline samples
    val renderTimes = ArrayList<Metric.MetricSample>()
    val memoryUsages = ArrayList<Metric.MetricSample>()

    for (i in 0 until NUMBER_OF_SAMPLES) {
      val metric = computable.compute()

      renderTimes.add(metric.renderTimeMetricSample)
      memoryUsages.add(metric.memoryMetricSample)
    }

    val renderMetric = Metric(renderMetricName)
    val result = pruneOutliers(renderTimes)
    renderMetric.addSamples(sRenderTimeBenchMark, *result.toTypedArray())
    renderMetric.commit()

    // Let's start without pruning to see how bad it is.
    val memMetric = Metric(memoryMetricName)
    memMetric.addSamples(sRenderMemoryBenchMark, *memoryUsages.toTypedArray())
    memMetric.commit()
  }

  private fun getInflateMetric(task: RenderTask): PerfgateRenderMetric {
    val renderMetric = PerfgateRenderMetric()

    renderMetric.beforeTest()
    val result = Futures.getUnchecked(task.inflate())
    renderMetric.afterTest()

    checkComplexLayoutInflateResult(result)
    return renderMetric
  }

  private fun getRenderMetric(task: RenderTask): PerfgateRenderMetric {
    checkComplexLayoutInflateResult(Futures.getUnchecked(task.inflate()))
    val renderMetric = PerfgateRenderMetric()

    renderMetric.beforeTest()
    val result = Futures.getUnchecked(task.render())
    renderMetric.afterTest()

    checkComplexLayoutRenderResult(result)
    return renderMetric
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