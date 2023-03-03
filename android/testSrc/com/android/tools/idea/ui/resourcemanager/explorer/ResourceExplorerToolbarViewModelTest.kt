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
import com.android.tools.idea.ui.resourcemanager.actions.NewResourceFileAction
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.ui.resourcemanager.importer.ImportersProvider
import com.android.tools.idea.ui.resourcemanager.model.FilterOptions
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.event.ActionEvent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunsInEdt
class ResourceExplorerToolbarViewModelTest {

  @get:Rule
  var rule = AndroidProjectRule.onDisk()

  @get:Rule
  val edtRule = EdtRule()

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
    val importers = viewModel.getImportersActions().map {
      val event = TestActionEvent(DataContext.EMPTY_CONTEXT, it)
      it .update(event)
      event.presentation.text
    }.sorted()
    assertThat(importers).isEmpty()
  }

  @Test
  fun getDirectories() {
    val resFolder = rule.fixture.copyDirectoryToProject("res/", "res")
    assertThat(viewModel.directories.map { it.virtualFile }).containsExactly(resFolder)
  }

  @Test
  fun getData() {
    assertThat(viewModel.getData(PlatformCoreDataKeys.MODULE.name)).isEqualTo(rule.module)
    assertThat(viewModel.getData(CommonDataKeys.PROJECT.name)).isEqualTo(rule.project)

    val resFolder = rule.fixture.copyDirectoryToProject("res/", "res")
    val drawableDir = resFolder.findChild("drawable")
    val psiDrawableDir = PsiDirectoryFactory.getInstance(rule.project).createDirectory(drawableDir!!)
    runInEdtAndWait {
      val slowDataProvider = viewModel.getData(PlatformCoreDataKeys.BGT_DATA_PROVIDER.name) as DataProvider
      assertThat((slowDataProvider.getData(CommonDataKeys.PSI_ELEMENT.name) as PsiElement)).isEqualTo(psiDrawableDir)
    }
  }

  @Test
  fun hasResourceValueAction() {
    // For Drawable
    assertTrue(viewModel.addActions.getChildren(null).hasValueActionOfType(ResourceType.DRAWABLE))

    // For Color
    viewModel = ResourceExplorerToolbarViewModel(
      rule.module.androidFacet!!, ResourceType.COLOR, ImportersProvider(), FilterOptions.createDefault())
    assertTrue(viewModel.addActions.getChildren(null).hasValueActionOfType(ResourceType.COLOR))
  }

  @Test
  fun hasResourceFileAction() {
    rule.fixture.copyDirectoryToProject("res/", "res")
    val actions = viewModel.addActions.getChildren(null).toMutableList()

    val resourceFileActions = actions.filter { it is NewResourceFileAction }
    assertThat(resourceFileActions).hasSize(1)

    val resourceFileAction = resourceFileActions.get(0)
    val event = TestActionEvent(DataContext.EMPTY_CONTEXT, resourceFileAction)
    resourceFileAction.update(event)

    assertThat(event.presentation.text).startsWith(ResourceType.DRAWABLE.displayName)
  }
}

private fun Array<AnAction>.hasValueActionOfType(type: ResourceType): Boolean
  = filterIsInstance<NewResourceFileAction>()
  .any {
    val event = TestActionEvent(DataContext.EMPTY_CONTEXT, it)
    it.update(event)

    event.presentation.text.startsWith(type.displayName)
  }
