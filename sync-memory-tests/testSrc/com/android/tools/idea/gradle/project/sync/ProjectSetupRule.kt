/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.SdkConstants
import com.android.testutils.TestUtils
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.testProjectTemplateFromPath
import com.android.tools.idea.gradle.util.GradleProperties
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.tests.IdeaTestSuiteBase
import com.intellij.openapi.project.Project
import com.intellij.util.containers.map2Array
import org.junit.rules.ExternalResource
import java.nio.file.Paths

private const val DIRECTORY = "benchmark"
private val rootDirectory = if (TestUtils.runningFromBazel()) Paths.get("") else TestUtils.getTestOutputDir()

private const val STANDARD_PATH = "prebuilts/studio/buildbenchmarks/extra-large.2022.9"

/**
 * Represents a specific project used for to collect metrics from.
 *
 * It consists of a base path, collections of diffs which will be applied to the project
 * before the benchmark is run and other properties pertaining to the project.
 *
 * The base path is expected to by a directory containing the following files
 *   1. src.zip - the bulk of the project which will be extracted and used as a base for other diffs
 *   2. repo.zip - a maven repository containing all the required dependencies for the project
 *   3. diff-properties - a diff which will be applied to the gradle.properties file of the project
 *
 * The diffs are all assumed to be paths relative to the base path.
 */
enum class BenchmarkProject(val projectPath: String, val maxHeapMB: Int, val diffs: List<String>) {
  STANDARD_50(STANDARD_PATH, maxHeapMB = 400, listOf("diff-50")),
  STANDARD_100(STANDARD_PATH, maxHeapMB = 600, listOf("diff-100")),
  STANDARD_200(STANDARD_PATH, maxHeapMB = 1300, listOf("diff-200")),
  // Below are some experimented values, with gradle 8.2.
  // Keeping them here to make the next update smoother as well
  // Measured usage =  2059 mb, GC time ~11,000 ms
  // x1.15 -  2400 mb -> ~15,500 ms -> BAD
  // x1.30 -  2700 mb -> ~13,000 ms -> BAD
  // x1.45 -  3000 mb -> ~10,000 ms -> GOOD
  // x1.55 -  3200 mb ->   9,000 ms -> GOOD
  STANDARD_500(STANDARD_PATH, maxHeapMB = 3000, listOf("diff-500")),
  // Measured usage =  4233 mb, GC time ~22,000 ms
  // x1.15 -  4800 mb -> ~25,500 ms -> BAD
  // x1.30 -  5500 mb -> ~24,700 ms -> BAD
  // x1.35 -  5700 mb -> ~23,300 ms -> BAD
  // x1.41 -  6000 mb -> ~16,500 ms -> GOOD
  STANDARD_1000(STANDARD_PATH, maxHeapMB = 6000, listOf("diff-1000")),
  // Measured usage = 10931 mb, GC time ~19,000 ms
  // x1.15 - 12500 mb -> ~22,500ms  -> BAD
  // x1.30 - 14200 mb -> ~14,300ms  -> BORDERLINE?
  // x1.40 - 15300 mb -> ~13,600ms  -> GOOD
  // x1.55 - 17000 mb -> ~13,000 ms -> GOOD
  STANDARD_2000(STANDARD_PATH, maxHeapMB = 15300, listOf("diff-2200")),
  STANDARD_4200(STANDARD_PATH, maxHeapMB = 30000, emptyList()),
  MULTI_APP_100(STANDARD_PATH, maxHeapMB = 6000, listOf("diff-100-apps-1300-modules")),
  MULTI_APP_190(STANDARD_PATH, maxHeapMB = 15300, listOf("diff-190-apps-2200-modules"));
}

/**
 * Test rule used to setup a project for metric collection. The [projectName]
 * is generally used as an identifier for any collected metrics whereas the
 * [project] defines which project will be used to collect them.
 */
interface ProjectSetupRule {
  val projectName: String
  val project: BenchmarkProject
  fun openProject(body: (Project) -> Any = {})
  fun addListener(listener: GradleSyncListenerWithRoot)
}

class ProjectSetupRuleImpl(
  override val projectName: String,
  override val project: BenchmarkProject,
  testEnvironmentRuleProvider: () -> IntegrationTestEnvironmentRule) : ProjectSetupRule, ExternalResource() {
  private val listeners = mutableListOf<GradleSyncListenerWithRoot>()
  val testEnvironmentRule: IntegrationTestEnvironmentRule by lazy(testEnvironmentRuleProvider)

  override fun before() {
    setUpProject(project)
  }

  override fun addListener(listener: GradleSyncListenerWithRoot) {
    listeners.add(listener)
  }

  override fun openProject(
    body: (Project) -> Any
  ) {
    testEnvironmentRule.prepareTestProject(
      testProjectTemplateFromPath(
        path = DIRECTORY,
        testDataPath = rootDirectory.toString()),
    ).open(
      updateOptions = {
        it.copy(
          subscribe = { connection ->
            listeners.forEach {
              connection.subscribe(GRADLE_SYNC_TOPIC, it)
            }
          }
        )
      }) {
      body(it)
    }
  }

  companion object : IdeaTestSuiteBase() {
    fun setUpProject(project: BenchmarkProject) {
      setUpSourceZip(
        "${project.projectPath}/src.zip",
        rootDirectory.resolve(DIRECTORY).toString(),
        "diff-properties".toSpec(project),
        "diff-compose-plugin".toSpec(project),
        *(project.diffs.map2Array { it.toSpec(project) })
      )

      unzipIntoOfflineMavenRepo("${project.projectPath}/repo.zip")
      if (TestUtils.runningFromBazel()) { // If not running from bazel, you'll need to make sure
        // latest AGP is published, with databinding artifacts.
        unzipIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin.zip")
        unzipIntoOfflineMavenRepo("tools/data-binding/data_binding_runtime.zip")
        linkIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin_runtime_dependencies.manifest")
        linkIntoOfflineMavenRepo("tools/base/build-system/integration-test/kotlin_gradle_plugin_prebuilts.manifest")
      }
    }

    private fun String.toSpec(project: BenchmarkProject) = DiffSpec("${project.projectPath}/$this", 0)
  }
}

internal fun mutateGradleProperties(function: GradleProperties.() -> Unit) {
  GradleProperties(rootDirectory.resolve(DIRECTORY).resolve(SdkConstants.FN_GRADLE_PROPERTIES).toFile()).apply {
    function(this)
    save()
  }
}


