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
import java.util.logging.Logger

@RunWith(JarTestSuiteRunner::class)
@ExcludeClasses(SyncMemoryTestSuite::class)
object SyncMemoryTestSuite : IdeaTestSuiteBase() {
  @JvmField @ClassRule  val checker = LeakCheckerRule()
  @JvmField @ClassRule  val gradle = GradleDaemonsRule()

  init {
    setUpSourceZip(
      "prebuilts/studio/buildbenchmarks/extra-large.2022.9/src.zip",
      "tools/adt/idea/sync-memory-tests/testData/benchmark-100",
      DiffSpec("prebuilts/studio/buildbenchmarks/extra-large.2022.9/diff-100", 0),
      DiffSpec("prebuilts/studio/buildbenchmarks/extra-large.2022.9/diff-properties", 0)
    )
    unzipIntoOfflineMavenRepo("prebuilts/studio/buildbenchmarks/extra-large.2022.9/repo.zip")
    unzipIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin.zip")
    linkIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin_runtime_dependencies.manifest")
    linkIntoOfflineMavenRepo("tools/base/build-system/integration-test/kotlin_gradle_plugin_prebuilts.manifest")
  }
}