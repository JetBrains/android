/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.testFramework.EdtRule
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VisualizationToolWindowFactoryTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @Test
  fun testToolWindowIsRegistered() {
    // VisualizationTestToolWindowManager loads the tool window from extension point.
    val toolManager = VisualizationTestToolWindowManager(projectRule.project, projectRule.fixture.testRootDisposable)
    projectRule.replaceProjectService(ToolWindowManager::class.java, toolManager)
    assertNotNull(ToolWindowManager.getInstance(projectRule.project).getToolWindow(VisualizationToolWindowFactory.TOOL_WINDOW_ID))
  }

  @Test
  fun testAvailableWhenOpeningProject() {
    val factory = VisualizationToolWindowFactory()

    // Not available when no file is opened.
    assertFalse(factory.shouldBeAvailable(projectRule.project))

    // Available when a layout file is opened.
    val layoutFile = projectRule.fixture.addFileToProject("res/layout/my_layout.xml", LAYOUT_FILE_TEXT)
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      projectRule.fixture.openFileInEditor(layoutFile.virtualFile)
    }
    assertTrue(factory.shouldBeAvailable(projectRule.project))

    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      FileEditorManager.getInstance(projectRule.project).closeFile(layoutFile.virtualFile)
    }

    // Not available when there is no opened layout file.
    val ktFile = projectRule.fixture.addFileToProject("src/my_test_project/SomeFile.kt", KT_FILE_TEXT)
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      projectRule.fixture.openFileInEditor(ktFile.virtualFile)
    }
    assertFalse(factory.shouldBeAvailable(projectRule.project))
  }

  @Test
  fun testAvailableWhenSwitchingFile() {
    val toolWindow = VisualizationTestToolWindow(projectRule.project)
    val factory = VisualizationToolWindowFactory()
    WriteCommandAction.runWriteCommandAction(projectRule.project) { factory.init(toolWindow) }

    val layoutFile = projectRule.fixture.addFileToProject("res/layout/my_layout.xml", LAYOUT_FILE_TEXT)
    val ktFile = projectRule.fixture.addFileToProject("src/my_test_project/SomeFile.kt", KT_FILE_TEXT)


    WriteCommandAction.runWriteCommandAction(projectRule.project) { projectRule.fixture.openFileInEditor(layoutFile.virtualFile) }
    assertTrue(toolWindow.isAvailable)

    WriteCommandAction.runWriteCommandAction(projectRule.project) { projectRule.fixture.openFileInEditor(ktFile.virtualFile) }
    assertFalse(toolWindow.isAvailable)

    WriteCommandAction.runWriteCommandAction(projectRule.project) { projectRule.fixture.openFileInEditor(layoutFile.virtualFile) }
    assertTrue(toolWindow.isAvailable)

    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      FileEditorManager.getInstance(projectRule.project).closeFile(layoutFile.virtualFile)
      FileEditorManager.getInstance(projectRule.project).closeFile(ktFile.virtualFile)
    }
    assertFalse(toolWindow.isAvailable)
  }

  @Test
  fun testAvailableWhenClosingFile() {
    val toolWindow = VisualizationTestToolWindow(projectRule.project)
    val factory = VisualizationToolWindowFactory()
    factory.init(toolWindow)

    val layoutFile = projectRule.fixture.addFileToProject("res/layout/my_layout.xml", LAYOUT_FILE_TEXT)
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      projectRule.fixture.openFileInEditor(layoutFile.virtualFile)
    }
    assertTrue(toolWindow.isAvailable)

    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      FileEditorManager.getInstance(projectRule.project).closeFile(layoutFile.virtualFile)
    }
    assertFalse(toolWindow.isAvailable)
  }

  @Test
  fun testAvailableWhenEditorIsOpenedBeforeInit() {
    val toolWindow = VisualizationTestToolWindow(projectRule.project)
    val factory = VisualizationToolWindowFactory()

    val layoutFile = projectRule.fixture.addFileToProject("res/layout/my_layout.xml", LAYOUT_FILE_TEXT)
    WriteCommandAction.runWriteCommandAction(projectRule.project) { projectRule.fixture.openFileInEditor(layoutFile.virtualFile) }

    factory.init(toolWindow)
    assertFalse(toolWindow.isAvailable)

    projectRule.project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowsRegistered(
      listOf(VisualizationToolWindowFactory.TOOL_WINDOW_ID), ToolWindowManager.getInstance(projectRule.project)
    )

    assertTrue(toolWindow.isAvailable)
  }
}

@Language("kotlin")
private const val KT_FILE_TEXT = """
package my_test_project
object SomeFile
"""

@Language("xml")
private const val LAYOUT_FILE_TEXT = """<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="match_parent"
  android:layout_height="match_parent" />"""
