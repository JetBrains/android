/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.testutils.junit4.OldAgpTest
import com.android.tools.idea.gradle.project.sync.CapturePlatformModelsProjectResolverExtension.Companion.getKaptModel
import com.android.tools.idea.gradle.project.sync.CapturePlatformModelsProjectResolverExtension.Companion.registerTestHelperProjectResolver
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.gradleModule
import com.google.common.truth.Expect
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@OldAgpTest(gradleVersions = ["8.13"], agpVersions = ["8.13.0"])
@RunsInEdt
class KaptSingleVariantSyncIntegrationTest {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  var expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun kaptSingleVariantSync() {
    registerTestHelperProjectResolver(CapturePlatformModelsProjectResolverExtension.IdeModels(), projectRule.testRootDisposable)
    // The `KaptGradleModel` is not available when the built-in Kotlin Gradle plugin is used (the default in AGP 9.0+).
    // This test is pinned to an older AGP version to maintain test coverage for the Kapt model.
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.KOTLIN_KAPT,
                                                         agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_8_13)
    preparedProject.open { project ->
      expect.that(getKaptModel(project.gradleModule(":app")!!)?.testSourceSetNames().orEmpty())
        .containsExactly("debugAndroidTest", "debug", "debugUnitTest")
    }
  }
}
