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

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.npw.project.DEFAULT_KOTLIN_VERSION_FOR_NEW_PROJECTS
import com.android.tools.idea.npw.template.ModuleTemplateDataBuilder
import com.android.tools.idea.npw.template.ProjectTemplateDataBuilder
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.templates.diff.TemplateDiffTestUtils.getPinnedAgpVersion
import com.android.tools.idea.templates.diff.activity.ProjectDiffer
import com.android.tools.idea.templates.diff.activity.ProjectStateCustomizer
import com.android.tools.idea.templates.diff.activity.TemplateDiffTest
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wizard.template.Language
import org.junit.Rule
import org.junit.Test

/**
 * This test is done outside of [TemplateDiffTest] as it does not support API 36 yet (at time of
 * writing the test).
 *
 * It is also used to check that the template renders for API levels < 36 once API 36 is supported.
 */
class ComposeWearActivityTemplateTest {
  @get:Rule val projectRule = AndroidProjectRule.withAndroidModels()

  private val withSpecificKotlin: ProjectStateCustomizer =
    withKotlin(RenderTemplateModel.getComposeKotlinVersion())

  @Test
  fun testApi35() {
    val template = TemplateResolver.getTemplateByName("Empty Wear App")!!
    val renderer = ProjectDiffer(template, goldenDirName = "testNewComposeWearActivity")

    renderer.renderProject(
      projectRule.project,
      getPinnedAgpVersion(),
      withSpecificKotlin,
      withApi(35),
    )
  }

  @Test
  fun testApi36() {
    val template = TemplateResolver.getTemplateByName("Empty Wear App")!!
    val renderer = ProjectDiffer(template, goldenDirName = "testNewComposeWearActivityApi36")

    renderer.renderProject(
      projectRule.project,
      getPinnedAgpVersion(),
      withSpecificKotlin,
      withApi(36),
    )
  }

  private fun withApi(api: Int): ProjectStateCustomizer = { moduleData, projectData ->
    val apiVersion = AndroidVersion(api, 0)
    moduleData.apis =
      moduleData.apis!!.copy(buildApi = apiVersion, targetApi = apiVersion.majorVersion)
  }

  private fun withKotlin(
    kotlinVersion: String = DEFAULT_KOTLIN_VERSION_FOR_NEW_PROJECTS
  ): ProjectStateCustomizer =
    { _: ModuleTemplateDataBuilder, projectData: ProjectTemplateDataBuilder ->
      projectData.language = Language.Kotlin
      // Use the Kotlin version for tests
      projectData.kotlinVersion = kotlinVersion
    }
}
