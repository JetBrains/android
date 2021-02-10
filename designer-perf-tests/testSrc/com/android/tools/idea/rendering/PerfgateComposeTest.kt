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

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.compose.preview.renderer.renderPreviewElementForResult
import com.android.tools.idea.compose.preview.util.SinglePreviewElementInstance
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDocumentManager
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val NUMBER_OF_WARM_UP = 2
private const val NUMBER_OF_SAMPLES = 40

private val composeTimeBenchmark = Benchmark.Builder("Compose Preview Benchmark")
  .setProject("Design Tools")
  .setDescription("Base line for Compose Preview (mean) after $NUMBER_OF_SAMPLES samples.")
  .build()

class PerfgateComposeTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Before
  fun setUp() {
    RenderTestUtil.beforeRenderTestCase()
    RenderService.setForTesting(projectRule.project, NoSecurityManagerRenderService(projectRule.project))
    val baseTestPath = resolveWorkspacePath("tools/adt/idea/designer-perf-tests/testData").toString()
    projectRule.fixture.testDataPath = baseTestPath
    projectRule.load(SIMPLE_COMPOSE_PROJECT_PATH)
    projectRule.requestSyncAndWait()

    projectRule.invokeTasks("compileDebugSources").apply {
      buildError?.printStackTrace()
      assertTrue("The project must compile correctly for the test to pass", isBuildSuccessful)
    }
  }

  @After
  fun tearDown() {
    ApplicationManager.getApplication().invokeAndWait {
      RenderTestUtil.afterRenderTestCase()
    }
    RenderService.setForTesting(projectRule.project, null)
  }

  @Test
  fun baselineCompileTime() {
    val mainFile =
      projectRule.fixture.project.guessProjectDir()!!
        .findFileByRelativePath("app/src/main/java/google/simpleapplication/MainActivity.kt")!!
    ApplicationManager.getApplication().invokeAndWait {
      WriteAction.run<Throwable> {
        projectRule.fixture.openFileInEditor(mainFile)
        projectRule.fixture.type("//")
      }
    }

    composeTimeBenchmark.measureOperation(
      measures = listOf(ElapsedTimeMeasurement(Metric("kotlin_compile_time"))),
      samplesCount = 10) {
      ApplicationManager.getApplication().invokeAndWait {
        WriteAction.run<Throwable> {
          projectRule.fixture.type("A")
          PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
        }
      }
      // Modify the file to make sure a change is done
      assertTrue(projectRule.invokeTasks("compileDebugKotlin").isBuildSuccessful)
    }
  }

  @Test
  fun baselinePerf() {
    composeTimeBenchmark.measureOperation(listOf(
      // Measures the full rendering time, including ModuleClassLoader instantiation, inflation and render.
      ElapsedTimeMeasurement(Metric("default_template_end_to_end_time")),
      // Measures the memory usage of the render operation end to end.
      MemoryUseMeasurement(Metric("default_template_memory_use")),
      // Measures just the inflate time.
      InflateTimeMeasurement(Metric("default_template_inflate_time")),
      // Measures just the render time.
      RenderTimeMeasurement(Metric("default_template_render_time"))),
      printSamples = true) {
      val renderResult = renderPreviewElementForResult(projectRule.androidFacet(":app"),
                                                       SinglePreviewElementInstance.forTesting(
                                                         "google.simpleapplication.MainActivityKt.DefaultPreview")).get()
      val image = renderResult!!.renderedImage
      assertTrue(
        "Valid result image is expected to be bigger than 10x10. It's ${image.width}x${image.height}",
        image.width > 10 && image.height > 10)
      assertNotNull(image.copy)

      renderResult
    }
  }

  @Test
  fun complexPerf() {
    composeTimeBenchmark.measureOperation(listOf(
      // Measures the full rendering time, including ModuleClassLoader instantiation, inflation and render.
      ElapsedTimeMeasurement(Metric("complex_template_end_to_end_time")),
      // Measures the memory usage of the render operation end to end.
      MemoryUseMeasurement(Metric("complex_template_memory_use")),
      // Measures just the inflate time.
      InflateTimeMeasurement(Metric("complex_template_inflate_time")),
      // Measures just the render time.
      RenderTimeMeasurement(Metric("complex_template_render_time"))),
                                          printSamples = true) {
      val renderResult = renderPreviewElementForResult(projectRule.androidFacet(":app"),
                                                       SinglePreviewElementInstance.forTesting(
                                                         "google.simpleapplication.ComplexPreviewKt.ComplexPreview"),
                                                        true).get()
      val image = renderResult!!.renderedImage
      assertTrue(
        "Valid result image is expected to be bigger than 10x10. It's ${image.width}x${image.height}",
        image.width > 10 && image.height > 10)
      assertNotNull(image.copy)

      renderResult
    }
  }
}