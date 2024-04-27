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

import com.android.tools.idea.editors.sourcecode.isSourceFileType
import com.android.tools.idea.isAndroidEnvironment
import com.android.tools.idea.uibuilder.editor.multirepresentation.MULTI_PREVIEW_STATE_TAG
import com.android.tools.idea.uibuilder.editor.multirepresentation.MultiRepresentationPreviewFileEditorState
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationProvider
import com.intellij.configurationStore.serialize
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.impl.text.QuickDefinitionProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Attribute
import org.jdom.Element
import org.jetbrains.annotations.TestOnly

private const val EP_NAME =
  "com.android.tools.idea.uibuilder.editor.multirepresentation.sourcecode.sourceCodePreviewRepresentationProvider"
private val PROVIDERS_EP = ExtensionPointName.create<PreviewRepresentationProvider>(EP_NAME)
private const val EDITOR_STATE_TAG = "editor-state"
private const val SELECTED_LAYOUT_ATTRIBUTE = "selected-layout"

/** [FileEditorState] that contains the state of both the editor and the preview sides. */
data class SourceCodeEditorWithMultiRepresentationPreviewState(
  val parentState: FileEditorState = FileEditorState.INSTANCE,
  val editorState: FileEditorState = FileEditorState.INSTANCE,
  val previewState: MultiRepresentationPreviewFileEditorState =
    MultiRepresentationPreviewFileEditorState.INSTANCE,
  val selectedLayout: TextEditorWithPreview.Layout? = null
) : FileEditorState {
  override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean =
    otherState is SourceCodeEditorWithMultiRepresentationPreviewState &&
      otherState.editorState.canBeMergedWith(editorState, level) &&
      otherState.previewState.canBeMergedWith(previewState, level) &&
      otherState.parentState.canBeMergedWith(parentState, level) &&
      otherState.selectedLayout == selectedLayout
}

/**
 * [FileEditorProvider] intended to be used with all source code files universally and therefore
 * accepts all source code files. Creates [SourceCodeEditorWithMultiRepresentationPreview] as a
 * corresponding [FileEditor].
 */
class SourceCodeEditorProvider
private constructor(private val providers: Collection<PreviewRepresentationProvider>) :
  FileEditorProvider, QuickDefinitionProvider, DumbAware {
  constructor() : this(PROVIDERS_EP.extensions.toList())

  private val log = Logger.getInstance(SourceCodeEditorProvider::class.java)

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return !LightEdit.owns(project) && file.isSourceFileType() && isAndroidEnvironment(project)
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    if (log.isDebugEnabled) {
      log.debug("createEditor file=${file.path}")
    }

    val psiFile = PsiManager.getInstance(project).findFile(file)!!

    val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
    val multiRepresentationPreview = SourceCodePreview(psiFile, textEditor.editor, providers)

    return SourceCodeEditorWithMultiRepresentationPreview(
      project,
      textEditor,
      multiRepresentationPreview
    )
  }

  override fun getEditorTypeId() = "android-source-code"

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  override fun writeState(state: FileEditorState, project: Project, targetElement: Element) {
    if (state is SourceCodeEditorWithMultiRepresentationPreviewState) {
      // Persist the text editor state
      (state.editorState as? TextEditorState)?.let {
        val editorElement = Element(EDITOR_STATE_TAG)
        TextEditorProvider().writeState(it, project, editorElement)
        targetElement.addContent(editorElement)
      }

      // Persist the multi-preview state
      serialize(state.previewState)?.let {
        targetElement.addContent(it)
      }

      // Persist the current selected layout
      state.selectedLayout?.let {
        targetElement.setAttribute(Attribute(SELECTED_LAYOUT_ATTRIBUTE, it.name))
      }
    }
  }

  override fun readState(
    sourceElement: Element,
    project: Project,
    file: VirtualFile
  ): FileEditorState {
    if (accept(project, file)) {
      var editorState: TextEditorState? = null
      var multiPreviewState: MultiRepresentationPreviewFileEditorState? = null
      var selectedLayout: TextEditorWithPreview.Layout? = null
      sourceElement.attributes.forEach {
        if (it.name == SELECTED_LAYOUT_ATTRIBUTE) {
          selectedLayout = TextEditorWithPreview.Layout.valueOf(it.value)
        } else {
          log.warn("Unexpected attribute deserializing state ${it.name}")
        }
      }
      sourceElement.children.forEach {
        when (it.name) {
          EDITOR_STATE_TAG -> {
            editorState = TextEditorProvider().readState(it, project, file) as? TextEditorState
          }
          MULTI_PREVIEW_STATE_TAG -> {
            multiPreviewState =
              XmlSerializer.deserialize(it, MultiRepresentationPreviewFileEditorState::class.java)
          }
          else -> log.warn("Unexpected tag deserializing state ${it.name}")
        }
      }

      // Avoid de-serializing empty states
      if (editorState != null || multiPreviewState != null || selectedLayout != null) {
        return SourceCodeEditorWithMultiRepresentationPreviewState(
          editorState = editorState ?: FileEditorState.INSTANCE,
          previewState = multiPreviewState ?: MultiRepresentationPreviewFileEditorState.INSTANCE,
          selectedLayout = selectedLayout
        )
      }
    }

    return FileEditorState.INSTANCE
  }

  companion object {
    @TestOnly
    fun forTesting(providers: List<PreviewRepresentationProvider>) =
      SourceCodeEditorProvider(providers)
  }
}
