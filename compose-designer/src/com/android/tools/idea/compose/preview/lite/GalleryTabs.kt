/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.lite

import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.compose.preview.message
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.ui.AnActionButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBThinOverlappingScrollBar
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Adjustable
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER

/** A key for each tab in [GalleryTabs]. */
interface TitledKey {
  /** A title of the tab in [GalleryTabs]. */
  val title: String
}

/**
 * Shows a tab for each [Key].
 *
 * @param root a root [JComponent] for this [GalleryTabs].
 * @param keys initial set of [Key]s to create [GalleryTabs] with
 * @param tabChangeListener is called when new [Key] is selected. It is also called after
 *   [GalleryTabs] initialization with first selected [Key]. [GalleryTabs] insures
 *   [tabChangeListener] is not called twice if same [Key] set twice.
 */
class GalleryTabs<Key : TitledKey>(
  private val root: JComponent,
  keys: Set<Key>,
  private val tabChangeListener: (Key?) -> Unit
) : JPanel(BorderLayout()) {

  private inner class TabLabelAction(val key: Key) :
    ToggleAction(key.title), CustomComponentAction {
    override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
      ActionButtonWithText(
          this,
          presentation,
          ActionPlaces.TOOLBAR,
          ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE,
        )
        .apply { font = JBFont.medium() }

    override fun actionPerformed(e: AnActionEvent) {
      selectedKey = key
      // If popup was opened - close it.
      allTabDropdown.popup?.cancel()
      val sameTabInToolbar =
        previousToolbar?.components?.filterIsInstance<ActionButtonWithText>()?.firstOrNull {
          (it.action as? GalleryTabs<*>.TabLabelAction)?.key == this.key
        }
      sameTabInToolbar?.let {
        when {
          // If tab is not visible and on the left side - move it to the most left visible side.
          it.location.x < -centerPanel.location.x -> scrollBar.value = it.location.x

          // If tab is not visible and on the right side - move it to the most right visible side.
          it.location.x + it.bounds.width > -centerPanel.location.x + scrollBar.bounds.width ->
            scrollBar.value = it.location.x + it.bounds.width - scrollBar.bounds.width
        }
        it.requestFocus()
      }
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      return selectedKey == key
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) selectedKey = key
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }
  }

  /** Toolbar button that shows all available previews in a dropdown. */
  private inner class AllTabsDropdown :
    AnActionButton(message("action.gallery.show.hidden"), AllIcons.General.ChevronDown) {

    var popup: ListPopup? = null
    override fun actionPerformed(e: AnActionEvent) {
      popup?.let {
        it.cancel()
        popup = null
      }
      popup =
        JBPopupFactory.getInstance()
          .createActionGroupPopup(
            null,
            DefaultActionGroup(labelActions.values.toList()),
            e.dataContext,
            null,
            true,
          )
          .also { it.showInCenterOf(allTabToolbar) }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun updateButton(e: AnActionEvent) {
      super.updateButton(e)
      e.presentation.isVisible = centerPanel.width > this@GalleryTabs.width
    }
  }

  var selectedKey: Key? = null
    private set(value) {
      // Avoid setting same value twice.
      if (field == value) return
      field = value
      tabChangeListener(value)
    }

  private val allTabDropdown = AllTabsDropdown()
  private val labelActions: MutableMap<Key, TabLabelAction> = mutableMapOf()
  private val centerPanel = JPanel(BorderLayout())
  private val scrollBar = JBThinOverlappingScrollBar(Adjustable.HORIZONTAL)
  private val allTabToolbar: JComponent = createToolbar("More Tabs", listOf(allTabDropdown))
  private var previousToolbar: JComponent? = null

  init {
    add(
      JBScrollPane(centerPanel, VERTICAL_SCROLLBAR_NEVER, HORIZONTAL_SCROLLBAR_AS_NEEDED).apply {
        horizontalScrollBar = scrollBar
        border = JBUI.Borders.empty()
      },
      BorderLayout.CENTER,
    )
    add(allTabToolbar, BorderLayout.EAST)
    updateKeys(keys)
  }

  /**
   * Update all available [Key]s. Toolbar is recreated if there are any changes in [keys]. It also
   * insures that if there are no changes in [keys] toolbar will not be updated.
   */
  fun updateKeys(keys: Set<Key>) {

    // If [GalleryTabs] needs update, [previousToolbar] will be removed and new toolbar will be
    // created and added with available [labelActions].
    val needsUpdate =
      labelActions.keys.any { !keys.contains(it) } || keys.any { !labelActions.keys.contains(it) }

    // Only update toolbar if there are any changes.
    if (needsUpdate) {
      labelActions.clear()
      keys.forEach { labelActions[it] = TabLabelAction(it) }
      // Remove previous toolbar if exists.
      previousToolbar?.let { centerPanel.remove(it) }
      // Create new toolbar.
      val toolbar = createToolbar("Gallery Tabs", labelActions.values.toList())
      centerPanel.add(toolbar, BorderLayout.CENTER)
      previousToolbar = toolbar
      // If selectedKey was removed, select first key.
      selectedKey = if (keys.contains(selectedKey)) selectedKey else keys.firstOrNull()
    }
  }

  /** Creates [ActionToolbarImpl] with [actions]. */
  private fun createToolbar(place: String, actions: List<AnAction>) =
    ActionToolbarImpl(place, DefaultActionGroup(actions), true).apply {
      targetComponent = root
      ActionToolbarUtil.makeToolbarNavigable(this)
      layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
      setMinimumButtonSize(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    }
}
