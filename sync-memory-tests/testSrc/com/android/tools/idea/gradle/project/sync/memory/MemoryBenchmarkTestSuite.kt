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

import com.android.testutils.TestUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.perflogger.Benchmark
import com.android.tools.tests.GradleDaemonsRule
import com.android.tools.tests.IdeaTestSuiteBase
import com.android.tools.tests.LeakCheckerRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.containers.map2Array
import org.jetbrains.android.AndroidTestBase
import org.junit.ClassRule
import org.junit.Rule
import java.nio.file.Path
import java.nio.file.Paths

@RunsInEdt
abstract class MemoryBenchmarkTestSuite : IdeaTestSuiteBase() {

  @get:Rule val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

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


