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
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.SplitEditorToolbar
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import java.awt.Component
import javax.swing.JComponent

/**
 * Utility method that ensures the current [TextEditorWithPreview.Layout] for this editor is the given [layout].
 */
fun <P: FileEditor> SeamlessTextEditorWithPreview<P>.setEditorLayout(layout: TextEditorWithPreview.Layout) = when (layout) {
  TextEditorWithPreview.Layout.SHOW_PREVIEW -> selectDesignMode(false)
  TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW -> selectSplitMode(false)
  TextEditorWithPreview.Layout.SHOW_EDITOR -> selectTextMode(false)
  else -> {}
}

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

  private var tabsAction: TabsActionWrapper? = null

  override fun getComponent(): JComponent {
    // super.getComponent() initializes toolbar and sets true visibility values for
    val mainComponent = super.getComponent()
    if (toolbarComponent == null) {
      toolbarComponent = TreeWalker(mainComponent).descendantStream().filter { it is SplitEditorToolbar }.findFirst().orElseThrow {
        IllegalStateException("TextEditorWithPreview should have a toolbar.")
      }
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
      // Restore the visibility of the components depending on the current layout.
      if (layout != null) setEditorLayout(layout)
    }
  }

  /**
   * Sets the visibility values of both [myEditor] and [myPreview] components when [isPureTextEditor] is true.
   */
  private fun setPureTextEditorVisibility() {
    myEditor.component.isVisible = true
    myPreview.component.isVisible = false
  }

  override fun getTabActions(): ActionGroup? {
    val parentActions = super.getTabActions() ?: return null
    return tabsAction ?: TabsActionWrapper(parentActions).also { tabsAction = it }
  }

  // Even though isPureTextEditor is meant to be persistent this editor delegates keeping the state persistent to the clients
  var isPureTextEditor: Boolean = true
    set(value) {
      // Toolbar should be hidden if file the file is handled as pure-text, if the split controls are shown in tabs, or if the controls
      // are shown in a floating toolbar.
      val shouldHideToolbar = value || isShowActionsInTabs || isShowFloatingToolbar
      toolbarComponent?.isVisible = !shouldHideToolbar
      tabsAction?.setTabsActionVisibility(!value && isShowActionsInTabs)
      if (value) {
        setPureTextEditorVisibility()
        setEditorLayout(Layout.SHOW_EDITOR)
      }
      else {
        // Restore the visibility of the components depending on the current layout.
        if (layout != null) setEditorLayout(layout)
      }
      field = value
    }

  /**
   * [DefaultActionGroup] wrapper that controls another [ActionGroup] visibility by adding/removing its actions to the wrapper whenever
   * [setTabsActionVisibility] is called. Since the wrapper sets its template presentation `isHideGroupIfEmpty` property to true, the group
   * entry point will be invisible if its children list is empty.
   */
  private class TabsActionWrapper(private val originalActionGroup: ActionGroup) : DefaultActionGroup() {
    init {
      templatePresentation.isHideGroupIfEmpty = true
    }

    fun setTabsActionVisibility(visible: Boolean) {
      removeAll()
      if (visible) {
        addAll(originalActionGroup)
      }
    }
  }
}