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
package com.android.tools.idea.compose.preview.gallery

import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.createToolbarWithNavigation
import com.android.tools.idea.preview.Colors
import com.intellij.icons.AllIcons
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
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBThinOverlappingScrollBar
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import java.awt.Adjustable
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.util.concurrent.Executor
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
 * Shows a tab for each [Key]. [GalleryTabs] always track the list of currently available tabs using
 * [keysProvider] and currently selected tab using [selectedProvider]. [tabChangeListener] is called
 * only if tab is changed by interacting with [GalleryTabs].
 *
 * @param root a root [JComponent] for this [GalleryTabs].
 * @param tabChangeListener is called when new [Key] is selected. [GalleryTabs] insures
 *   [tabChangeListener] is not called twice if same [Key] set twice.
 */
class GalleryTabs<Key : TitledKey>(
  private val root: JComponent,
  private val selectedProvider: (DataContext) -> Key?,
  private val keysProvider: (DataContext) -> Set<Key>,
  private val tabChangeListener: (DataContext, Key?) -> Unit
) : JPanel(BorderLayout()) {

  private inner class TabLabelAction(val key: Key) :
    ToggleAction(key.title), CustomComponentAction {
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
        .apply {
          updateFont()

          addFocusListener(
            object : FocusListener {
              override fun focusGained(e: FocusEvent) {
                (e.component as? ActionButtonWithText)?.let { focusOnComponent(it) }
              }

              override fun focusLost(e: FocusEvent) {}
            },
          )
        }

    override fun actionPerformed(e: AnActionEvent) {
      updateSelectedKey(e, key)
      // If popup was opened - close it.
      allTabDropdown.popup?.cancel()
      val sameTabInToolbar =
        previousToolbar?.components?.filterIsInstance<ActionButtonWithText>()?.firstOrNull {
          (it.action as? GalleryTabs<*>.TabLabelAction)?.key == this.key
        }
      sameTabInToolbar?.let { focusOnComponent(it) }
    }

    private fun focusOnComponent(button: ActionButtonWithText) {
      when {
        // If tab is not visible and on the left side - move it to the most left visible side.
        button.location.x < -centerPanel.location.x -> scrollBar.value = button.location.x

        // If tab is not visible and on the right side - move it to the most right visible side.
        button.location.x + button.bounds.width >
          -centerPanel.location.x + scrollBar.bounds.width ->
          scrollBar.value = button.location.x + button.bounds.width - scrollBar.bounds.width
      }
      button.requestFocus()
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
    DumbAwareAction(message("action.gallery.show.hidden"), null, AllIcons.General.ChevronDown) {

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
          .also {
            val location: Point = allTabToolbar.locationOnScreen
            location.translate(0, allTabToolbar.height)
            it.showInScreenCoordinates(allTabToolbar, location)
          }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isVisible = centerPanel.width > this@GalleryTabs.width
    }
  }

  var selectedKey: Key? = null
    private set

  /**
   * Notify [tabChangeListener] about change in [selectedKey]. Should only be called if
   * [selectedKey] is modified by interaction with [GalleryTabs].
   */
  private fun updateSelectedKey(e: AnActionEvent, key: Key?) {
    // Avoid setting same value twice.
    if (selectedKey == key) return
    selectedKey = key
    tabChangeListener(e.dataContext, key)
  }

  private val allTabDropdown = AllTabsDropdown()
  private val labelActions: MutableMap<Key, TabLabelAction> = mutableMapOf()
  private val centerPanel = JPanel(BorderLayout())
  private val scrollBar = JBThinOverlappingScrollBar(Adjustable.HORIZONTAL)
  private val allTabToolbar: JComponent =
    createToolbarWithNavigation(root, "More Tabs", listOf(allTabDropdown)).component.apply {
      background = Colors.DEFAULT_BACKGROUND_COLOR
    }
  private var previousToolbar: JComponent? = null

  private var updateToolbarExecutor: Executor = Executor { command ->
    invokeLater { command.run() }
  }

  init {
    add(
      JBScrollPane(centerPanel, VERTICAL_SCROLLBAR_NEVER, HORIZONTAL_SCROLLBAR_AS_NEEDED).apply {
        horizontalScrollBar = scrollBar
        border = JBUI.Borders.empty()
      },
      BorderLayout.CENTER,
    )
    add(allTabToolbar, BorderLayout.EAST)
    createEmptyToolbar()
  }

  private fun createEmptyToolbar() {
    previousToolbar =
      createToolbarWithNavigation(root, "Gallery Tabs", GalleryActionGroup(emptyList()))
        .component
        .apply {
          background = Colors.DEFAULT_BACKGROUND_COLOR
          centerPanel.add(this, BorderLayout.CENTER)
        }
  }

  /**
   * Update all available [Key]s and currently [selectedKey]. Toolbar is recreated if there are any
   * changes in [keys]. It also ensures that if there are no changes in [keys] toolbar will not be
   * updated.
   */
  private fun updateKeys(keys: Set<Key>, selected: Key?) {

    // If [GalleryTabs] needs update, [previousToolbar] will be removed and new toolbar will be
    // created and added with available [labelActions].
    val needsUpdate =
      labelActions.keys.any { !keys.contains(it) } || keys.any { !labelActions.keys.contains(it) }

    selectedKey = selected

    // Only update toolbar if there are any changes.
    if (needsUpdate) {
      labelActions.clear()
      keys.forEach { labelActions[it] = TabLabelAction(it) }
      // Remove previous toolbar if exists.
      previousToolbar?.let { centerPanel.remove(it) }
      // Create new toolbar.
      val toolbar =
        createToolbarWithNavigation(
            root,
            "Gallery Tabs",
            GalleryActionGroup(labelActions.values.toList())
          )
          .component

      updateToolbarExecutor.execute {
        centerPanel.add(toolbar, BorderLayout.CENTER)
        previousToolbar = toolbar
      }
    }
  }

  @TestOnly
  fun setUpdateToolbarExecutorForTests(executor: Executor) {
    updateToolbarExecutor = executor
  }

  private inner class GalleryActionGroup(actions: List<AnAction>) : DefaultActionGroup(actions) {
    override fun update(e: AnActionEvent) {
      super.update(e)
      e.dataContext.let { updateKeys(keysProvider(it), selectedProvider(it)) }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }
  }
}
