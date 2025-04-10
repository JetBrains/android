/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.npw.template

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.npw.model.ProjectSyncInvoker.DefaultProjectSyncInvoker
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.npw.model.RenderTemplateModel.Companion.fromFacet
import com.android.tools.idea.npw.project.getModuleTemplates
import com.android.tools.idea.observable.BatchInvoker
import com.android.tools.idea.observable.TestInvokeStrategy
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.createAndroidProjectBuilderForDefaultTestProjectStructure
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.LAYOUT
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.PACKAGE
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.WizardUiContext
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext.MENU_GALLERY
import com.intellij.openapi.util.Disposer
import javax.swing.JLabel
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class ConfigureTemplateParametersStepTest {
  private lateinit var step: ConfigureTemplateParametersStep

  @get:Rule
  val projectRule =
    AndroidProjectRule.withAndroidModel(createAndroidProjectBuilderForDefaultTestProjectStructure())
      .onEdt()

  private lateinit var facet: AndroidFacet
  private val myInvokeStrategy = TestInvokeStrategy()

  @Before
  @Throws(Exception::class)
  fun setUp() {
    facet = AndroidFacet.getInstance(projectRule.projectRule.module)!!
    step = ConfigureTemplateParametersStep(mock(), "", mock())
    BatchInvoker.setOverrideStrategy(myInvokeStrategy)
  }

  @After
  @Throws(Exception::class)
  fun tearDown() {
    BatchInvoker.clearOverrideStrategy()
    Disposer.dispose(step)
  }

  @Test
  fun packageConstraintsAreValidatedBeforeClass() {
    val param1 = mock<StringParameter>() // e.g. Activity Name
    whenever(param1.isVisibleAndEnabled).thenReturn(true)
    whenever(param1.constraints).thenReturn(listOf(CLASS, UNIQUE, NONEMPTY))
    val param2 = mock<StringParameter>() // e.g. Layout Name when generate layout is unchecked
    whenever(param2.isVisibleAndEnabled).thenReturn(false)
    whenever(param2.constraints).thenReturn(listOf(LAYOUT, UNIQUE, NONEMPTY))
    val param3 = mock<StringParameter>() // e.g. Launcher Activity
    whenever(param3.isVisibleAndEnabled).thenReturn(true)
    whenever(param3.constraints).thenReturn(listOf())
    val param4 = mock<StringParameter>() // e.g. Package Name
    whenever(param4.isVisibleAndEnabled).thenReturn(true)
    whenever(param4.constraints).thenReturn(listOf(PACKAGE))

    val actual = step.getSortedStringParametersForValidation(listOf(param1, param2, param3, param4))

    assertEquals(listOf(param4, param1, param3), actual)
  }

  @Test
  fun targetSourceSetSelector_addedWhenMultipleModuleTemplatesAvailable() {
    val moduleTemplates = facet.getModuleTemplates(null)
    assertTrue(moduleTemplates.size > 1)

    val templateModel = createTemplate("Basic Views Activity", moduleTemplates)
    val modelWizard = createTemplateWizard(templateModel, moduleTemplates)
    val fakeUI = FakeUi(modelWizard.contentPanel)

    val targetSourceSetSelector =
      checkNotNull(fakeUI.findComponent<JLabel> { it.text.contains("Target Source Set") })
    assertTrue(fakeUI.isShowing(targetSourceSetSelector))
  }

  @Test
  fun targetSourceSetSelector_notAddedWhenThereIsASingleModuleTemplate() {
    val moduleTemplates = facet.getModuleTemplates(null).take(1)
    assertTrue(moduleTemplates.size == 1)

    val templateModel = createTemplate("Basic Views Activity", moduleTemplates)
    val modelWizard = createTemplateWizard(templateModel, moduleTemplates)
    val fakeUI = FakeUi(modelWizard.contentPanel)

    assertNull(fakeUI.findComponent<JLabel> { it.text.contains("Target Source Set") })
  }

  @Test
  fun targetSourceSetSelector_notAddedIfParameterDisabled() {
    val moduleTemplates = facet.getModuleTemplates(null)
    assertTrue(moduleTemplates.size > 1)

    val templateModel = createTemplate("Journey File", moduleTemplates)
    val modelWizard =
      createTemplateWizard(templateModel, moduleTemplates, showTargetSourceSetPicker = false)
    val fakeUI = FakeUi(modelWizard.contentPanel)

    assertNull(fakeUI.findComponent<JLabel> { it.text.contains("Target Source Set") })
  }

  private fun createTemplate(
    templateName: String,
    moduleTemplates: List<NamedModuleTemplate>,
  ): RenderTemplateModel {
    val templateModel =
      fromFacet(
        facet,
        "",
        moduleTemplates[0],
        "New activity",
        DefaultProjectSyncInvoker(),
        true,
        MENU_GALLERY,
      )
    val newActivity =
      TemplateResolver.getAllTemplates()
        .filter { WizardUiContext.MenuEntry in it.uiContexts }
        .find { it.name == templateName }
    templateModel.newTemplate = newActivity!!

    return templateModel
  }

  private fun createTemplateWizard(
    templateModel: RenderTemplateModel,
    moduleTemplates: List<NamedModuleTemplate>,
    showTargetSourceSetPicker: Boolean = true,
  ): ModelWizard {
    val wizardBuilder = ModelWizard.Builder()
    wizardBuilder.addStep(
      ConfigureTemplateParametersStep(
        templateModel,
        "Add new Activity test",
        moduleTemplates,
        showTargetSourceSetPicker,
      )
    )

    val modelWizard = wizardBuilder.build()
    Disposer.register(projectRule.project, modelWizard)

    myInvokeStrategy.updateAllSteps()

    return modelWizard
  }
}
