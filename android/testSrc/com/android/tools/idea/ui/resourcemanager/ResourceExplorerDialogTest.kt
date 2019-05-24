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
package com.android.tools.idea.ui.resourcemanager

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.explorer.AssetListView
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerView
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.WaitFor
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent

class ResourceExplorerDialogTest {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  private lateinit var pickerDialog: ResourceExplorerDialog

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = getTestDataDirectory()
    projectRule.fixture.copyDirectoryToProject("res/", "res/")
    pickerDialog = createResourcePickerDialog()
  }

  @After
  fun tearDown() {
    runInEdtAndWait { Disposer.dispose(pickerDialog.disposable) }
  }

  @Test
  fun selectResource() {
    val explorerView = UIUtil.findComponentOfType(pickerDialog.resourceExplorerPanel, ResourceExplorerView::class.java)!!
    val list = UIUtil.findComponentOfType(explorerView, AssetListView::class.java)!!
    val point = list.indexToLocation(0)
    // Simulate double clicking on an asset.
    simulateMouseClick(list, point)
    assertThat(pickerDialog.resourceName).isEqualTo("@drawable/png")
  }

  private fun createResourcePickerDialog(): ResourceExplorerDialog {
    var explorerDialog: ResourceExplorerDialog? = null
    runInEdtAndWait {
      explorerDialog = ResourceExplorerDialog(AndroidFacet.getInstance(projectRule.module)!!)
    }
    assertThat(explorerDialog).isNotNull()
    explorerDialog?.let { view ->
      val explorerView = UIUtil.findComponentOfType(view.resourceExplorerPanel, ResourceExplorerView::class.java)!!

      val waitForAssetListToBeCreated = object : WaitFor(1000) {
        public override fun condition() = UIUtil.findComponentOfType(explorerView, AssetListView::class.java) != null
      }
      assertThat(waitForAssetListToBeCreated.isConditionRealized).isEqualTo(true)
    }
    return explorerDialog!!
  }

  private fun simulateMouseClick(component: JComponent, point: Point) {
    runInEdtAndWait {
      component.mouseListeners.forEach { mouseListener ->
        mouseListener.mouseClicked(
          MouseEvent(component, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), InputEvent.BUTTON1_MASK, point.x, point.y, 2, false)
        )
      }
    }
  }
}