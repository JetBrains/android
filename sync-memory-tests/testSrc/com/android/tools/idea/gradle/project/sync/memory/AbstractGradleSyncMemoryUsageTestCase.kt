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
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject.Companion.openTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.testProjectTemplateFromPath
import com.android.tools.idea.gradle.util.GradleProperties
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.memory.usage.LightweightHeapTraverse
import com.android.tools.memory.usage.LightweightHeapTraverseConfig
import com.android.tools.memory.usage.LightweightTraverseResult
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.android.tools.perflogger.Metric.MetricSample
import com.android.tools.tests.GradleDaemonsRule
import com.android.tools.tests.IdeaTestSuiteBase
import com.android.tools.tests.LeakCheckerRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.containers.map2Array
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.jetbrains.android.AndroidTestBase
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import kotlin.io.path.createDirectory
import kotlin.system.measureTimeMillis

@RunsInEdt
abstract class AbstractGradleSyncMemoryUsageTestCase : IdeaTestSuiteBase() {

  abstract val projectName: String
  abstract val memoryLimitMb: Int?

  @get:Rule
  val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

  private lateinit var outputDirectory: String
  private val memoryAgentPath = System.getProperty("memory.agent.path")

  @Before
  open fun setUp() {
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
    projectRule.openTestProject(testProjectTemplateFromPath(
        path = DIRECTORY,
        testDataPath = TEST_DATA.toString())) {
      // Free up some memory by closing the Gradle Daemon
      DefaultGradleConnector.close()

      // Wait for the IDE to "settle" before taking a measurement. There will be indexing
      // and caching related cleanup jobs still running for a while. This allows them to
      // finish and results in much more reliable values.
      Thread.sleep(Duration.ofSeconds(30).toMillis())

      val metricIdeAfterSync = Metric("${projectName}_IDE_After_Sync")
      val metricIdeAfterSyncTotal = Metric("${projectName}_IDE_After_Sync_Total")
      val metricBeforeSync = Metric("${projectName}_Before_Sync")
      val metricBeforeSyncTotal = Metric("${projectName}_Before_Sync_Total")
      val metricAfterSync = Metric("${projectName}_After_Sync")
      val metricAfterSyncTotal = Metric("${projectName}_After_Sync_Total")

      val currentTime = Instant.now().toEpochMilli()
      var result : LightweightTraverseResult?

      val elapsedTimeAfterSync = measureTimeMillis {
        result = LightweightHeapTraverse.collectReport(LightweightHeapTraverseConfig())
      }
      println("Heap traversal for IDE after sync finished in $elapsedTimeAfterSync milliseconds")

      metricIdeAfterSyncTotal.addSamples(BENCHMARK, MetricSample(currentTime, result!!.totalObjectsSizeBytes))
      metricIdeAfterSync.addSamples(BENCHMARK, MetricSample(currentTime, result!!.totalStrongReferencedObjectsSizeBytes))
      println("IDE total size MBs: ${result!!.totalObjectsSizeBytes shr 20} ")
      println("IDE total object count: ${result!!.totalObjectsNumber} ")
      println("IDE strong size MBs: ${result!!.totalStrongReferencedObjectsSizeBytes shr 20} ")
      println("IDE strong object count: ${result!!.totalStrongReferencedObjectsNumber} ")


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
      metricIdeAfterSync.commit()
      metricIdeAfterSyncTotal.commit()
    }

  }

  private fun setJvmArgs() {
    GradleProperties(TEST_DATA.resolve(DIRECTORY).resolve(SdkConstants.FN_GRADLE_PROPERTIES).toFile()).apply {
      setJvmArgs(jvmArgs.orEmpty().replace("-Xmx60g", if (memoryLimitMb == null) "" else "-Xmx${memoryLimitMb}m"))
      setJvmArgs("$jvmArgs -agentpath:${File(memoryAgentPath).absolutePath}")
      save()
    }
  }

  companion object {
    val DIRECTORY = "benchmark"
    val BENCHMARK = Benchmark.Builder("Retained heap size")
      .setProject("Android Studio Sync Test")
      .build()
    val TEST_DATA: Path = Paths.get(AndroidTestBase.getModulePath("sync-memory-tests")).resolve("testData")

    @JvmField @ClassRule val checker = LeakCheckerRule()
    @JvmField @ClassRule val gradle = GradleDaemonsRule()

    @JvmStatic
    protected fun setUpProject(vararg diffSpecs: String) {
      setUpSourceZip(
        "prebuilts/studio/buildbenchmarks/extra-large.2022.9/src.zip",
        "tools/adt/idea/sync-memory-tests/testData/$DIRECTORY",
        DiffSpec("prebuilts/studio/buildbenchmarks/extra-large.2022.9/diff-properties", 0),
        *(diffSpecs.map2Array { it.toSpec() })
      )
      unzipIntoOfflineMavenRepo("prebuilts/studio/buildbenchmarks/extra-large.2022.9/repo.zip")
      unzipIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin.zip")
      unzipIntoOfflineMavenRepo("tools/data-binding/data_binding_runtime.zip")
      linkIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin_runtime_dependencies.manifest")
      linkIntoOfflineMavenRepo("tools/base/build-system/integration-test/kotlin_gradle_plugin_prebuilts.manifest")
    }
    private fun String.toSpec() = DiffSpec("prebuilts/studio/buildbenchmarks/extra-large.2022.9/$this", 0)
  }
}
