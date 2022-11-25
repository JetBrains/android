/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.memory

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.android.tools.perflogger.Metric.MetricSample
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.io.path.createDirectory
import kotlin.system.measureTimeMillis

@RunsInEdt
abstract class AbstractGradleSyncMemoryUsageTestCase {
  companion object {
    val BENCHMARK = Benchmark.Builder("Retained heap size")
      .setProject("Android Studio Sync Test")
      .build()
  }

  abstract val relativePath: String
  abstract val projectName: String

  val projectRule = AndroidGradleProjectRule()
  @get:Rule val ruleChain = org.junit.rules.RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val eclipseMatHelper = EclipseMatHelper()
  private lateinit var snapshotDirectory: String

  @Before
  open fun setUp() {
    val projectSettings = GradleProjectSettings()
    projectSettings.distributionType = DistributionType.DEFAULT_WRAPPED
    GradleSettings.getInstance(projectRule.project).linkedProjectsSettings = listOf(projectSettings)
    projectRule.fixture.testDataPath = AndroidTestBase.getModulePath("sync-memory-tests") + File.separator + "testData"
    snapshotDirectory = File(System.getenv("TEST_TMPDIR"), "snapshots").also {
      it.toPath().createDirectory()
    }.absolutePath
    StudioFlags.GRADLE_HPROF_OUTPUT_DIRECTORY.override(snapshotDirectory)
  }

  @After
  open fun tearDown() {
    StudioFlags.GRADLE_HPROF_OUTPUT_DIRECTORY.clearOverride()
    File(snapshotDirectory).delete()
  }

  @Test
  open fun testSyncMemory() {
    projectRule.loadProject(relativePath)
    // Free up some memory by closing the Gradle Daemon
    DefaultGradleConnector.close()

    val metricBeforeSync = Metric("${projectName}_Before_Sync")
    val metricAfterSync = Metric("${projectName}_After_Sync")
    val currentTime = Instant.now().toEpochMilli()
    for (hprofPath in File(snapshotDirectory).walk().filter { !it.isDirectory && it.name.endsWith(".hprof")}.asIterable()) {
      val elapsedTime = measureTimeMillis {
        val size = eclipseMatHelper.getHeapDumpSize(hprofPath.absolutePath)
        println("Size of ${hprofPath.name}: $size")
        if (hprofPath.name.contains("before_sync")) {
          metricBeforeSync.addSamples(BENCHMARK, MetricSample(currentTime, size))
        }
        if (hprofPath.name.contains("after_sync")) {
          metricAfterSync.addSamples(BENCHMARK, MetricSample(currentTime, size))
        }
      }
      println("Analysis took $elapsedTime MS.")
    }
    metricAfterSync.commit()
    metricBeforeSync.commit()
  }
}