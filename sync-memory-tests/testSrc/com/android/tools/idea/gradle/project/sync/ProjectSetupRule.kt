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
import com.intellij.util.messages.Topic
import org.jetbrains.android.AndroidTestBase
import org.junit.rules.ExternalResource
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

private const val DIRECTORY = "benchmark"
private val rootDirectory = if (TestUtils.runningFromBazel()) Paths.get("") else TestUtils.getTestOutputDir()

private val SUBSET_TO_DIFF = mapOf(
  SUBSET_50_NAME to "diff-50",
  SUBSET_100_NAME to "diff-100",
  SUBSET_200_NAME to "diff-200",
  SUBSET_500_NAME to "diff-500",
  SUBSET_1000_NAME to "diff-1000",
  SUBSET_2000_NAME to "diff-app",
  SUBSET_4200_NAME to null
)

interface ProjectSetupRule {
  val projectName: String
  fun openProject(
    subscriptions: Map<Topic<GradleSyncListenerWithRoot>, GradleSyncListenerWithRoot> = emptyMap(),
    body: (Project) -> Any = {}
  )
}

class ProjectSetupRuleImpl(
  override val projectName: String,
  private val testEnvironmentRule: IntegrationTestEnvironmentRule) : ProjectSetupRule, ExternalResource() {

  override fun before() {
    setUpProject(listOfNotNull(SUBSET_TO_DIFF[projectName]))
  }

  override fun openProject(
    subscriptions: Map<Topic<GradleSyncListenerWithRoot>, GradleSyncListenerWithRoot>,
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
            subscriptions.forEach {
              connection.subscribe(it.key, it.value)
            }
          }
        )
      }) {
      body(it)
    }
  }

  companion object : IdeaTestSuiteBase() {
    fun setUpProject(diffSpecs: List<String>) {
      setUpSourceZip(
        "prebuilts/studio/buildbenchmarks/extra-large.2022.9/src.zip",
        rootDirectory.resolve(DIRECTORY).toString(),
        DiffSpec("prebuilts/studio/buildbenchmarks/extra-large.2022.9/diff-properties", 0),
        *(diffSpecs.map2Array { it.toSpec() })
      )

      unzipIntoOfflineMavenRepo("prebuilts/studio/buildbenchmarks/extra-large.2022.9/repo.zip")
      if (TestUtils.runningFromBazel()) { // If not running from bazel, you'll need to make sure
        // latest AGP is published, with databinding artifacts.
        unzipIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin.zip")
        unzipIntoOfflineMavenRepo("tools/data-binding/data_binding_runtime.zip")
        linkIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin_runtime_dependencies.manifest")
        linkIntoOfflineMavenRepo("tools/base/build-system/integration-test/kotlin_gradle_plugin_prebuilts.manifest")
      }
    }

    private fun String.toSpec() = DiffSpec("prebuilts/studio/buildbenchmarks/extra-large.2022.9/$this", 0)
  }
}

internal fun mutateGradleProperties(function: GradleProperties.() -> Unit) {
  GradleProperties(rootDirectory.resolve(DIRECTORY).resolve(SdkConstants.FN_GRADLE_PROPERTIES).toFile()).apply {
    function(this)
    save()
  }
}


