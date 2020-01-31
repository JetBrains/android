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

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.util.SystemInfo
import com.intellij.pom.Navigatable
import java.awt.event.ActionEvent
import java.awt.event.InputEvent.ALT_DOWN_MASK
import java.awt.event.InputEvent.CTRL_DOWN_MASK
import java.awt.event.InputEvent.SHIFT_DOWN_MASK
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
import javax.swing.KeyStroke


val ACTION_SHORTCUT_MODIFIERS = (if (SystemInfo.isMac) CTRL_DOWN_MASK else ALT_DOWN_MASK) or SHIFT_DOWN_MASK

private const val NAV_LEFT_INPUT_KEY = "navigate_split_editor_mode_left"

private const val NAV_RIGHT_INPUT_KEY = "navigate_split_editor_mode_right"

/**
 * [TextEditorWithPreview] with keyboard shortcuts to navigate between views, and code navigation when interacting with the preview portion
 * of the editor. Please use this class if you're adding a [TextEditorWithPreview] editor to Android Studio.
 */
abstract class SplitEditor(textEditor: TextEditor, designEditor: FileEditor, editorName: String, defaultLayout: Layout) :
  TextEditorWithPreview(textEditor, designEditor, editorName, defaultLayout), TextEditor {

  private val textViewAction = SplitEditorAction("Code", AllIcons.General.LayoutEditorOnly, super.getShowEditorAction())

  private val splitViewAction = SplitEditorAction("Split", AllIcons.General.LayoutEditorPreview, super.getShowEditorAndPreviewAction())

  private val previewViewAction = SplitEditorAction("Design", AllIcons.General.LayoutPreviewOnly, super.getShowPreviewAction())

  protected val actions: List<SplitEditorAction> by lazy { listOf(showEditorAction, showEditorAndPreviewAction, showPreviewAction) }

  init {
    registerModeNavigationShortcuts()
  }

  override fun getFile() = myEditor.file

  override fun getEditor() = myEditor.editor

  override fun canNavigateTo(navigatable: Navigatable) = myEditor.canNavigateTo(navigatable)

  override fun navigateTo(navigatable: Navigatable) = myEditor.navigateTo(navigatable)

  override fun getShowEditorAction() = textViewAction

  override fun getShowEditorAndPreviewAction() = splitViewAction

  override fun getShowPreviewAction() = previewViewAction

  private fun getDummyActionEvent() =
    AnActionEvent(null, DataManager.getInstance().getDataContext(component), "", Presentation(), ActionManager.getInstance(), 0)

  // TODO(b/143210506): Review the current APIs for selecting and checking the current mode to be backed by an enum.
  fun isTextMode() = textViewAction.isSelected(getDummyActionEvent())

  fun isSplitMode() = splitViewAction.isSelected(getDummyActionEvent())

  fun isDesignMode() = previewViewAction.isSelected(getDummyActionEvent())

  fun selectTextMode(userExplicitlyTriggered: Boolean) = selectAction(showEditorAction, userExplicitlyTriggered)

  fun selectSplitMode(userExplicitlyTriggered: Boolean) = selectAction(showEditorAndPreviewAction, userExplicitlyTriggered)

  fun selectDesignMode(userExplicitlyTriggered: Boolean) = selectAction(showPreviewAction, userExplicitlyTriggered)

  protected fun selectAction(action: SplitEditorAction, userExplicitlyTriggered: Boolean) =
    action.setSelected(getDummyActionEvent(), true, userExplicitlyTriggered)

  protected fun getSelectedAction() = actions.firstOrNull { it.isSelected(getDummyActionEvent()) }

  private fun List<SplitEditorAction>.next(selectedIndex: Int): SplitEditorAction = this[(selectedIndex + 1) % this.size]

  private fun List<SplitEditorAction>.previous(selectedIndex: Int): SplitEditorAction = this[(this.size + selectedIndex - 1) % this.size]

  private fun registerModeNavigationShortcuts() {
    val navigateLeftAction = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) = selectAction(actions.previous(actions.indexOf(getSelectedAction())), true)
    }
    component.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, ACTION_SHORTCUT_MODIFIERS), NAV_LEFT_INPUT_KEY)
    component.actionMap.put(NAV_LEFT_INPUT_KEY, navigateLeftAction)

    val navigateRightAction = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) = selectAction(actions.next(actions.indexOf(getSelectedAction())), true)
    }

    component.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, ACTION_SHORTCUT_MODIFIERS), NAV_RIGHT_INPUT_KEY)
    component.actionMap.put(NAV_RIGHT_INPUT_KEY, navigateRightAction)
  }

  protected open inner class SplitEditorAction internal constructor(val name: String,
                                                                    val icon: Icon,
                                                                    val delegate: ToggleAction)
    : ToggleAction(name, name, icon) {

    override fun isSelected(e: AnActionEvent) = delegate.isSelected(e)

    override fun setSelected(e: AnActionEvent, state: Boolean) = setSelected(e, state, true)

    open fun setSelected(e: AnActionEvent, state: Boolean, userExplicitlySelected: Boolean) {
      delegate.setSelected(e, state)
      if (userExplicitlySelected) {
        // We might want to run a callback when users explicitly select the action, i.e. when they click on the action to change the mode.
        // For example, we might want to track when they change modes. An example of indirectly changing the mode is triggering "Go to XML"
        // when in design mode, as we change the mode to text-only.
        onUserSelectedAction()
      }
      component.requestFocus()
    }

    open fun onUserSelectedAction() {}
  }
}