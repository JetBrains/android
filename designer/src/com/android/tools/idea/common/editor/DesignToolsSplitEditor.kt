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

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.codeHighlighting.HighlightingPass
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project

private const val SPLIT_MODE_PROPERTY_PREFIX = "SPLIT_EDITOR_MODE"

private const val EDITOR_NAME = "Design"

/**
 * [SplitEditor] whose preview is a [DesignerEditor] and [getTextEditor] contains the corresponding
 * XML file displayed in the preview.
 */
open class DesignToolsSplitEditor(
  textEditor: TextEditor,
  val designerEditor: DesignerEditor,
  private val project: Project,
) :
  SplitEditor<DesignerEditor>(
    textEditor,
    designerEditor,
    EDITOR_NAME,
    defaultLayout(designerEditor),
  ) {

  private val propertiesComponent = PropertiesComponent.getInstance()

  private val backgroundEditorHighlighter = CompoundBackgroundHighlighter()

  private var textViewToolbarAction: MyToolBarAction? = null

  private var splitViewToolbarAction: MyToolBarAction? = null

  private var designViewToolbarAction: MyToolBarAction? = null

  private val modePropertyName: String?
    get() {
      val file = file ?: return null
      return String.format("%s_%s", SPLIT_MODE_PROPERTY_PREFIX, file.path)
    }

  init {
    clearLastModeProperty()
  }

  private fun clearLastModeProperty() {
    // Clear the application-level "DesignLayout" property. This is done to prevent IntelliJ from
    // storing the last selected mode and
    // restoring it when opening new files. Instead, we want to open new files using the defaults
    // set in the settings panel.
    //
    // Note: "${editorName}Layout" is the current format used by TextEditorWithPreview. Check
    // TextEditorWithPreview#getLayoutPropertyName in
    // the unlikely event this starts to fail, since the property name might have changed.
    PropertiesComponent.getInstance().setValue("${EDITOR_NAME}Layout", null)
  }

  override fun getBackgroundHighlighter(): BackgroundEditorHighlighter {
    return backgroundEditorHighlighter
  }

  override fun selectNotify() {
    super.selectNotify()
    // select/deselectNotify will be called when the user selects (clicks) or opens a new editor.
    // However, in some cases, the editor might
    // be deselected but still visible. We first check whether we should pay attention to the
    // select/deselect so we only do something if we
    // are visible.
    if (FileEditorManager.getInstance(project).selectedEditors.contains(this)) {
      designerEditor.component.activate()
    }
  }

  override fun deselectNotify() {
    super.deselectNotify()
    // If we are still visible but the user deselected us, do not deactivate the model since we
    // still need to receive updates.
    if (!FileEditorManager.getInstance(project).selectedEditors.contains(this)) {
      designerEditor.component.deactivate()
    }
  }

  override val showEditorAction: SplitEditorAction
    get() {
    if (textViewToolbarAction == null) {
      textViewToolbarAction = MyToolBarAction(super.showEditorAction, DesignerEditorPanel.State.DEACTIVATED)
    }
    return textViewToolbarAction!!
  }

  override val showEditorAndPreviewAction: SplitEditorAction
    get() {
    if (splitViewToolbarAction == null) {
      splitViewToolbarAction = MyToolBarAction(super.showEditorAndPreviewAction, DesignerEditorPanel.State.SPLIT)
    }
    return splitViewToolbarAction!!
  }

  override val showPreviewAction: SplitEditorAction
    get() {
    if (designViewToolbarAction == null) {
      designViewToolbarAction = MyToolBarAction(super.showPreviewAction, DesignerEditorPanel.State.FULL)
    }
    return designViewToolbarAction!!
  }

  /** Persist the mode in order to restore it next time we open the editor. */
  private fun setModeProperty(state: DesignerEditorPanel.State) =
    modePropertyName?.let { propertiesComponent.setValue(it, state.name) }

  override fun getState(level: FileEditorStateLevel): FileEditorState {
    // Override getState to make sure getState(FileEditorStateLevel.UNDO) works properly, otherwise
    // we'd be defaulting to the implementation
    // of TextEditorWithPreview#getState, which returns a new instance every time, causing issues in
    // undoing the editor's state because it
    // will return a different state even if nothing relevant has changed. Consequently, we need to
    // implement setState below to make sure we
    // restore the selected action when reopening this editor, which was previously taken care by
    // TextEditorWithPreview#setState, but with a
    // logic too tied to its getState implementation.
    return myEditor.getState(level)
  }

  override fun setState(state: FileEditorState) {
    // Restore the editor state, which includes, for instance, cursor position.
    myEditor.setState(state)

    // Restore the surface mode persisted.
    val propertyName = modePropertyName
    var propertyValue: String? = null
    if (propertyName != null) {
      propertyValue = propertiesComponent.getValue(propertyName)
    }

    if (propertyValue == null) {
      return
    }
    // Select the action saved if the mode saved is different than the current one.
    val panelState = DesignerEditorPanel.State.valueOf(propertyValue)
    if (panelState == designerEditor.component.state) {
      return
    }
    actions
      .firstOrNull { it is MyToolBarAction && it.panelState == panelState }
      ?.let { selectAction(it, false) }
  }

  private inner class MyToolBarAction(
    delegate: SplitEditorAction,
    val panelState: DesignerEditorPanel.State,
  ) :
    SplitEditor<DesignerEditor>.SplitEditorAction(
      delegate.name,
      delegate.icon,
      delegate.delegate,
      panelState != DesignerEditorPanel.State.FULL,
    ) {

    override fun setSelected(e: AnActionEvent, state: Boolean, userExplicitlySelected: Boolean) {
      designerEditor.component.state = panelState
      setModeProperty(panelState)
      super.setSelected(e, state, userExplicitlySelected)
      // clear the property as it might have been set by TextEditorWithPreview selection.
      clearLastModeProperty()
    }

    override fun onUserSelectedAction() {
      // We only want to track actions when users explicitly trigger them, i.e. when they click on
      // the action to change the mode. An example
      // of indirectly changing the mode is triggering "Go to XML" when in design-only mode, as we
      // change the mode to text-only.
      designerEditor.component.surface.analyticsManager.trackSelectEditorMode(panelState)
    }
  }

  private inner class CompoundBackgroundHighlighter : BackgroundEditorHighlighter {
    override fun createPassesForEditor(): Array<HighlightingPass> {
      val designEditorPasses = designerEditor.backgroundHighlighter.createPassesForEditor()
      val textEditorHighlighter = myEditor.backgroundHighlighter
      val textEditorPasses =
        textEditorHighlighter?.createPassesForEditor() ?: HighlightingPass.EMPTY_ARRAY
      return designEditorPasses + textEditorPasses
    }
  }
}

private fun defaultLayout(designerEditor: DesignerEditor) =
  when (designerEditor.component.state) {
    DesignerEditorPanel.State.FULL -> TextEditorWithPreview.Layout.SHOW_PREVIEW
    DesignerEditorPanel.State.SPLIT -> TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW
    DesignerEditorPanel.State.DEACTIVATED -> TextEditorWithPreview.Layout.SHOW_EDITOR
  }
