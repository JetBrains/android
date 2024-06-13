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

import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.databinding.psiclass.LightBindingClass
import com.android.tools.idea.databinding.util.isViewBindingEnabled
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findClass
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.ui.UIUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class DataBindingPackageFinderTest {
  private val projectRule = AndroidGradleProjectRule()

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  /**
   * Expose the underlying project rule fixture directly.
   *
   * We know that the underlying fixture is a [JavaCodeInsightTestFixture] because our
   * [AndroidProjectRule] is initialized to use the disk.
   */
  private val fixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

  @Before
  fun setUp() {
    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
  }

  @Test
  fun dataBindingPackagePathCanBeFoundWhenViewBindingEnabled() {
    projectRule.load(TestDataPaths.PROJECT_FOR_VIEWBINDING)
    val syncState = GradleSyncState.getInstance(projectRule.project)
    assertThat(syncState.isSyncNeeded().toBoolean()).isFalse()

    // Make sure that all file system events up to this point have been processed.
    VirtualFileManager.getInstance().syncRefresh()
    UIUtil.dispatchAllInvocationEvents()

    val facet = projectRule.androidFacet(":app")
    assertThat(facet.isViewBindingEnabled()).isTrue()

    val context = fixture.findClass("com.android.example.viewbinding.MainActivity")

    assertThat(
        fixture.findClass(
          "com.android.example.viewbinding.databinding.ActivityMainBinding",
          context,
        )
      )
      .isInstanceOf(LightBindingClass::class.java)
    assertThat(fixture.findPackage("com.android.example.viewbinding.databinding")).isNotNull()
  }

  @Test
  fun dataBindingPackagePathCanBeFoundWhenDataBindingEnabled() {
    projectRule.load(TestDataPaths.PROJECT_WITH_DATA_BINDING_ANDROID_X)
    val syncState = GradleSyncState.getInstance(projectRule.project)
    assertThat(syncState.isSyncNeeded().toBoolean()).isFalse()

    // Make sure that all file system events up to this point have been processed.
    VirtualFileManager.getInstance().syncRefresh()
    UIUtil.dispatchAllInvocationEvents()

    val context = fixture.findClass("com.android.example.appwithdatabinding.MainActivity")
    assertThat(
        LayoutBindingModuleCache.getInstance(projectRule.androidFacet(":app")).dataBindingMode
      )
      .isEqualTo(DataBindingMode.ANDROIDX)
    assertThat(
        fixture.findClass(
          "com.android.example.appwithdatabinding.databinding.ActivityMainBinding",
          context,
        )
      )
      .isInstanceOf(LightBindingClass::class.java)
    assertThat(fixture.findPackage("com.android.example.appwithdatabinding.databinding"))
      .isNotNull()
  }
}
