/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.sampledata

import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.util.toPathString
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiPlainTextFile

const val SAMPLE_DATA_CSV_EDITOR_ID: String = "sample-data-csv-editor"

class CsvEditorProvider : FileEditorProvider, DumbAware {
  /**
   * FileEditorProvider ID for the CSV sample data editor
   */
  override fun accept(project: Project, virtualFile: VirtualFile): Boolean {
    val psiFile = AndroidPsiUtils.getPsiFileSafely(project, virtualFile)

    if (psiFile is PsiPlainTextFile && "csv" == virtualFile.extension?.toLowerCase()) {
      val sampleDataDirectory = AndroidPsiUtils.getModuleSafely(psiFile)?.getModuleSystem()?.getSampleDataDirectory()
      return sampleDataDirectory != null
             && virtualFile.toPathString().let { it.startsWith(sampleDataDirectory) && it != sampleDataDirectory }
    }

    return false
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor =
      CsvEditor(AndroidPsiUtils.getPsiFileSafely(project, file) as PsiPlainTextFile, project)

  override fun getEditorTypeId(): String = SAMPLE_DATA_CSV_EDITOR_ID
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR
}
