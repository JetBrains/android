/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.ui

import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_TOOL_WINDOW_ID
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import com.intellij.testFramework.registerServiceInstance
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LayoutInspectorSettingsTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  private lateinit var windowManager: ToolWindowManager

  private var originalValue = true

  @Before
  fun setUp() {
    originalValue = enableLiveLayoutInspector
    val project = projectRule.project
    windowManager = MyToolWindowManager(project)
    project.registerServiceInstance(ToolWindowManager::class.java, windowManager)
  }


  @After
  fun tearDown() {
    ApplicationManager.getApplication().invokeAndWait {
      enableLiveLayoutInspector = originalValue
    }
  }

  @Test
  fun testSettingPersists() {
    ApplicationManager.getApplication().invokeAndWait {
      enableLiveLayoutInspector = false
      assertThat(enableLiveLayoutInspector).isFalse()
      assertThat(windowManager.getToolWindow("Layout Inspector")).isNull()

      enableLiveLayoutInspector = true
      assertThat(enableLiveLayoutInspector).isTrue()
      assertThat(windowManager.getToolWindow("Layout Inspector")).isNotNull()
      assertThat(windowManager.getToolWindow("Layout Inspector")!!.isAvailable).isTrue()

      enableLiveLayoutInspector = false
      assertThat(enableLiveLayoutInspector).isFalse()
      assertThat(windowManager.getToolWindow("Layout Inspector")!!.isAvailable).isFalse()
    }
  }

  private class MyToolWindowManager(val project: Project) : ToolWindowHeadlessManagerImpl(project) {
    var toolWindow: ToolWindow? = null

    override fun registerToolWindow(task: RegisterToolWindowTask): ToolWindow {
      assertThat(task.contentFactory!!.isApplicable(project)).isTrue()
      assertThat(task.id).isEqualTo(LAYOUT_INSPECTOR_TOOL_WINDOW_ID)
      toolWindow = MyMockToolWindow(project)
      return toolWindow!!
    }

    override fun getToolWindow(id: String?): ToolWindow? {
      assertThat(id).isEqualTo(LAYOUT_INSPECTOR_TOOL_WINDOW_ID)
      return toolWindow
    }
  }

  private class MyMockToolWindow(project: Project) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
    var shouldBeAvailable = true
    var visible = true

    override fun setAvailable(available: Boolean, runnable: Runnable?) {
      shouldBeAvailable = available
    }

    override fun isAvailable() = shouldBeAvailable

    override fun show(runnable: Runnable?) {
      visible = true
    }

    override fun hide(runnable: Runnable?) {
      visible = false
    }

    override fun isVisible(): Boolean {
      return visible
    }
  }
}