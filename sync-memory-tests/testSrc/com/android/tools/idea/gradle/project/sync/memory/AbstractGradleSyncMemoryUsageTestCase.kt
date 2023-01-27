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

import com.android.SdkConstants
import com.android.testutils.TestUtils
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.util.GradleProperties
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
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.createDirectory

@RunsInEdt
abstract class AbstractGradleSyncMemoryUsageTestCase {
  companion object {
    val BENCHMARK = Benchmark.Builder("Retained heap size")
      .setProject("Android Studio Sync Test")
      .build()
  }

  abstract val relativePath: String
  abstract val projectName: String
  abstract val memoryLimitMb: Int

  val projectRule = AndroidGradleProjectRule()
  @get:Rule val ruleChain = org.junit.rules.RuleChain.outerRule(projectRule).around(EdtRule())!!

  private lateinit var outputDirectory: String
  private val memoryAgentPath = System.getProperty("memory.agent.path")

  @Before
  open fun setUp() {
    val projectSettings = GradleProjectSettings()
    projectSettings.distributionType = DistributionType.DEFAULT_WRAPPED
    GradleSettings.getInstance(projectRule.project).linkedProjectsSettings = listOf(projectSettings)
    projectRule.fixture.testDataPath = AndroidTestBase.getModulePath("sync-memory-tests") + File.separator + "testData"
    outputDirectory = File(System.getenv("TEST_TMPDIR"), "snapshots").also {
      it.toPath().createDirectory()
    }.absolutePath
    StudioFlags.GRADLE_HEAP_ANALYSIS_OUTPUT_DIRECTORY.override(outputDirectory)
  }

  @After
  open fun tearDown() {
    val tmpDir = Paths.get(System.getProperty("java.io.tmpdir"))
    val testOutputDir = TestUtils.getTestOutputDir()
    tmpDir
      .resolve(".gradle/daemon").toFile()
      .walk()
      .filter { it.name.endsWith("out.log") }
      .forEach {
        Files.move(it.toPath(), testOutputDir.resolve(it.name))
      }
    StudioFlags.GRADLE_HEAP_ANALYSIS_OUTPUT_DIRECTORY.clearOverride()
    File(outputDirectory).delete()
  }

  @Test
  open fun testSyncMemory() {
    setJvmArgs()
    projectRule.loadProject(relativePath)
    // Free up some memory by closing the Gradle Daemon
    DefaultGradleConnector.close()

    val metricBeforeSync = Metric("${projectName}_Before_Sync")
    val metricBeforeSyncTotal = Metric("${projectName}_Before_Sync_Total")
    val metricAfterSync = Metric("${projectName}_After_Sync")
    val metricAfterSyncTotal = Metric("${projectName}_After_Sync_Total")
    val currentTime = Instant.now().toEpochMilli()
    for (metricFilePath in File(outputDirectory).walk().filter { !it.isDirectory }.asIterable()) {
      when {
        metricFilePath.name.endsWith("before_sync_strong") -> metricBeforeSync
        metricFilePath.name.endsWith("before_sync_total") -> metricBeforeSyncTotal
        metricFilePath.name.endsWith("after_sync_strong") -> metricAfterSync
        metricFilePath.name.endsWith("after_sync_total") -> metricAfterSyncTotal
        else -> null
      }?.addSamples(BENCHMARK, MetricSample(currentTime, metricFilePath.readText().toLong()))
    }
    metricBeforeSync.commit()
    metricBeforeSyncTotal.commit()

    metricAfterSync.commit()
    metricAfterSyncTotal.commit()
  }

  private fun setJvmArgs() {
    GradleProperties(File(projectRule.resolveTestDataPath(relativePath), SdkConstants.FN_GRADLE_PROPERTIES)).apply {
      setJvmArgs(jvmArgs.orEmpty().replace("-Xmx60g", "-Xmx${memoryLimitMb}m"))
      setJvmArgs("$jvmArgs -agentpath:${File(memoryAgentPath).absolutePath}")
      save()
    }
  }
}
