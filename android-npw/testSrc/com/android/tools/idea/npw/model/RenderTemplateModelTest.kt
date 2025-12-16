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
package com.android.tools.idea.npw.model

import com.android.tools.idea.npw.SDK_VERSION_FOR_NPW_TESTS
import com.android.tools.idea.npw.model.ProjectSyncInvoker.DefaultProjectSyncInvoker
import com.android.tools.idea.npw.model.RenderTemplateModel.Companion.fromFacet
import com.android.tools.idea.npw.project.DEFAULT_KOTLIN_VERSION_FOR_NEW_PROJECTS
import com.android.tools.idea.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.requestSyncAndWait
import com.android.tools.idea.testing.withCompileSdk
import com.android.tools.idea.testing.withKotlin
import com.android.tools.idea.testing.withTargetSdk
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TemplateFlag
import com.android.tools.idea.wizard.template.Thumb
import com.android.tools.idea.wizard.template.Widget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RenderTemplateModelTest {
  private val agpVersion =
    AgpVersionSoftwareEnvironmentDescriptor.AGP_81.withCompileSdk(SDK_VERSION_FOR_NPW_TESTS).withTargetSdk(SDK_VERSION_FOR_NPW_TESTS)

  @get:Rule val projectRule = AndroidGradleProjectRule(agpVersionSoftwareEnvironment = agpVersion)

  @Test
  fun composeTemplate_withIncompatibleKotlin_upgradesVersion() {
    projectRule.load(
      TestProjectPaths.ANDROIDX_SIMPLE,
      agpVersion.withKotlin("1.9.23"), // Only Kotlin 2.0.0+ versions are supported
    )
    projectRule.project.requestSyncAndWait()

    val renderTemplateModel = createRenderTemplateModel()

    var captureTemplateData: TemplateData? = null
    val fakeRecipe: Recipe = { data: TemplateData -> captureTemplateData = data }
    renderTemplateModel.newTemplate = createFakeTemplate(fakeRecipe, listOf(TemplateConstraint.Compose))

    renderTemplateModel.handleFinished()

    val moduleTemplateData = captureTemplateData as ModuleTemplateData
    assertThat(moduleTemplateData.projectTemplateData.kotlinVersion).isEqualTo(DEFAULT_KOTLIN_VERSION_FOR_NEW_PROJECTS)
  }

  @Test
  fun composeTemplate_withCompatibleKotlin_usesProjectVersion() {
    projectRule.load(TestProjectPaths.ANDROIDX_SIMPLE, agpVersion.withKotlin("2.2.0"))
    projectRule.project.requestSyncAndWait()

    val renderTemplateModel = createRenderTemplateModel()

    var captureTemplateData: TemplateData? = null
    val fakeRecipe: Recipe = { data: TemplateData -> captureTemplateData = data }
    renderTemplateModel.newTemplate = createFakeTemplate(fakeRecipe, listOf(TemplateConstraint.Compose))

    renderTemplateModel.handleFinished()

    val moduleTemplateData = captureTemplateData as ModuleTemplateData
    assertThat(moduleTemplateData.projectTemplateData.kotlinVersion).isEqualTo("2.2.0")
  }

  @Test
  fun nonComposeTemplate_usesProjectKotlinVersion() {
    projectRule.load(TestProjectPaths.ANDROIDX_SIMPLE, agpVersion.withKotlin("1.9.23"))
    projectRule.project.requestSyncAndWait()

    val renderTemplateModel = createRenderTemplateModel()

    var captureTemplateData: TemplateData? = null
    val fakeRecipe: Recipe = { data: TemplateData -> captureTemplateData = data }
    renderTemplateModel.newTemplate = createFakeTemplate(fakeRecipe, emptyList())

    renderTemplateModel.handleFinished()

    val moduleTemplateData = captureTemplateData as ModuleTemplateData
    assertThat(moduleTemplateData.projectTemplateData.kotlinVersion).isEqualTo("1.9.23")
  }

  @Test
  fun composeTemplate_noKotlinConfigured_defaultsToLatest() {
    projectRule.load(TestProjectPaths.SIMPLE_APPLICATION, agpVersion)
    projectRule.project.requestSyncAndWait()

    val renderTemplateModel = createRenderTemplateModel()

    var captureTemplateData: TemplateData? = null
    val fakeRecipe: Recipe = { data: TemplateData -> captureTemplateData = data }
    renderTemplateModel.newTemplate = createFakeTemplate(fakeRecipe, listOf(TemplateConstraint.Compose))

    renderTemplateModel.handleFinished()

    val moduleTemplateData = captureTemplateData as ModuleTemplateData
    assertThat(moduleTemplateData.projectTemplateData.kotlinVersion).isEqualTo(DEFAULT_KOTLIN_VERSION_FOR_NEW_PROJECTS)
  }

  private fun createRenderTemplateModel(): RenderTemplateModel {
    val module = projectRule.getModule("app")
    val template = GradleAndroidModuleTemplate.createDefaultModuleTemplate(projectRule.project, "")
    return fromFacet(
      module.androidFacet!!,
      "com.example",
      template,
      "command",
      DefaultProjectSyncInvoker(),
      true,
      AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext.NEW_PROJECT,
    )
  }

  private fun createFakeTemplate(recipe: Recipe, constraints: List<TemplateConstraint>): Template {
    return object : Template {
      override val name = "Template"
      override val description = "Description"
      override val documentationUrl = null

      override fun thumb() = Thumb.NoThumb

      override val widgets = emptyList<Widget<*>>()
      override val recipe = recipe
      override val uiContexts = emptyList<WizardUiContext>()
      override val minSdk = 0
      override val category = Category.Activity
      override val formFactor = FormFactor.Mobile
      override val constraints = constraints
      override val flags = emptyList<TemplateFlag>()
      override val useGenericInstrumentedTests = false
      override val useGenericLocalTests = false
    }
  }
}
