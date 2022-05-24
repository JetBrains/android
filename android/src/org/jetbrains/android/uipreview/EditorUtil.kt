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
package org.jetbrains.android.uipreview

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.ide.impl.ProjectViewSelectInPaneTarget
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

object EditorUtil {
  /**
   * Opens the specified file in the editor
   *
   * @param project The project which contains the given file.
   * @param vFile   The file to open
   */
  @JvmStatic
  fun openEditor(project: Project, vFile: VirtualFile) {
    val descriptor =
      if (vFile.fileType === XmlFileType.INSTANCE && AndroidEditorSettings.getInstance().globalState.isPreferXmlEditor) {
        OpenFileDescriptor(project, vFile, 0)
      }
      else {
        OpenFileDescriptor(project, vFile)
      }
    FileEditorManager.getInstance(project).openEditor(descriptor, true)
  }

  /**
   * Selects the specified file in the project view.
   * **Note:** Must be called with read access.
   *
   * @param project the project
   * @param file    the file to select
   */
  @JvmStatic
  fun selectEditor(project: Project, file: VirtualFile) {
    ApplicationManager.getApplication().assertReadAccessAllowed()

    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
    val currentPane = ProjectView.getInstance(project).currentProjectViewPane ?: return

    ProjectViewSelectInPaneTarget(project, currentPane, true).select(psiFile, false)
  }
}