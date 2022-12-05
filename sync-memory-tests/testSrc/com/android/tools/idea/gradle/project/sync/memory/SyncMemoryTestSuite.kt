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
package com.android.tools.idea.gradle.project.sync.memory

import com.android.testutils.JarTestSuiteRunner
import com.android.testutils.JarTestSuiteRunner.ExcludeClasses
import com.android.tools.tests.GradleDaemonsRule
import com.android.tools.tests.IdeaTestSuiteBase
import com.android.tools.tests.LeakCheckerRule
import org.junit.ClassRule
import org.junit.runner.RunWith

class Benchmark100MemoryTest : AbstractGradleSyncMemoryUsageTestCase() {
  override val relativePath = "benchmark-100"
  override val projectName = "100Modules"
}

class Benchmark200MemoryTest : AbstractGradleSyncMemoryUsageTestCase() {
  override val relativePath = "benchmark-200"
  override val projectName = "200Modules"
}

class Benchmark500MemoryTest : AbstractGradleSyncMemoryUsageTestCase() {
  override val relativePath = "benchmark-500"
  override val projectName = "500Modules"
}

@RunWith(JarTestSuiteRunner::class)
object SyncMemoryTestSuite : IdeaTestSuiteBase() {
  @JvmField @ClassRule  val checker = LeakCheckerRule()
  @JvmField @ClassRule  val gradle = GradleDaemonsRule()

  init {
    setUpProject("benchmark-100", "diff-100")
    setUpProject("benchmark-200", "diff-200")
    setUpProject("benchmark-500", "diff-500")
    unzipIntoOfflineMavenRepo("prebuilts/studio/buildbenchmarks/extra-large.2022.9/repo.zip")
    unzipIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin.zip")
    unzipIntoOfflineMavenRepo("tools/data-binding/data_binding_runtime.zip")
    linkIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin_runtime_dependencies.manifest")
    linkIntoOfflineMavenRepo("tools/base/build-system/integration-test/kotlin_gradle_plugin_prebuilts.manifest")
  }

  private fun setUpProject(directory: String, diffFileName: String) {
    setUpSourceZip(
      "prebuilts/studio/buildbenchmarks/extra-large.2022.9/src.zip",
      "tools/adt/idea/sync-memory-tests/testData/$directory",
      DiffSpec("prebuilts/studio/buildbenchmarks/extra-large.2022.9/$diffFileName", 0),
      DiffSpec("prebuilts/studio/buildbenchmarks/extra-large.2022.9/diff-properties", 0)
    )
  }
}