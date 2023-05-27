/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.actions

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.tools.idea.res.ResourceFolderRegistry
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.RESOURCE_DESIGN_ASSETS_KEY
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.TestActionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.awt.datatransfer.DataFlavor

internal class CopyResourceValueActionTest {
  @get:Rule
  val androidProject = AndroidProjectRule.inMemory()

  private fun updateAction(dataContext: DataContext): Pair<CopyResourceValueAction, AnActionEvent> {
    val action = CopyResourceValueAction()
    val testActionEvent = TestActionEvent.createTestEvent(action, dataContext)

    action.update(testActionEvent)
    return action to testActionEvent
  }

  private fun getColorAsset(): DesignAsset {
    val colorsFile = androidProject.fixture.addFileToProject("res/values/colors.xml", """
      <resources>
          <color name="color2">#008577</color>
      </resources>
    """.trimIndent())
    val folderRepository = runReadAction {
      ResourceFolderRegistry.getInstance(androidProject.project)
        .get(androidProject.module.androidFacet!!, colorsFile.parent!!.parent!!.virtualFile)
    }
    val colorItem = folderRepository.getResources(ResourceNamespace.RES_AUTO, ResourceType.COLOR).values().single()
    return DesignAsset(colorsFile.virtualFile, listOf(), ResourceType.COLOR, resourceItem = colorItem)
  }

  @Test
  fun testActionIsNotAvailable() {
    assertFalse(updateAction { null }.second.presentation.isVisible)
    assertFalse(updateAction { arrayOf<DesignAsset>() }.second.presentation.isVisible)
  }

  @Test
  fun testActionNotAvailableForMultipleItems() {
    val asset = getColorAsset()
    val dataContext = DataContext { dataId ->
      if (dataId == RESOURCE_DESIGN_ASSETS_KEY.name)
        arrayOf(asset, asset)
      else null
    }
    assertFalse(updateAction(dataContext).second.presentation.isVisible)
  }

  @Test
  fun testActionIsAvailable() {
    val asset = getColorAsset()
    val dataContext = DataContext { dataId ->
      if (dataId == RESOURCE_DESIGN_ASSETS_KEY.name)
        arrayOf(asset)
      else null
    }
    val (action, event) = updateAction(dataContext)
    assertTrue(event.presentation.isVisible)
    action.actionPerformed(event)
    assertEquals("#008577", CopyPasteManager.getInstance().contents!!.getTransferData(DataFlavor.stringFlavor))
  }
}