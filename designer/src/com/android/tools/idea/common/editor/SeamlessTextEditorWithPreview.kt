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
open class SeamlessTextEditorWithPreview(textEditor: TextEditor, designEditor: FileEditor, editorName: String) :
  TextEditorWithPreview(textEditor, designEditor, editorName) {

  private var toolbarComponent: Component? = null

  override fun getComponent(): JComponent {
    // super.getComponent() initializes toolbar and sets true visibility values for
    val mainComponent = super.getComponent()
    if (toolbarComponent == null) {
      toolbarComponent = TreeWalker(mainComponent).descendantStream().filter { it is SplitEditorToolbar }.findFirst().orElseThrow { IllegalStateException("TextEditorWithPreview should have a toolbar.") }
      saveTrueVisibility()
      // Apply visibility values overridden by this
      if (isPureTextEditor) {
        toolbarComponent?.isVisible = false
        setPureTextEditorVisibility()
      }
    }
    return mainComponent
  }

  override fun setState(state: FileEditorState) {
    super.setState(state)
    saveTrueVisibility()
    // super.setState could change the visibility, restore it if we are in a pure text editor mode
    if (isPureTextEditor) {
      setPureTextEditorVisibility()
    }
  }

  private var myIsEditorVisible = true
  private var myIsPreviewVisible = false

  /**
   * When switching to [isPureTextEditor]=true we are overriding visibility values for [myEditor] and [myPreview]. Namely, [myEditor] is
   * always visible and [myPreview] is always hidden. Thus, to be able to restore true values when switching back, we have to save those.
   */
  private fun saveTrueVisibility() {
    myIsEditorVisible = myEditor.component.isVisible
    myIsPreviewVisible = myPreview.component.isVisible
  }

  /**
   * Restoring true visibility values of [myEditor] and [myPreview] when switching to [isPureTextEditor]=false.
   */
  private fun restoreTrueVisibility() {
    myEditor.component.isVisible = myIsEditorVisible
    myPreview.component.isVisible = myIsPreviewVisible
  }

  /**
   * Setting visibility values [isPureTextEditor]=true, see [saveTrueVisibility] for more details.
   */
  private fun setPureTextEditorVisibility() {
    myEditor.component.isVisible = true
    myPreview.component.isVisible = false
  }

  // Even though isPureTextEditor is meant to be persistent this editor delegates keeping the state persistent to the clients
  var isPureTextEditor: Boolean = true
    set(value) {
      if (value == field) {
        return
      }
      toolbarComponent?.isVisible = !value
      if (value) {
        saveTrueVisibility()
        setPureTextEditorVisibility()
      }
      else {
        restoreTrueVisibility()
      }
      field = value
    }
}