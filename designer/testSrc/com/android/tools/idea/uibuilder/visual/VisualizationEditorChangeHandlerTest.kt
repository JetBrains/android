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

import com.android.resources.ResourceFolderType
import com.android.tools.idea.res.getFolderType
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.testFramework.EdtRule
import javax.swing.JComponent
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class VisualizationEditorChangeHandlerTest {

  @JvmField @Rule val projectRule = AndroidProjectRule.inMemory()

  @JvmField @Rule val edtRule = EdtRule()

  @Test
  fun testEditorChange() {
    val handler = SyncVisualizationEditorChangeHandler(TestVisualizationContentProvider)
    val layoutFile =
      projectRule.fixture.addFileToProject("res/layout/my_layout.xml", LAYOUT_FILE_TEXT)
    val ktFile =
      projectRule.fixture.addFileToProject("src/my_test_project/SomeFile.kt", KT_FILE_TEXT)

    // The initial availability of tool window is false because there is no editor.
    val toolWindow = VisualizationTestToolWindow(projectRule.project).apply { isAvailable = false }

    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      projectRule.fixture.openFileInEditor(layoutFile.virtualFile)
    }
    handler.onFileEditorChange(
      FileEditorManager.getInstance(projectRule.project).selectedEditor,
      projectRule.project,
      toolWindow,
    )
    assertTrue(toolWindow.isAvailable)

    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      projectRule.fixture.openFileInEditor(ktFile.virtualFile)
    }
    handler.onFileEditorChange(
      FileEditorManager.getInstance(projectRule.project).selectedEditor,
      projectRule.project,
      toolWindow,
    )
    assertFalse(toolWindow.isAvailable)

    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      projectRule.fixture.openFileInEditor(ktFile.virtualFile)
    }
    handler.onFileEditorChange(null, projectRule.project, toolWindow)
    assertFalse(toolWindow.isAvailable)
  }

  @Test
  fun testFileClose() {
    val handler = SyncVisualizationEditorChangeHandler(TestVisualizationContentProvider)
    val layoutFile =
      projectRule.fixture.addFileToProject("res/layout/my_layout.xml", LAYOUT_FILE_TEXT)

    // The initial availability of tool window is false because there is no editor.
    val toolWindow = VisualizationTestToolWindow(projectRule.project).apply { isAvailable = false }

    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      projectRule.fixture.openFileInEditor(layoutFile.virtualFile)
    }
    handler.onFileEditorChange(
      FileEditorManager.getInstance(projectRule.project).selectedEditor,
      projectRule.project,
      toolWindow,
    )
    assertTrue(toolWindow.isAvailable)

    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      FileEditorManager.getInstance(projectRule.project).closeFile(layoutFile.virtualFile)
    }
    handler.onFileClose(
      FileEditorManager.getInstance(projectRule.project),
      toolWindow,
      layoutFile.virtualFile,
    )
    assertFalse(toolWindow.isAvailable)
  }
}

object TestVisualizationContentProvider : VisualizationContentProvider {
  override fun createVisualizationForm(
    project: Project,
    toolWindow: ToolWindow,
  ): VisualizationContent {
    val panel = object : JComponent(), VisualizationContent by TestVisualizationContent() {}
    with(toolWindow.contentManager) {
      val content = factory.createContent(panel, "Test Validation Tool", true)
      addDataProvider { if (VisualizationContent.VISUALIZATION_CONTENT.`is`(it)) panel else null }
      val context = (DataManager.getInstance() as HeadlessDataManager).dataContext
      (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider {
        if (VisualizationContent.VISUALIZATION_CONTENT.`is`(it)) panel else context.getData(it)
      }

      addContent(content)
    }
    return panel
  }
}

class TestVisualizationContent : VisualizationContent {

  private var currentConfigurationSet: ConfigurationSet = ConfigurationSet.WindowSizeDevices

  override fun setNextEditor(editor: FileEditor): Boolean =
    getFolderType(editor.file) == ResourceFolderType.LAYOUT

  override fun fileClosed(editorManager: FileEditorManager, file: VirtualFile) = Unit

  override fun getConfigurationSet(): ConfigurationSet = currentConfigurationSet

  override fun setConfigurationSet(configurationSet: ConfigurationSet) {
    currentConfigurationSet = configurationSet
  }

  override fun activate() = Unit

  override fun deactivate() = Unit

  override fun dispose() = Unit
}

@Language("kotlin")
private const val KT_FILE_TEXT =
  """
package my_test_project
object SomeFile
"""

@Language("xml")
private const val LAYOUT_FILE_TEXT =
  """<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="match_parent"
  android:layout_height="match_parent" />"""
