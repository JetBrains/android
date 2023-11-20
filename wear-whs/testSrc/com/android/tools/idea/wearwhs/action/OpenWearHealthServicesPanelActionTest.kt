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
package com.android.tools.idea.wearwhs.action

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.registerServiceInstance
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OpenWearHealthServicesPanelActionTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var fakeToolWindowManager: ToolWindowManager
  private lateinit var fakeWhsToolWindow: ToolWindow

  @Before
  fun setUp() {
    StudioFlags.SYNTHETIC_HAL_PANEL.override(true)

    fakeWhsToolWindow = object : ToolWindowHeadlessManagerImpl.MockToolWindow(projectRule.project) {
      var visible = false

      override fun show() {
        visible = true
      }

      override fun isVisible(): Boolean {
        return visible
      }
    }

    fakeToolWindowManager =
      object : ToolWindowHeadlessManagerImpl(projectRule.project) {
        override fun getToolWindow(id: String?): ToolWindow {
          if (id == "Wear Health Services") {
            return fakeWhsToolWindow
          }
          return MockToolWindow(projectRule.project)
        }
      }

    projectRule.project.registerServiceInstance(ToolWindowManager::class.java, fakeToolWindowManager)
  }

  @Test
  fun `OpenWearHealthServicesPanelAction results in WearHealthServicesToolWindow being visible`() {
    val action = OpenWearHealthServicesPanelAction()
    val dataContext = MapDataContext(mapOf(CommonDataKeys.PROJECT to projectRule.project))
    val actionEvent = AnActionEvent(
      null, dataContext, "", Presentation(), ActionManager.getInstance(), 0
    )

    assert(!fakeWhsToolWindow.isVisible)

    action.actionPerformed(actionEvent)

    assert(fakeWhsToolWindow.isVisible)
  }
}