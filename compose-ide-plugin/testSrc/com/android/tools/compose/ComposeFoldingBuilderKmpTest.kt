/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.compose

import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Test for [ComposeFoldingBuilder] in a Kotlin Multiplatform project.
 *
 * The `commonMain` source set has no Android facet. [ComposeFoldingBuilder] finds the Composable annotation on the module classpath, so the
 * Modifier chain still folds.
 */
class ComposeFoldingBuilderKmpTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule(agpVersionSoftwareEnvironment = AgpVersionSoftwareEnvironmentDescriptor.AGP_8_13)

  private val myFixture: CodeInsightTestFixtureImpl
    get() = projectRule.fixture as CodeInsightTestFixtureImpl

  @Before
  fun setUp() {
    projectRule.loadProject(KOTLIN_MULTIPLATFORM_PROJECT)
  }

  @Test
  fun modifierChainFoldsInCommonMain() {
    val commonMainFile = VfsUtil.findRelativeFile(COMMON_MAIN_APP_PATH, projectRule.project.guessProjectDir())!!
    myFixture.configureFromExistingVirtualFile(commonMainFile)

    assertThat(myFixture.getFoldingDescription(false, false)).contains("<fold text='Modifier.(...)'>")
  }
}

private const val KOTLIN_MULTIPLATFORM_PROJECT = "projects/androidKotlinMultiplatformMultiPreview"
private const val COMMON_MAIN_APP_PATH = "composeApp/src/commonMain/kotlin/org/example/project/App.kt"
