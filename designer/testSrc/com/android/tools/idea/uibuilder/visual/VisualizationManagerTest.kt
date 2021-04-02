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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.EdtRule
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(JUnit4::class)
class VisualizationManagerTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @Before
  fun setupToolWindowManager() {
    // The HeadlessToolWindowManager doesn't record the status of ToolWindow. We create a simple one to record it.
    val toolManager = VisualizationTestToolWindowManager(projectRule.project, projectRule.fixture.testRootDisposable)
    projectRule.replaceProjectService(ToolWindowManager::class.java, toolManager)
  }

  @After
  fun tearDown() {
    // The plugin service doesn't not be disposed automatically. Dispose it manually to avoid the leakage in unit test.
    // TODO(b/180927397): Remove this when VisualizationManager is not a Project Service.
    VisualizationManager.getInstance(projectRule.project)?.let { Disposer.dispose(it) }
  }

  @Test
  fun testToolWindowExist() {
    assertNotNull(ToolWindowManager.getInstance(projectRule.project).getToolWindow(VisualizationManager.TOOL_WINDOW_ID))
  }

  @Test
  fun testAvailableWithLayoutFile() {
    // We make it visible, so when focusing layout file it would be opened automatically.
    VisualizationToolSettings.getInstance().globalState.isVisible = true

    val project = projectRule.project

    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(VisualizationManager.TOOL_WINDOW_ID)!!

    val manager = projectRule.project.getService(VisualizationManager::class.java)
    val layoutFile = projectRule.fixture.addFileToProject("res/layout/my_layout.xml", LAYOUT_FILE_TEXT)
    val ktFile = projectRule.fixture.addFileToProject("src/my_test_project/SomeFile.kt", KT_FILE_TEXT)

    // Handle post activity case.
    manager.toolWindowUpdateQueue.waitForAllExecuted(10, TimeUnit.SECONDS)
    // Not visible when there is no editor.
    assertFalse(toolWindow.isAvailable)


    WriteCommandAction.runWriteCommandAction(projectRule.project) { projectRule.fixture.openFileInEditor(layoutFile.virtualFile) }
    manager.toolWindowUpdateQueue.waitForAllExecuted(10, TimeUnit.SECONDS)
    assertTrue(toolWindow.isAvailable)


    WriteCommandAction.runWriteCommandAction(projectRule.project) { projectRule.fixture.openFileInEditor(ktFile.virtualFile) }
    manager.toolWindowUpdateQueue.waitForAllExecuted(10, TimeUnit.SECONDS)
    assertFalse(toolWindow.isAvailable)


    WriteCommandAction.runWriteCommandAction(projectRule.project) { projectRule.fixture.openFileInEditor(layoutFile.virtualFile) }
    manager.toolWindowUpdateQueue.waitForAllExecuted(10, TimeUnit.SECONDS)
    assertTrue(toolWindow.isAvailable)


    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      FileEditorManager.getInstance(project).closeFile(ktFile.virtualFile)
      FileEditorManager.getInstance(project).closeFile(layoutFile.virtualFile)
    }
    manager.toolWindowUpdateQueue.waitForAllExecuted(10, TimeUnit.SECONDS)
    assertFalse(toolWindow.isAvailable)
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
