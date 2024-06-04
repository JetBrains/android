/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.gallery

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.util.createToolbarWithNavigation
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JPanel
import org.jetbrains.annotations.VisibleForTesting

/**
 * Shows dropdown with a option for each [Key]. [GalleryView] always track the list of currently
 * available [Key]s using [keysProvider] and currently selected [Key] using [selectedProvider].
 * [selectionListener] is called only if selection is changed by interacting with [GalleryView].
 *
 * @param root a root [JComponent] for this [GalleryView].
 * @param selectionListener is called when new [Key] is selected. [GalleryView] insures
 *   [selectionListener] is not called twice if same [Key] set twice.
 */
internal class GalleryView<Key : TitledKey>(
  private val root: JComponent,
  private val selectedProvider: (DataContext) -> Key?,
  private val keysProvider: (DataContext) -> Set<Key>,
  private val selectionListener: (DataContext, Key?) -> Unit,
) : JPanel(BorderLayout()), Gallery<Key> {

  override val component: JComponent = this

  companion object {
    private const val MAX_TITLE_LENGTH = 50

    /** Truncate title string to have [MAX_TITLE_LENGTH] maximum length. */
    @VisibleForTesting
    fun String.truncate(): String {
      return if (this.length >= MAX_TITLE_LENGTH) this.take(MAX_TITLE_LENGTH) + "..." else this
    }
  }

  /** A label displayed in toolbar. */
  private class SelectPreviewLabel : ToolbarLabelAction() {
    init {
      templatePresentation.text = message("action.gallery.select.preview")
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
      (super.createCustomComponent(presentation, place) as JBLabel).apply { font = JBFont.label() }
  }

  /** An action to select [Key]. */
  private inner class SelectKeyAction(val key: Key) :
    ToggleAction(key.title.truncate()), CustomComponentAction {
    override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
      object :
          ActionButtonWithText(
            this,
            presentation,
            ActionPlaces.TOOLBAR,
            ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE,
          ) {
          override fun updateUI() {
            super.updateUI()
            updateFont()
          }

          fun updateFont() {
            font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
          }
        }
        .apply { updateFont() }

    override fun actionPerformed(e: AnActionEvent) {
      updateSelectedKey(e, key)
      // If popup was opened - close it.
      selectPreviewDropDown.popup?.cancel()
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      return selectedKey == key
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) selectedKey = key
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }
  }

  /** Toolbar button that shows all available previews in a dropdown. */
  private inner class SelectPreviewDropDown : DropDownAction(null, null, null) {

    var popup: ListPopup? = null

    override fun displayTextInToolbar() = true

    override fun actionPerformed(e: AnActionEvent) {
      popup?.let {
        it.cancel()
        popup = null
      }
      popup =
        JBPopupFactory.getInstance()
          .createActionGroupPopup(
            null,
            DefaultActionGroup(keyActions.values.toList()),
            e.dataContext,
            null,
            true,
          )
          .also {
            val location: Point = selectPreviewToolbar.locationOnScreen
            location.translate(0, selectPreviewToolbar.height)
            it.showInScreenCoordinates(selectPreviewToolbar, location)
          }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isVisible = true
      e.dataContext.let {
        e.presentation.text =
          selectedProvider(it)?.title?.truncate() ?: message("action.gallery.select.preview")
      }
    }
  }

  override var selectedKey: Key? = null
    private set

  /**
   * Notify [selectionListener] about change in [selectedKey]. Should only be called if
   * [selectedKey] is modified by interaction with [GalleryView].
   */
  private fun updateSelectedKey(e: AnActionEvent, key: Key?) {
    // Avoid setting same value twice.
    if (selectedKey == key) return
    selectedKey = key
    selectionListener(e.dataContext, key)
  }

  private val selectPreviewDropDown = SelectPreviewDropDown()
  private val keyActions: MutableMap<Key, SelectKeyAction> = mutableMapOf()

  @get:VisibleForTesting
  val testKeys: List<Key>
    get() = keyActions.keys.toList()

  private val selectPreviewToolbar: JComponent =
    createToolbarWithNavigation(
        root,
        "Select Preview",
        GalleryActionGroup(listOf(SelectPreviewLabel(), selectPreviewDropDown)),
      )
      .component

  init {
    add(selectPreviewToolbar, BorderLayout.EAST)
  }

  /**
   * Update all available [Key]s and currently [selectedKey]. Toolbar is recreated if there are any
   * changes in [keys]. It also ensures that if there are no changes in [keys] toolbar will not be
   * updated.
   */
  private fun updateKeys(keys: Set<Key>, selected: Key?) {

    val needsUpdate =
      keyActions.keys.any { !keys.contains(it) } || keys.any { !keyActions.keys.contains(it) }

    selectedKey = selected

    // Only update toolbar if there are any changes.
    if (needsUpdate) {
      keyActions.clear()
      keys.forEach { keyActions[it] = SelectKeyAction(it) }
    }
  }

  private inner class GalleryActionGroup(actions: List<AnAction>) : DefaultActionGroup(actions) {
    override fun update(e: AnActionEvent) {
      super.update(e)
      e.dataContext.let { updateKeys(keysProvider(it), selectedProvider(it)) }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }
  }
}
