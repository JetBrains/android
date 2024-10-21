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
package com.android.tools.idea.npw.module

import com.android.tools.idea.npw.module.recipes.macrobenchmarkModule.addProfileableToTargetManifest
import com.android.tools.idea.npw.module.recipes.macrobenchmarkModule.getUniqueBuildTypeName
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.readText
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidRootUtil
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class MacrobenchmarkModuleTest {
  @get:Rule val projectRule = AndroidGradleProjectRule()

  @Test
  fun uniqueBuildType_noBuildTypes() {
    val name = getUniqueBuildTypeName("unique", emptyList())
    assertThat(name).isEqualTo("unique")
  }

  @Test
  fun uniqueBuildType_oneDifferentBuildType() {
    val name = getUniqueBuildTypeName("unique", listOf("any"))
    assertThat(name).isEqualTo("unique")
  }

  @Test
  fun uniqueBuildType_hasConflict() {
    val name = getUniqueBuildTypeName("unique", listOf("unique"))
    assertThat(name).isEqualTo("unique1")
  }

  @Test
  fun uniqueBuildType_hasTwoConflicts() {
    val name = getUniqueBuildTypeName("unique", listOf("unique", "unique1"))
    assertThat(name).isEqualTo("unique2")
  }

  @Test
  fun checkProfileableAddedToTargetModule() {
    projectRule.load(TestProjectPaths.ANDROIDX_WITH_LIB_MODULE)

    val mockExecutor = mock<RecipeExecutor>()
    val targetModule = projectRule.getModule("app")

    mockExecutor.addProfileableToTargetManifest(targetModule)

    val androidFacet = AndroidFacet.getInstance(targetModule)
    assertNotNull(androidFacet)
    val primaryManifest = AndroidRootUtil.getPrimaryManifestFile(androidFacet)
    assertNotNull(primaryManifest)
    assertTrue(primaryManifest.exists())

    assertThat(primaryManifest.readText()).doesNotContain("<profileable android:shell=\"true\"")

    verify(mockExecutor).run {
      mergeXml(Mockito.contains("<profileable android:shell=\"true\""), any())
    }
  }
}
