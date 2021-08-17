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
package com.android.tools.idea.uibuilder.editor.multirepresentation

import com.android.tools.idea.common.editor.SeamlessTextEditorWithPreview
import com.android.tools.idea.common.editor.setEditorLayout
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener

/**
 * Converts the [PreferredVisibility] value into the equivalent [TextEditorWithPreview.Layout].
 */
private fun PreferredVisibility?.toTextEditorLayout(): TextEditorWithPreview.Layout? =
  when (this) {
    PreferredVisibility.HIDDEN -> TextEditorWithPreview.Layout.SHOW_EDITOR
    PreferredVisibility.SPLIT -> TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW
    PreferredVisibility.FULL -> TextEditorWithPreview.Layout.SHOW_PREVIEW
    else -> null
  }

/**
 * A generic [SeamlessTextEditorWithPreview] where a preview part of it is [MultiRepresentationPreview]. It keeps track of number of
 * representations in the preview part and if none switches to the pure text editor mode.
 */
open class TextEditorWithMultiRepresentationPreview<P : MultiRepresentationPreview>(
  private val project: Project, textEditor: TextEditor, preview: P, editorName: String) :
  SeamlessTextEditorWithPreview<P>(textEditor, preview, editorName) {
  /**
   * Whether this editor is active currently or not.
   */
  private var isActive = false

  /**
   * True until the first activation happens.
   */
  private var firstActivation = true

  /**
   * True if the layout has been set explicitly when restoring the state. When it has been set explicitly,
   * the editor will not try to set the preferred layout from the [PreviewRepresentation.preferredInitialVisibility].
   */
  private var layoutSetExplicitly = false

  init {
    isPureTextEditor = preview.representationNames.isEmpty()
    preview.onRepresentationsUpdated = {
      isPureTextEditor = preview.representationNames.isEmpty()
    }
    preview.registerShortcuts(component)
    preview.component.addComponentListener(object : ComponentListener {
      override fun componentResized(e: ComponentEvent?) {}
      override fun componentMoved(e: ComponentEvent?) {}

      override fun componentShown(e: ComponentEvent?) {
        // The preview has been shown but only activate if the editor is selected
        if (isEditorSelected()) activate()
      }

      override fun componentHidden(e: ComponentEvent?) {
        deactivate()
      }
    })
  }

  /**
   * Returns whether this preview is active. That means that the number of [selectNotify] calls is larger than
   * the number of [deselectNotify] calls.
   */
  private fun isEditorSelected(): Boolean {
    val selectedEditors = FileEditorManager.getInstance(
      project).selectedEditors.filterIsInstance<TextEditorWithMultiRepresentationPreview<*>>()
    return selectedEditors.any { it == this }
  }

  private fun activate() {
    if (isActive) return
    isActive = true
    preview.onActivate()
  }

  private fun deactivate() {
    if (!isActive) return
    isActive = false
    preview.onDeactivate()
  }

  final override fun selectNotify() {
    super.selectNotify()

    if (firstActivation) {
      // This is the first time the editor is being activated so trigger the onInit initialization.
      firstActivation = false
      preview.onInit()

      if (!layoutSetExplicitly) {
        preview.currentRepresentation?.preferredInitialVisibility?.toTextEditorLayout()?.let {
          setLayout(it)
        }
      }
    }

    // The editor has been selected, but only activate if it's visible.
    if (preview.component.isShowing) activate()
  }

  final override fun deselectNotify() {
    super.deselectNotify()
    if (isEditorSelected()) return

    deactivate()
  }

  /**
   * Set the layout (code, split or only design) of the panel explicitly.
   */
  protected fun setLayout(layout: Layout?) {
    if (layout != null) {
      layoutSetExplicitly = true
      setEditorLayout(layout)
    }
  }
}
