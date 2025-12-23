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
package com.android.tools.idea.npw.actions

import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.createAndroidProjectBuilderForDefaultTestProjectStructure
import com.android.tools.idea.wizard.model.ModelWizard
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiManager
import java.awt.Dimension
import java.io.File
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AndroidAssetStudioActionTest {

  @get:Rule
  val projectRule =
    AndroidProjectRule.withAndroidModels(
      AndroidModuleModelBuilder(
        ":",
        "debug",
        createAndroidProjectBuilderForDefaultTestProjectStructure(),
      )
    )

  @Test
  fun testUpdateWithModuleFromLocation() {
    val project = projectRule.project

    // Use paths that match the default test project structure (legacy/IntelliJ style likely)
    // based on debug output showing 'res' at root.
    projectRule.fixture.addFileToProject(
      "AndroidManifest.xml",
      "<manifest package=\"com.example\"/>",
    )
    val stringsVirtual =
      projectRule.fixture
        .addFileToProject("res/values/strings.xml", "<resources></resources>")
        .virtualFile
    projectRule.fixture.addFileToProject("src/Main.java", "")

    ApplicationManager.getApplication().runReadAction {
      // Ensure module is found from the file (This validates the core fix: ability to resolve
      // module from file location)
      val foundModule = ModuleUtilCore.findModuleForFile(stringsVirtual, project)
      assertThat(foundModule).isNotNull()
      val facet = AndroidFacet.getInstance(foundModule!!)
      assertThat(facet).isNotNull()

      var createWizardCalled = false
      val action =
        object : AndroidAssetStudioAction("Test", "Test Description") {
          override fun createWizard(
            facet: AndroidFacet,
            template: NamedModuleTemplate,
            resFolder: File,
          ): ModelWizard {
            createWizardCalled = true
            return mock(ModelWizard::class.java)
          }

          override fun showWizard(wizard: ModelWizard, facet: AndroidFacet) {
            // Nop for this test
          }

          override val wizardMinimumSize: Dimension = Dimension(0, 0)
          override val wizardPreferredSize: Dimension = Dimension(0, 0)
        }

      val ideView = mock(IdeView::class.java)
      // No module provided to verify it is resolved from VIRTUAL_FILE
      val dataContext =
        SimpleDataContext.builder()
          .add(CommonDataKeys.PROJECT, project)
          .add(CommonDataKeys.VIRTUAL_FILE, stringsVirtual)
          .add(LangDataKeys.IDE_VIEW, ideView)
          .build()

      val psiDir = PsiManager.getInstance(project).findDirectory(stringsVirtual.parent)
      assertThat(psiDir).isNotNull()
      `when`(ideView.directories).thenReturn(arrayOf(psiDir!!))

      val event = AnActionEvent.createEvent(dataContext, null, "menu", ActionUiKind.POPUP, null)

      // Verify that the action shows and it works on click.
      action.update(event)
      assertThat(event.presentation.isVisible).isTrue()

      action.actionPerformed(event)
      assertThat(createWizardCalled).isTrue()
    }
  }

  @Test
  fun testUpdateDisabledWhenMissingInfo() {
    val project = projectRule.project
    projectRule.fixture.addFileToProject(
      "AndroidManifest.xml",
      "<manifest package=\"com.example\"/>",
    )
    val stringsVirtual =
      projectRule.fixture
        .addFileToProject("res/values/strings.xml", "<resources></resources>")
        .virtualFile

    ApplicationManager.getApplication().runReadAction {
      val action =
        object : AndroidAssetStudioAction("Test", "Test Description") {
          override fun createWizard(
            facet: AndroidFacet,
            template: NamedModuleTemplate,
            resFolder: File,
          ): ModelWizard = mock(ModelWizard::class.java)

          override fun showWizard(wizard: ModelWizard, facet: AndroidFacet) {}

          override val wizardMinimumSize: Dimension = Dimension(0, 0)
          override val wizardPreferredSize: Dimension = Dimension(0, 0)
        }

      val ideView = mock(IdeView::class.java)
      val psiDir = PsiManager.getInstance(project).findDirectory(stringsVirtual.parent)
      `when`(ideView.directories).thenReturn(arrayOf(psiDir!!))

      // 1. Missing Project
      var dataContext =
        SimpleDataContext.builder()
          .add(CommonDataKeys.VIRTUAL_FILE, stringsVirtual)
          .add(LangDataKeys.IDE_VIEW, ideView)
          .build()
      var event = AnActionEvent.createEvent(dataContext, null, "menu", ActionUiKind.POPUP, null)
      action.update(event)
      assertThat(event.presentation.isVisible).isFalse()

      // 2. Missing Virtual File
      dataContext =
        SimpleDataContext.builder()
          .add(CommonDataKeys.PROJECT, project)
          .add(LangDataKeys.IDE_VIEW, ideView)
          .build()
      event = AnActionEvent.createEvent(dataContext, null, "menu", ActionUiKind.POPUP, null)
      action.update(event)
      assertThat(event.presentation.isVisible).isFalse()

      // 3. Missing IdeView
      dataContext =
        SimpleDataContext.builder()
          .add(CommonDataKeys.PROJECT, project)
          .add(CommonDataKeys.VIRTUAL_FILE, stringsVirtual)
          .build()
      event = AnActionEvent.createEvent(dataContext, null, "menu", ActionUiKind.POPUP, null)
      action.update(event)
      assertThat(event.presentation.isVisible).isFalse()

      // 4. IdeView with empty directories
      val emptyIdeView = mock(IdeView::class.java)
      `when`(emptyIdeView.directories).thenReturn(emptyArray())
      dataContext =
        SimpleDataContext.builder()
          .add(CommonDataKeys.PROJECT, project)
          .add(CommonDataKeys.VIRTUAL_FILE, stringsVirtual)
          .add(LangDataKeys.IDE_VIEW, emptyIdeView)
          .build()
      event = AnActionEvent.createEvent(dataContext, null, "menu", ActionUiKind.POPUP, null)
      action.update(event)
      assertThat(event.presentation.isVisible).isFalse()
    }
  }
}
