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
package com.android.tools.idea.uibuilder.editor.multirepresentation.sourcecode

import com.android.tools.idea.flags.StudioFlags.NELE_SOURCE_CODE_EDITOR
import com.intellij.facet.ProjectFacetManager
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.QuickDefinitionProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.android.facet.AndroidFacet

/**
 * [FileEditorProvider] intended to be used with all source code files universally and therefore accepts all source code files. Creates
 * [SourceCodeEditorWithMultiRepresentationPreview] as a corresponding [FileEditor].
 */
class SourceCodeEditorProvider : FileEditorProvider, QuickDefinitionProvider, DumbAware {
  private val LOG = Logger.getInstance(SourceCodeEditorProvider::class.java)

  override fun accept(project: Project, file: VirtualFile): Boolean =
    ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID) &&
    !LightEdit.owns(project) && NELE_SOURCE_CODE_EDITOR.get() && file.hasSourceFileExtension()

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    if (LOG.isDebugEnabled) {
      LOG.debug("createEditor file=${file.path}")
    }

    val psiFile = PsiManager.getInstance(project).findFile(file)!!

    val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
    val multiRepresentationPreview = SourceCodePreview(psiFile)

    return SourceCodeEditorWithMultiRepresentationPreview(textEditor, multiRepresentationPreview)
  }

  override fun getEditorTypeId() = "android-source-code"

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}