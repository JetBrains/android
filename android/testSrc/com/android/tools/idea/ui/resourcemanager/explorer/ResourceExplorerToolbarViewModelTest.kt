/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.explorer

import com.android.resources.ResourceType
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.actions.NewResourceValueAction
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.ui.resourcemanager.importer.ImportersProvider
import com.android.tools.idea.ui.resourcemanager.model.FilterOptions
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.actions.CreateResourceActionBase
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceExplorerToolbarViewModelTest {

  @get:Rule
  var rule = AndroidProjectRule.onDisk()

  private lateinit var viewModel: ResourceExplorerToolbarViewModel

  @Before
  fun setUp() {
    viewModel = ResourceExplorerToolbarViewModel(
      rule.module.androidFacet!!, ResourceType.DRAWABLE, ImportersProvider(), FilterOptions.createDefault())
    rule.fixture.testDataPath = getTestDataDirectory()
  }

  @Test
  fun getCurrentModuleName() {
    assertEquals(rule.module.name, viewModel.currentModuleName)
  }

  @Test
  fun getImportersActions() {
    val importers = viewModel.getImportersActions().map { it.templatePresentation.text }.sorted()
    assertThat(importers).isEmpty()
  }

  @Test
  fun getDirectories() {
    val resFolder = rule.fixture.copyDirectoryToProject("res/", "res")
    assertThat(viewModel.directories.map { it.virtualFile }).containsExactly(resFolder)
  }

  @Test
  fun getData() {
    assertThat(viewModel.getData(LangDataKeys.MODULE.name)).isEqualTo(rule.module)
    assertThat(viewModel.getData(CommonDataKeys.PROJECT.name)).isEqualTo(rule.project)

    val resFolder = rule.fixture.copyDirectoryToProject("res/", "res")
    val drawableDir = resFolder.findChild("drawable")
    val psiDrawableDir = PsiDirectoryFactory.getInstance(rule.project).createDirectory(drawableDir!!)
    runInEdtAndWait { assertThat((viewModel.getData(CommonDataKeys.PSI_ELEMENT.name) as PsiElement)).isEqualTo(psiDrawableDir) }
  }

  @Test
  fun resourceValueAction() {
    // For Drawable
    assertFalse(viewModel.addActions.getChildren(null).hasValueActionOfType(ResourceType.DRAWABLE))

    // For Color
    viewModel = ResourceExplorerToolbarViewModel(
      rule.module.androidFacet!!, ResourceType.COLOR, ImportersProvider(), FilterOptions.createDefault())
    assertTrue(viewModel.addActions.getChildren(null).hasValueActionOfType(ResourceType.COLOR))
  }

  @Test
  fun enabledResourceFileAction() {
    rule.fixture.copyDirectoryToProject("res/", "res")
    val actions = viewModel.addActions.getChildren(null).toMutableList()
    // Filter out non create resource actions.
    actions.removeIf { it !is CreateResourceActionBase }
    // FIXME: Have to remove the default CreateResourceFileAction (assumed to be at the first index), in reality it should not be enabled
    //  but it returns as such in this test.
    actions.removeAt(0)
    assertThat(actions.size).isGreaterThan(1)
    val event = AnActionEvent(
      null, viewModel::getData, "", viewModel.addActions.templatePresentation.clone(), ActionManager.getInstance(), 0)
    var hasDrawableAction = false
    runInEdtAndWait {
      actions.forEach { action ->
        action.update(event)
        if (action.templatePresentation.text?.startsWith(ResourceType.DRAWABLE.displayName) == true) {
          // Search for the create resource action for Drawables and confirm that is enabled.
          hasDrawableAction = true
          assertTrue(event.presentation.isEnabled)
        } else {
          assertFalse(event.presentation.isEnabled)
        }
      }
    }
    assertTrue(hasDrawableAction)
  }
}

private fun Array<AnAction>.hasValueActionOfType(type: ResourceType): Boolean
  = any { (it is NewResourceValueAction) && (it.templatePresentation.text.startsWith(type.displayName)) }
