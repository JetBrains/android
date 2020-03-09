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
package com.android.tools.idea.common.assistant

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito

class HelpPanelToolWindowListenerTest : AndroidTestCase() {

  fun testEnsureListener() {
    assertTrue(HelpPanelToolWindowListener.projectToListener.isEmpty())
    HelpPanelToolWindowListener.registerListener(project)
    assertEquals(1, HelpPanelToolWindowListener.projectToListener.size)
  }

  fun testProjectClosed() {
    HelpPanelToolWindowListener.registerListener(project)
    val listener = HelpPanelToolWindowListener.projectToListener[project]!!

    listener.dispose()
    assertTrue(HelpPanelToolWindowListener.projectToListener.isEmpty())
  }

  fun testToolWindowUnregistered() {
    HelpPanelToolWindowListener.registerListener(project)
    val listener = HelpPanelToolWindowListener.projectToListener[project]!!

    listener.toolWindowUnregistered("test", Mockito.mock(ToolWindow::class.java))
    assertTrue(HelpPanelToolWindowListener.projectToListener.isEmpty())
  }

  fun testRemovedTwice() {
    HelpPanelToolWindowListener.registerListener(project)
    val listener = HelpPanelToolWindowListener.projectToListener[project]!!

    listener.dispose()
    listener.toolWindowUnregistered("test", Mockito.mock(ToolWindow::class.java))

    assertTrue(HelpPanelToolWindowListener.projectToListener.isEmpty())
  }
}