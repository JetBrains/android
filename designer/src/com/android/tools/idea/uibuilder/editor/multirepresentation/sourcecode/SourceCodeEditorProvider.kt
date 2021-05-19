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
import com.android.tools.idea.uibuilder.editor.multirepresentation.MultiRepresentationPreviewFileEditorState
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationProvider
import com.intellij.facet.ProjectFacetManager
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.text.QuickDefinitionProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly


private const val EP_NAME = "com.android.tools.idea.uibuilder.editor.multirepresentation.sourcecode.sourceCodePreviewRepresentationProvider"
private val PROVIDERS_EP = ExtensionPointName.create<PreviewRepresentationProvider>(EP_NAME)

/**
 * [FileEditorState] that contains the state of both the editor and the preview sides.
 */
data class SourceCodeEditorWithMultiRepresentationPreviewState(
  val parentState: FileEditorState = FileEditorState.INSTANCE,
  val editorState: FileEditorState = FileEditorState.INSTANCE,
  val previewState: MultiRepresentationPreviewFileEditorState = MultiRepresentationPreviewFileEditorState.INSTANCE) : FileEditorState {
  override fun canBeMergedWith(otherState: FileEditorState?, level: FileEditorStateLevel?): Boolean =
    otherState is SourceCodeEditorWithMultiRepresentationPreviewState &&
    otherState.editorState.canBeMergedWith(editorState, level) &&
    otherState.previewState.canBeMergedWith(previewState, level) &&
    otherState.parentState.canBeMergedWith(parentState, level)
}

/**
 * [FileEditorProvider] intended to be used with all source code files universally and therefore accepts all source code files. Creates
 * [SourceCodeEditorWithMultiRepresentationPreview] as a corresponding [FileEditor].
 */
class SourceCodeEditorProvider private constructor(private val providers: Collection<PreviewRepresentationProvider>) : FileEditorProvider, QuickDefinitionProvider, DumbAware {
  constructor() : this(PROVIDERS_EP.extensions.toList())

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
    val multiRepresentationPreview = SourceCodePreview(psiFile, textEditor.editor, providers)

    return SourceCodeEditorWithMultiRepresentationPreview(project, textEditor, multiRepresentationPreview)
  }

  override fun getEditorTypeId() = "android-source-code"

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  override fun writeState(state: FileEditorState, project: Project, targetElement: Element) {
    if (state is SourceCodeEditorWithMultiRepresentationPreviewState
        && state.previewState != MultiRepresentationPreviewFileEditorState.INSTANCE) {
      val serializedState = XmlSerializer.serializeIfNotDefault(state.previewState, null) ?: return
      targetElement.addContent(serializedState)
    }
  }

  override fun readState(sourceElement: Element, project: Project, file: VirtualFile): FileEditorState {
    if (accept(project, file) && sourceElement.children.size == 1) {
      val deserialized = XmlSerializer.deserialize(sourceElement.children.single(), MultiRepresentationPreviewFileEditorState::class.java)
      // Avoid de-serializing empty states
      if (deserialized != MultiRepresentationPreviewFileEditorState.INSTANCE) {
        return SourceCodeEditorWithMultiRepresentationPreviewState(previewState = deserialized)
      }
    }

    return FileEditorState.INSTANCE
  }

  companion object {
    @TestOnly
    fun forTesting(providers: List<PreviewRepresentationProvider>) = SourceCodeEditorProvider(providers)
  }
}