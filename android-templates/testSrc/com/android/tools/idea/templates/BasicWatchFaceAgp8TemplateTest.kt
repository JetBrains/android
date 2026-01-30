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
package com.android.tools.idea.templates

import com.android.tools.idea.npw.project.DEFAULT_KOTLIN_VERSION_FOR_NEW_PROJECTS
import com.android.tools.idea.npw.template.ModuleTemplateDataBuilder
import com.android.tools.idea.npw.template.ProjectTemplateDataBuilder
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.templates.diff.activity.ProjectDiffer
import com.android.tools.idea.templates.diff.activity.ProjectStateCustomizer
import com.android.tools.idea.templates.diff.activity.TemplateDiffTest
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wizard.template.Language
import org.junit.Rule
import org.junit.Test

/** This test is done outside of [TemplateDiffTest] to check that the template renders properly on AGP 8 */
class BasicWatchFaceAgp8TemplateTest {
  @get:Rule val projectRule = AndroidProjectRule.withAndroidModels()

  @Test
  fun testAgp8() {
    val template = TemplateResolver.getTemplateByName("Basic Watch Face")!!
    val renderer = ProjectDiffer(template, goldenDirName = "testBasicWatchFaceAgp8")

    renderer.renderProject(projectRule.project, AgpVersionSoftwareEnvironmentDescriptor.AGP_8_13, withKotlin(), withWatchFace())
  }

  private fun withKotlin(): ProjectStateCustomizer = { _: ModuleTemplateDataBuilder, projectData: ProjectTemplateDataBuilder ->
    projectData.language = Language.Kotlin
    // Use the Kotlin version for tests
    projectData.kotlinVersion = DEFAULT_KOTLIN_VERSION_FOR_NEW_PROJECTS
  }

  private fun withWatchFace(): ProjectStateCustomizer = { moduleData, _ -> moduleData.isWatchFace = true }
}
