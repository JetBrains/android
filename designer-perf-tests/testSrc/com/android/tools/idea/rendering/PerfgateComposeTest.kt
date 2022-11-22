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

import com.android.tools.idea.editors.fast.CompilationResult
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NUMBER_OF_SAMPLES = 40

private val composeTimeBenchmark = Benchmark.Builder("Compose Preview Benchmark")
  .setProject("Design Tools")
  .setDescription("Base line for Compose Preview (mean) after $NUMBER_OF_SAMPLES samples.")
  .build()

class PerfgateComposeTest : ComposeRenderTestBase() {

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
      SimpleComposeProjectScenarios.baselineCompileScenario(projectRule)
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
      RenderTimeMeasurement(Metric("default_template_render_time")),
      // Measures the class loading time.
      ClassLoadTimeMeasurment(Metric("default_class_total_load_time")),
      // Measures the class loading time.
      ClassRewriteTimeMeasurement(Metric("default_class_total_rewrite_time")),
      // Measures the number of classes loaded.
      ClassLoadCountMeasurement(Metric("default_class_load_count")),
      // Measures the class avg loading time.
      ClassAverageLoadTimeMeasurement(Metric("default_class_avg_load_time"))),
                                          printSamples = true) {
      SimpleComposeProjectScenarios.baselineRenderScenario(projectRule)
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
      RenderTimeMeasurement(Metric("complex_template_render_time")),
      // Measures the class loading time.
      ClassLoadTimeMeasurment(Metric("complex_template_class_total_load_time")),
      // Measures the class loading time.
      ClassRewriteTimeMeasurement(Metric("complex_template_class_total_rewrite_time")),
      // Measures the number of classes loaded.
      ClassLoadCountMeasurement(Metric("complex_template_class_load_count")),
      // Measures the class avg loading time.
      ClassAverageLoadTimeMeasurement(Metric("complex_template_class_avg_load_time"))),
                                          printSamples = true) {
      SimpleComposeProjectScenarios.complexRenderScenario(projectRule)
    }
  }

  @Test
  fun complexWithBoundsCalculationPerf() {
    composeTimeBenchmark.measureOperation(listOf(
      // Measures the full rendering time, including ModuleClassLoader instantiation, inflation and render.
      ElapsedTimeMeasurement(Metric("complex_with_bounds_template_end_to_end_time")),
      // Measures the memory usage of the render operation end to end.
      MemoryUseMeasurement(Metric("complex_with_bounds_template_memory_use")),
      // Measures just the inflate time.
      InflateTimeMeasurement(Metric("complex_with_bounds_template_inflate_time")),
      // Measures just the render time.
      RenderTimeMeasurement(Metric("complex_with_bounds_template_render_time")),
      // Measures the class loading time.
      ClassLoadTimeMeasurment(Metric("complex_with_bounds_template_class_total_load_time")),
      // Measures the class loading time.
      ClassRewriteTimeMeasurement(Metric("complex_with_bounds_template_class_total_rewrite_time")),
      // Measures the number of classes loaded.
      ClassLoadCountMeasurement(Metric("complex_with_bounds_template_class_load_count")),
      // Measures the class avg loading time.
      ClassAverageLoadTimeMeasurement(Metric("complex_with_bounds_template_class_avg_load_time"))),
                                          printSamples = true) {
      SimpleComposeProjectScenarios.complexRenderScenarioWithBoundsCalculation(projectRule)
    }
  }

  @Test
  fun interactiveClickPerf() {
    composeTimeBenchmark.measureOperation(listOf(
      // Measures the full rendering time, including ModuleClassLoader instantiation, inflation and render.
      ElapsedTimeMeasurement(Metric("interactive_template_end_to_end_time")),
      // Measures just the inflate time.
      InflateTimeMeasurement(Metric("interactive_template_inflate_time")),
      // Measures just the render time.
      RenderTimeMeasurement(Metric("interactive_template_render_time")),
      FirstCallbacksExecutionTimeMeasurement(Metric("interactive_first_callbacks_time")),
      FirstTouchEventTimeMeasurement(Metric("interactive_first_touch_time")),
      PostTouchEventCallbacksExecutionTimeMeasurement(Metric("interactive_post_touch_time"))),
                                          printSamples = true) {
      SimpleComposeProjectScenarios.interactiveRenderScenario(projectRule)
    }
  }

  /**
   * This test is similar to [fastPreviewSingleFileCompileTime] but it measures the time, including the startup time
   * of the daemon. This is meant to simulate the very first "fast preview" build from the user.
   */
  @Test
  fun fastPreviewSingleFileFirstBuild() {
    val project = projectRule.fixture.project
    val mainFile =
      project.guessProjectDir()!!
        .findFileByRelativePath("app/src/main/java/google/simpleapplication/MainActivity.kt")!!
    val psiMainFile = runReadAction { PsiManager.getInstance(project).findFile(mainFile)!! }
    val fastPreviewManager = FastPreviewManager.getInstance(project)

    // Make one full build (required before we use FastPreviewManager
    projectRule.buildAndAssertSuccess()

    runWriteActionAndWait {
      projectRule.fixture.openFileInEditor(mainFile)
      projectRule.fixture.type("//")
    }

    composeTimeBenchmark.measureOperation(
      measures = listOf(ElapsedTimeMeasurement(Metric("fast_preview_single_file_first_build_time"))),
      printSamples = true,
      samplesCount = 10) {
      runWriteActionAndWait {
        projectRule.fixture.type("A")
        PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      }
      runBlocking {
        val (result, _) = fastPreviewManager.compileRequest(psiMainFile, ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!)
        assertTrue("Compilation must pass", result == CompilationResult.Success)
        fastPreviewManager.stopAllDaemons().join()
      }
    }
  }

  @Test
  fun fastPreviewSingleFileCompileTime() {
    val project = projectRule.fixture.project
    val mainFile =
      project.guessProjectDir()!!
        .findFileByRelativePath("app/src/main/java/google/simpleapplication/MainActivity.kt")!!
    val psiMainFile = runReadAction { PsiManager.getInstance(project).findFile(mainFile)!! }
    val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
    val fastPreviewManager = FastPreviewManager.getInstance(project)
    fastPreviewManager.preStartDaemon(module)

    // Make one full build (required before we use FastPreviewManager
    projectRule.buildAndAssertSuccess()

    runWriteActionAndWait {
      projectRule.fixture.openFileInEditor(mainFile)
      projectRule.fixture.type("//")
    }

    composeTimeBenchmark.measureOperation(
      measures = listOf(ElapsedTimeMeasurement(Metric("fast_preview_single_file_compile_time"))),
      printSamples = true,
      samplesCount = 10) {
      runWriteActionAndWait {
        projectRule.fixture.type("A")
        PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      }
      runBlocking {
        val (result, _) = fastPreviewManager.compileRequest(psiMainFile, module)
        assertTrue("Compilation must pass", result == CompilationResult.Success)
      }
    }
  }
}