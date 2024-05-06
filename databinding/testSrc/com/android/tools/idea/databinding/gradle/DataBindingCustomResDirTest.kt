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
package com.android.tools.idea.databinding.gradle

import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.databinding.TestDataPaths.PROJECT_WITH_DATA_BINDING_ANDROID_X
import com.android.tools.idea.databinding.TestDataPaths.PROJECT_WITH_DATA_BINDING_SUPPORT
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.fileUnderGradleRoot
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests that ensure that layouts can be found inside custom resource directories, which can be
 * defined like so:
 * ```
 *   android { main { sourceSets { res.srcDirs += ['...'] } } }
 * ```
 */
@RunsInEdt
@RunWith(Parameterized::class)
class DataBindingCustomResDirTest(private val projectPath: String) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}")
    val projectPaths =
      listOf(PROJECT_WITH_DATA_BINDING_ANDROID_X, PROJECT_WITH_DATA_BINDING_SUPPORT)
  }

  private val projectRule = AndroidGradleProjectRule()

  @get:Rule val chainedRule = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @Test
  fun canFindBindingsGeneratedFromLayoutsInCustomResourceDirectories() {
    projectRule.fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    projectRule.load(projectPath)

    val project = projectRule.project
    val fixture = projectRule.fixture as JavaCodeInsightTestFixture

    val syncState = GradleSyncState.getInstance(project)
    assertThat(syncState.isSyncNeeded().toBoolean()).isFalse()

    // The following activity references a Binding class generated from
    // `res-alt/layout/activity_res_alt.xml`, which only works if we respected the module's
    // `build.gradle` settings, which defines `res-alt` as a custom resource directory.
    val file =
      projectRule
        .findGradleModule(":app")!!
        .fileUnderGradleRoot(
          "src/main/java/com/android/example/appwithdatabinding/ResAltActivity.java"
        )!!
    fixture.configureFromExistingVirtualFile(file)
    fixture.checkHighlighting(false, false, false)
  }
}
