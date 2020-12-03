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
package com.android.tools.idea.common.editor

import com.android.tools.adtui.TreeWalker
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.SplitEditorToolbar
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import java.awt.Component
import javax.swing.JComponent

/**
 * This Editor allows seamless switch between pure [TextEditor] and [TextEditorWithPreview]. It allows switching between those by changing
 * [isPureTextEditor] property. This editor records the true state of [TextEditorWithPreview] and hides toolbar and preview part of the
 * [TextEditorWithPreview] leaving only [TextEditor] visible when [isPureTextEditor] is true. When [isPureTextEditor] is false it behaves as
 * [TextEditorWithPreview].
 *
 * This is useful in case we want to show preview or not depending on the file content.
 */
open class SeamlessTextEditorWithPreview<P : FileEditor>(textEditor: TextEditor, preview: P, editorName: String) :
  SplitEditor<P>(textEditor, preview, editorName) {

  private var toolbarComponent: Component? = null

  override fun getComponent(): JComponent {
    // super.getComponent() initializes toolbar and sets true visibility values for
    val mainComponent = super.getComponent()
    if (toolbarComponent == null) {
      toolbarComponent = TreeWalker(mainComponent).descendantStream().filter { it is SplitEditorToolbar }.findFirst().orElseThrow { IllegalStateException("TextEditorWithPreview should have a toolbar.") }
      // Apply visibility values overridden by this
      if (isPureTextEditor) {
        setPureTextEditorVisibility()
      }
    }
    return mainComponent
  }

  override fun setState(state: FileEditorState) {
    super.setState(state)

    // super.setState could change the visibility, restore it if we are in a pure text editor mode
    if (isPureTextEditor) {
      setPureTextEditorVisibility()
    }
    else {
      setSplitTextEditorVisibility()
    }
  }

  /**
   * Restore the visibility of the components depending on the current [layout].
   */
  private fun setSplitTextEditorVisibility() =
    when (layout) {
      Layout.SHOW_PREVIEW -> selectDesignMode(false)
      Layout.SHOW_EDITOR_AND_PREVIEW -> selectSplitMode(false)
      Layout.SHOW_EDITOR -> selectTextMode(false)
      null -> {}
    }

  /**
   * Setting visibility values [isPureTextEditor]=true, see [setSplitTextEditorVisibility] for more details.
   */
  private fun setPureTextEditorVisibility() {
    myEditor.component.isVisible = true
    myPreview.component.isVisible = false
  }

  // Even though isPureTextEditor is meant to be persistent this editor delegates keeping the state persistent to the clients
  var isPureTextEditor: Boolean = true
    set(value) {
      toolbarComponent?.isVisible = !value
      if (value) {
        setPureTextEditorVisibility()
      }
      else {
        setSplitTextEditorVisibility()
      }
      field = value
    }
}