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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.project.sync.CapturePlatformModelsProjectResolverExtension.Companion.getKaptModel
import com.android.tools.idea.gradle.project.sync.CapturePlatformModelsProjectResolverExtension.Companion.getKotlinModel
import com.android.tools.idea.gradle.project.sync.CapturePlatformModelsProjectResolverExtension.Companion.registerTestHelperProjectResolver
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.gradleModule
import com.google.common.truth.Expect
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class KotlinSingleVariantSyncIntegrationTest {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  var expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun kotlinAndKaptSingleVariantSync() {
    registerTestHelperProjectResolver(CapturePlatformModelsProjectResolverExtension.IdeModels(), projectRule.testRootDisposable)
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.KOTLIN_KAPT)
    preparedProject.open { project ->
      expect.that(getKotlinModel(project.gradleModule(":app")!!)?.testSourceSetNames().orEmpty())
        .containsExactly("debugAndroidTest", "debug", "debugUnitTest")
      expect.that(getKaptModel(project.gradleModule(":app")!!)?.testSourceSetNames().orEmpty())
        .containsExactly("debugAndroidTest", "debug", "debugUnitTest")
    }
  }
}
