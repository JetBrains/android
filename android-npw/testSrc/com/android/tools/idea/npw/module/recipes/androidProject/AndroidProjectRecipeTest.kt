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
package com.android.tools.idea.npw.module.recipes.androidProject

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wizard.template.DslLanguage
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import java.io.File
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

class AndroidProjectRecipeTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory().initAndroid(true)

  @Test
  fun testAndroidProjectRecipe_Declarative_NoBuildGradle() {
    val executor = mock(RecipeExecutor::class.java)
    val data = mock(ProjectTemplateData::class.java)
    val rootDir = File("/tmp/project")

    whenever(data.rootDir).thenReturn(rootDir)
    whenever(data.dslLanguage).thenReturn(DslLanguage.DCL)
    whenever(data.agpVersion).thenReturn(AgpVersion.parse("8.1.0"))
    whenever(data.gradleVersion).thenReturn(GradleVersion.version("8.0"))
    whenever(data.additionalMavenRepos).thenReturn(emptyList())

    executor.androidProjectRecipe(data, "App Title", Language.Kotlin)

    // Verify build.gradle[.kts] is NOT saved
    verify(executor, never()).save(any(), eq(rootDir.resolve("build.gradle")))
    verify(executor, never()).save(any(), eq(rootDir.resolve("build.gradle.kts")))

    // Verify settings.gradle.dcl IS saved
    verify(executor).save(any(), eq(rootDir.resolve("settings.gradle.dcl")))
  }

  @Test
  fun testAndroidProjectRecipe_Groovy_HasBuildGradle() {
    val executor = mock(RecipeExecutor::class.java)
    val data = mock(ProjectTemplateData::class.java)
    val rootDir = File("/tmp/project")

    whenever(data.rootDir).thenReturn(rootDir)
    whenever(data.dslLanguage).thenReturn(DslLanguage.GROOVY)
    whenever(data.agpVersion).thenReturn(AgpVersion.parse("8.1.0"))
    whenever(data.gradleVersion).thenReturn(GradleVersion.version("8.0"))
    whenever(data.additionalMavenRepos).thenReturn(emptyList())

    executor.androidProjectRecipe(data, "App Title", Language.Kotlin)

    // Verify build.gradle IS saved
    verify(executor).save(any(), eq(rootDir.resolve("build.gradle")))

    // Verify settings.gradle IS saved
    verify(executor).save(any(), eq(rootDir.resolve("settings.gradle")))
  }
}
