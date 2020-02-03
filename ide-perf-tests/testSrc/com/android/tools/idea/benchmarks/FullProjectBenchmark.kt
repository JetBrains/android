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
package com.android.tools.idea.benchmarks

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.AndroidTestBase
import org.junit.After
import org.junit.Ignore
import org.junit.Test
import java.io.File

/**
 * Contains performance tests for a particular project defined by the subclasses.
 *
 * The gradleRule is shared between tests to preserve the sync state for benchmarks between tests.
 * The subclass must provide an AndroidGradleProjectRule and call FullProjectBenchmark.loadProject in @BeforeClass function.
 */
@Ignore
abstract class FullProjectBenchmark {
  abstract val projectName: String
  abstract val fileTypes: List<FileType>
  abstract val gradleRule: AndroidGradleProjectRule

  @After
  fun tearDown() {
    runInEdtAndWait {
      clearCaches()
    }
  }

  @Test
  fun fullProjectHighlighting() {
    runInEdtAndWait {
      for (fileType in fileTypes) {
        measureHighlighting(fileType)
      }
    }
  }

  companion object {
    fun loadProject(gradleRule: AndroidGradleProjectRule, projectName: String) {
      val modulePath = AndroidTestBase.getModulePath("ide-perf-tests")
      gradleRule.fixture.testDataPath = modulePath + File.separator + "testData"
      disableExpensivePlatformAssertions(gradleRule.fixture)
      enableAllDefaultInspections(gradleRule.fixture)

      gradleRule.load(projectName)
      gradleRule.generateSources() // Gets us closer to a production setup.
      waitForAsyncVfsRefreshes() // Avoids write actions during highlighting.
    }

    val highlightingBenchmark = Benchmark.Builder("Full Project Highlighting")
      .setDescription("""
        For each test project, this benchmark runs syntax highlighting on all files of a given file type
        and records the time elapsed per file. All measurements are given in milliseconds.

        Perfgate will by default only show the *average* per-file highlighting time. To get a
        better sense of performance on large/complex files, enable the 95th percentile trend line.
        This is especially important for XML because many XML files are trivial string resources.

        Note: "syntax highlighting" includes Android Lint and other inspections that are enabled by default.
      """.trimIndent())
      .setProject(EDITOR_PERFGATE_PROJECT_NAME)
      .build()
  }

  private fun clearCaches() {
    PsiManager.getInstance(gradleRule.project).dropPsiCaches()
    System.gc() // May help reduce noise, but it's just a hope. Investigate as needed.
  }

  /** Measures highlighting performance for all project source files of the given type. */
  private fun measureHighlighting(fileType: FileType) {
    // Collect files.
    val project = gradleRule.project
    val files = FileTypeIndex.getFiles(fileType, ProjectScope.getContentScope(project))
    assert(files.isNotEmpty())

    // Warmup.
    val fixture = gradleRule.fixture
    var errorCount = 0
    for (file in files) {
      fixture.openFileInEditor(file)
      val errors = fixture.doHighlighting(HighlightSeverity.ERROR)
      errorCount += errors.size

      // If the test happens to be broken, then the highlighting errors might hint at why.
      if (errors.isNotEmpty()) {
        println("There are ${errors.size} errors in ${file.name}")
        val errorList = errors.joinToString("\n") { it.description }
        println(errorList.prependIndent("    "))
      }
    }

    // Reset.
    clearCaches()

    // Measure.
    var totalTimeMs = 0L
    val samples = mutableListOf<Metric.MetricSample>()
    val timePerFile = mutableListOf<Pair<String, Long>>()
    for (file in files) {
      fixture.openFileInEditor(file)
      val timeMs = measureElapsedMillis {
        fixture.doHighlighting(HighlightSeverity.ERROR)
      }
      samples.add(Metric.MetricSample(System.currentTimeMillis(), timeMs))
      timePerFile.add(Pair(file.name, timeMs))
      totalTimeMs += timeMs
    }

    // Diagnostic logging.
    println("""
      ===
      Project: $projectName
      File type: ${fileType.description}
      Total files: ${files.size}
      Total errors: $errorCount
      Total time: $totalTimeMs ms
      Average time: ${totalTimeMs / files.size} ms
      ===
    """.trimIndent())
    timePerFile.sortByDescending { (_, timeMs) -> timeMs }
    for ((fileName, timeMs) in timePerFile) {
      println("$timeMs ms --- ${fileName}")
    }

    // Perfgate.
    val metric = Metric("${projectName}_${fileType.description}")
    metric.addSamples(highlightingBenchmark, *samples.toTypedArray())
    metric.commit()
  }
}