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

import com.google.common.annotations.VisibleForTesting
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import javax.swing.Icon
import javax.swing.JComponent

val SPLIT_TEXT_EDITOR_KEY = DataKey.create<TextEditor>(TextEditor::class.java.name)

/**
 * [TextEditorWithPreview] with keyboard shortcuts to navigate between views, and code navigation when interacting with the preview portion
 * of the editor. Please use this class if you're adding a [TextEditorWithPreview] editor to Android Studio.
 */
abstract class SplitEditor<P : FileEditor>(textEditor: TextEditor,
                                           designEditor: P,
                                           editorName: String,
                                           defaultLayout: Layout = Layout.SHOW_EDITOR_AND_PREVIEW)
  : TextEditorWithPreview(textEditor, designEditor, editorName, defaultLayout), DataProvider {

  private val textViewAction = SplitEditorAction("Code", AllIcons.General.LayoutEditorOnly, super.getShowEditorAction(), true)

  private val splitViewAction = SplitEditorAction("Split", AllIcons.General.LayoutEditorPreview, super.getShowEditorAndPreviewAction(),
                                                  true)

  private val previewViewAction = SplitEditorAction("Design", AllIcons.General.LayoutPreviewOnly, super.getShowPreviewAction(), false)

  private val navigateLeftAction = object : AnAction() {
    override fun actionPerformed(e: AnActionEvent) = selectAction(actions.previous(actions.indexOf(getSelectedAction())), true)
  }

  override fun isShowFloatingToolbar() = false

  override fun isShowActionsInTabs() = false

  private val navigateRightAction = object : AnAction() {
    override fun actionPerformed(e: AnActionEvent) = selectAction(actions.next(actions.indexOf(getSelectedAction())), true)
  }

  protected val actions: List<SplitEditorAction> by lazy { listOf(showEditorAction, showEditorAndPreviewAction, showPreviewAction) }

  private var shortcutsRegistered = false

  override fun getComponent(): JComponent {
    val thisComponent = super.getComponent()
    if (!shortcutsRegistered) {
      shortcutsRegistered = true
      registerModeNavigationShortcuts(thisComponent)
    }
    return thisComponent
  }

  override fun getShowEditorAction() = textViewAction

  override fun getShowEditorAndPreviewAction() = splitViewAction

  override fun getShowPreviewAction() = previewViewAction

  override fun getData(dataId: String): Any? {
    if (LangDataKeys.IDE_VIEW.`is`(dataId)) {
      val project = editor.project ?: return null
      return FileEditorManagerEx.getInstanceEx(project).getData(dataId, editor, editor.caretModel.currentCaret)
    }
    if (SPLIT_TEXT_EDITOR_KEY.`is`(dataId)) {
      return textEditor
    }
    return null
  }

  private fun getFakeActionEvent() =
    AnActionEvent(null, DataManager.getInstance().getDataContext(component), "", Presentation(), ActionManager.getInstance(), 0)

  // TODO(b/143210506): Review the current APIs for selecting and checking the current mode to be backed by an enum.
  fun isTextMode() = textViewAction.isSelected(getFakeActionEvent())

  fun isSplitMode() = splitViewAction.isSelected(getFakeActionEvent())

  fun isDesignMode() = previewViewAction.isSelected(getFakeActionEvent())

  fun selectTextMode(userExplicitlyTriggered: Boolean) = selectAction(showEditorAction, userExplicitlyTriggered)

  fun selectSplitMode(userExplicitlyTriggered: Boolean) = selectAction(showEditorAndPreviewAction, userExplicitlyTriggered)

  fun selectDesignMode(userExplicitlyTriggered: Boolean) = selectAction(showPreviewAction, userExplicitlyTriggered)

  protected fun selectAction(action: SplitEditorAction, userExplicitlyTriggered: Boolean) =
    action.setSelected(getFakeActionEvent(), true, userExplicitlyTriggered)

  protected fun getSelectedAction() = actions.firstOrNull { it.isSelected(getFakeActionEvent()) }

  private fun List<SplitEditorAction>.next(selectedIndex: Int): SplitEditorAction = this[(selectedIndex + 1) % this.size]

  private fun List<SplitEditorAction>.previous(selectedIndex: Int): SplitEditorAction = this[(this.size + selectedIndex - 1) % this.size]

  /**
   * TODO (b/149212539): Register these shortcuts to plugin xml file to support custom keymap. Then remove this function.
   */
  @VisibleForTesting
  protected fun registerModeNavigationShortcuts(applicableTo: JComponent) {
    navigateLeftAction.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_PREVIOUS_EDITOR_TAB), applicableTo)
    navigateRightAction.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_NEXT_EDITOR_TAB), applicableTo)
  }

  /**
   * Action to switch to a different mode in the split editor.
   *
   * @param name the name of the mode.
   * @param icon icon for the mode.
   * @param delegate a [ToggleAction] that will receive the [setSelected] call then it is triggered in this action.
   * @param showDefaultGutterPopup when this action is triggered, if true, the text editor will use the default gutter popup.
   */
  protected open inner class SplitEditorAction internal constructor(val name: String,
                                                                    val icon: Icon,
                                                                    val delegate: ToggleAction,
                                                                    val showDefaultGutterPopup: Boolean)
    : ToggleAction(name, name, icon), DumbAware {

    override fun isSelected(e: AnActionEvent) = delegate.isSelected(e)

    override fun setSelected(e: AnActionEvent, state: Boolean) = setSelected(e, state, true)

    open fun setSelected(e: AnActionEvent, state: Boolean, userExplicitlySelected: Boolean) {
      val isRedundantStateChange = isSelected(e) == state
      delegate.setSelected(e, state)
      if (isRedundantStateChange) return // Return early if state is being redundantly set, otherwise we could request the focus too often.

      // When the text editor is not visible, trying to trigger the right click popup on the breadcrumb bar will throw an
      // exception. This disables the popup when showDefaultGutterPopup is false allowing to avoid the popup when the text editor
      // is not visible. See http://b/208596732.
      (textEditor.editor as? EditorEx)?.gutterComponentEx?.setShowDefaultGutterPopup(showDefaultGutterPopup)
      if (userExplicitlySelected) {
        // We might want to run a callback when users explicitly select the action, i.e. when they click on the action to change the mode.
        // For example, we might want to track when they change modes. An example of indirectly changing the mode is triggering "Go to XML"
        // when in design mode, as we change the mode to text-only.
        onUserSelectedAction()
      }

      if (state) {
        preferredFocusedComponent?.requestFocusInWindow()
      }
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      val bothShortcutsEmpty = navigateLeftAction.shortcutSet == CustomShortcutSet.EMPTY
                               && navigateRightAction.shortcutSet == CustomShortcutSet.EMPTY
      if (bothShortcutsEmpty || isSelected(e)) {
        e.presentation.description = name
        return
      }

      val shortcut =
        // Action is on the right of the selected action
        if (actions.previous(actions.indexOf(this)) == getSelectedAction()) navigateRightAction.shortcutSet
        // Action is on the left of the selected action
        else navigateLeftAction.shortcutSet

      val suffix = KeymapUtil.getFirstKeyboardShortcutText(shortcut).takeIf { it.isNotEmpty() }?.let { " (${it})" } ?: ""
      e.presentation.description = "$name$suffix"
    }

    override fun displayTextInToolbar() = true

    open fun onUserSelectedAction() {}
  }

  @Suppress("UNCHECKED_CAST")
  val preview: P = myPreview as P
}