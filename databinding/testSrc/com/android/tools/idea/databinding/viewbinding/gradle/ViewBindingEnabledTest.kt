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
package com.android.tools.idea.databinding.viewbinding.gradle

import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.databinding.ViewBindingEnabledTrackingService
import com.android.tools.idea.databinding.util.isViewBindingEnabled
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findClass
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder

/**
 * Test which verifies that enabling view binding in a Gradle project causes its layout's light
 * classes to start getting generated at that time.
 */
@RunsInEdt
class ViewBindingEnabledTest {
  private val projectRule = AndroidGradleProjectRule()

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @get:Rule val temporaryFolder = TemporaryFolder()

  /**
   * Expose the underlying project rule fixture directly.
   *
   * We know that the underlying fixture is a [JavaCodeInsightTestFixture] because our
   * [AndroidProjectRule] is initialized to use the disk.
   */
  private val fixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

  private fun enableViewBinding(buildFile: File, enabled: Boolean) {
    assertThat(buildFile.exists()).isTrue()

    // Kind of hacky, but this assumes the only instance of `enabled = ...` is from:
    // `viewBinding\n{ enabled = [true|false] }`
    val oldText = buildFile.readText()
    val newText = oldText.replace(Regex("enabled = .+"), "enabled = $enabled")
    if (newText != oldText) {
      buildFile.writeText(newText)
    }
  }

  @Test
  fun enablingViewBindingEnablesLightBindingClassGeneration() {
    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    lateinit var buildFile: File
    projectRule.load(
      projectPath = TestDataPaths.PROJECT_FOR_VIEWBINDING,
      preLoad = { projectRoot ->
        buildFile = File(projectRoot, "app/build.gradle")
        enableViewBinding(buildFile, false)
      },
    )

    // Trigger resource repository initialization
    val facet = projectRule.androidFacet(":app")
    StudioResourceRepositoryManager.getAppResources(facet)
    IndexingTestUtil.waitUntilIndexesAreReady(projectRule.project)

    // Context needed for searching for light classes
    val context = fixture.findClass("com.android.example.viewbinding.MainActivity")

    var lastModificationCount = ViewBindingEnabledTrackingService.getInstance().modificationCount

    assertThat(facet.isViewBindingEnabled()).isFalse()
    assertThat(
        fixture.findClass(
          "com.android.example.viewbinding.databinding.ActivityMainBinding",
          context,
        )
      )
      .isNull()

    enableViewBinding(buildFile, true)
    projectRule.requestSyncAndWait()
    IndexingTestUtil.waitUntilIndexesAreReady(projectRule.project)

    assertThat(facet.isViewBindingEnabled()).isTrue()
    assertThat(ViewBindingEnabledTrackingService.getInstance().modificationCount)
      .isGreaterThan(lastModificationCount)
    lastModificationCount = ViewBindingEnabledTrackingService.getInstance().modificationCount
    assertThat(
        fixture.findClass(
          "com.android.example.viewbinding.databinding.ActivityMainBinding",
          context,
        )
      )
      .isNotNull()

    enableViewBinding(buildFile, false)
    projectRule.requestSyncAndWait()
    IndexingTestUtil.waitUntilIndexesAreReady(projectRule.project)

    assertThat(facet.isViewBindingEnabled()).isFalse()
    assertThat(ViewBindingEnabledTrackingService.getInstance().modificationCount)
      .isGreaterThan(lastModificationCount)
    assertThat(
        fixture.findClass(
          "com.android.example.viewbinding.databinding.ActivityMainBinding",
          context,
        )
      )
      .isNull()
  }
}
