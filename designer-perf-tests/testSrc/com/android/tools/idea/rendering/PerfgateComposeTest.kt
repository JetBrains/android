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

import com.android.testutils.TestUtils
import com.android.tools.idea.compose.preview.PreviewElement
import com.android.tools.idea.compose.preview.renderer.renderPreviewElement
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.google.common.collect.LinkedListMultimap
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
import java.time.Instant

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
    val baseTestPath = TestUtils.getWorkspaceFile("tools/adt/idea/designer-perf-tests/testData").path
    projectRule.fixture.testDataPath = baseTestPath
    projectRule.load(SIMPLE_COMPOSE_PROJECT_PATH)
    projectRule.requestSyncAndWait()

    assertTrue("The project must compile correctly for the test to pass",
               projectRule.invokeTasks("compileDebugSources").isBuildSuccessful)
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
      ElapsedTimeMeasurement(Metric("default_template_render_time")),
      MemoryUseMeasurement(Metric("default_template_memory_use")))) {
      val defaultRender = renderPreviewElement(projectRule.androidFacet(":app"),
                                               PreviewElement.forTesting("google.simpleapplication.MainActivityKt.DefaultPreview")).get()
      assertNotNull(defaultRender)
    }
  }
}