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
package com.android.tools.idea.run

import com.android.testutils.MockitoKt
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import junit.framework.TestCase.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class UnsignedApkQuickFixTest {
  @get:Rule val projectRule = AndroidGradleProjectRule()
  private var quickFix: UnsignedApkQuickFix? = null

  @After
  fun dispose() {
    quickFix?.let { Disposer.dispose(it) }
  }

  @Test
  fun updatesBuildWithSelectedConfig() {
    projectRule.load(TestProjectPaths.SIMPLE_APPLICATION)

    // There should be a default debug signing config.
    val module = projectRule.fixture.module
    val buildModel = ProjectBuildModel.get(projectRule.project).getModuleBuildModel(module)!!
    val signingConfigs = buildModel.android().signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    assertThat(signingConfigs[0].name()).isEqualTo("debug")

    // Fake a selector that picks the default debug signing config.
    val fakeSelector =
      object : SigningConfigSelector {
        override fun showAndGet() = true

        override fun selectedConfig() = signingConfigs[0]
      }

    // Release build doesn't have a signing config assigned.
    val releaseBuildSigningConfig =
      buildModel.android().buildTypes().find { it.name() == "release" }?.signingConfig()
    assertThat(releaseBuildSigningConfig?.valueAsString()).isNull()

    val unsignedApkQuickFix = UnsignedApkQuickFix(module, "release", null) { fakeSelector }
    unsignedApkQuickFix.applyFix(DataContext.EMPTY_CONTEXT)
    val updatedBuildModel = ProjectBuildModel.get(projectRule.project).getModuleBuildModel(module)!!

    // Release build is now assigned the debug signing config.
    val expectedSigningConfig =
      updatedBuildModel.android().buildTypes().find { it.name() == "release" }?.signingConfig()
    assertThat(expectedSigningConfig?.valueAsString()).contains("debug")
  }

  @Test
  fun differentModuleReCaches() {
    val module1 = MockitoKt.mock<Module>()
    UnsignedApkQuickFix.unsignedApkQuickFix = UnsignedApkQuickFix(module1, "release", null)

    val module2 = MockitoKt.mock<Module>()
    quickFix = UnsignedApkQuickFix.create(module2, "release", null)

    assertEquals(module2, quickFix!!.module)

    Disposer.dispose(module1)
    Disposer.dispose(module2)
  }

  @Test
  fun differentBuildTypeReCaches() {
    val module1 = MockitoKt.mock<Module>()
    UnsignedApkQuickFix.unsignedApkQuickFix = UnsignedApkQuickFix(module1, "release", null)

    quickFix = UnsignedApkQuickFix.create(module1, "debug", null)

    assertEquals("debug", quickFix!!.selectedBuildTypeName)

    Disposer.dispose(module1)
  }

  /**
   * Tests the case where validation has been run already, but then the {@code
   * AndroidRunConfigurationEditor} is opened, creating a new QuickFix where the callback would
   * trigger the editor revalidation.
   */
  @Test
  fun nonNullCallbackReCachesIfCurrentlyNull() {
    val module1 = MockitoKt.mock<Module>()
    UnsignedApkQuickFix.unsignedApkQuickFix = UnsignedApkQuickFix(module1, "release", null)

    val callback = MockitoKt.mock<Runnable>()
    quickFix = UnsignedApkQuickFix.create(module1, "release", callback)

    assertEquals(callback, quickFix!!.callback)

    Disposer.dispose(module1)
  }

  /**
   * Tests the case where the {@code AndroidRunConfigurationEditor} has already set a revalidation
   * callback, but a different validation request would have created a new QuickFix. In this case,
   * we do not want to overwrite the cache.
   */
  @Test
  fun nullCallbackDoesNotReCache() {
    val module1 = MockitoKt.mock<Module>()
    val callback = MockitoKt.mock<Runnable>()
    UnsignedApkQuickFix.unsignedApkQuickFix = UnsignedApkQuickFix(module1, "release", callback)

    quickFix = UnsignedApkQuickFix.create(module1, "release", null)

    assertEquals(callback, quickFix!!.callback)

    Disposer.dispose(module1)
  }

  /**
   * Tests the case where a different callback is requested, e.g. if the {@code
   * AndroidRunConfigurationEditor} dialog is opened again, meaning the new dialog would need to
   * receive the revalidation request.
   */
  @Test
  fun differentCallbackReCaches() {
    val module1 = MockitoKt.mock<Module>()
    val callback = MockitoKt.mock<Runnable>()
    UnsignedApkQuickFix.unsignedApkQuickFix = UnsignedApkQuickFix(module1, "release", callback)

    val callback2 = MockitoKt.mock<Runnable>()
    quickFix = UnsignedApkQuickFix.create(module1, "release", callback2)

    assertEquals(callback2, quickFix!!.callback)

    Disposer.dispose(module1)
  }
}
