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
package com.android.tools.idea.preview.focus

import com.android.tools.idea.preview.Colors
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.util.createToolbarWithNavigation
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
import com.intellij.ui.AnActionButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBThinOverlappingScrollBar
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
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
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

/**
 * Shows a tab for each [Key]. [FocusModeTabs] always track the list of currently available tabs
 * using [keysProvider] and currently selected tab using [selectedProvider]. [tabChangeListener] is
 * called only if tab is changed by interacting with [FocusModeTabs].
 *
 * @param root a root [JComponent] for this [FocusModeTabs].
 * @param tabChangeListener is called when new [Key] is selected. [FocusModeTabs] insures
 *   [tabChangeListener] is not called twice if same [Key] set twice.
 */
internal class FocusModeTabs<Key : FocusKey>(
  private val root: JComponent,
  private val selectedProvider: (DataContext) -> Key?,
  private val keysProvider: (DataContext) -> Set<Key>,
  private val tabChangeListener: (DataContext, Key?) -> Unit,
) : JPanel(BorderLayout()) {

  val component: JComponent = this

  companion object {
    private const val MAX_TITLE_LENGTH = 50

    /** Truncate title string to have [MAX_TITLE_LENGTH] maximum length. */
    @VisibleForTesting
    fun String.truncate(): String {
      return if (this.length >= MAX_TITLE_LENGTH) this.take(MAX_TITLE_LENGTH) + "..." else this
    }
  }

  inner class TabLabelAction(val key: Key, val partOfOrganizationGroup: Boolean) :
    ToggleAction(), CustomComponentAction {
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
            }
          )
        }

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.text =
        key.settings
          .let {
            it.parameterName.takeIf { e.place == ActionPlaces.POPUP && partOfOrganizationGroup }
              ?: it.name
          }
          .truncate()
    }

    override fun actionPerformed(e: AnActionEvent) {
      updateSelectedKey(e, key)
      // If popup was opened - close it.
      allTabDropdown.popup?.cancel()
      updateFocus()
    }

    /**
     * Request the focus of this label even if it hasn't been clicked. Clicks are already giving
     * focus on the tab. Use this function when it is needed to focus on this tab without clicking
     * on it.
     *
     * @return true if the focus is correctly applied in the selected button, false otherwise
     */
    fun updateFocus(): Boolean {
      val sameTabInToolbar =
        previousToolbar?.components?.filterIsInstance<ActionButtonWithText>()?.firstOrNull {
          (it.action as? FocusModeTabs<*>.TabLabelAction)?.key == key
        }
      sameTabInToolbar?.let {
        focusOnComponent(it)
        return true
      }
      return false
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
    DumbAwareAction(message("action.focus.show.hidden"), null, AllIcons.General.ChevronDown) {

    var popup: ListPopup? = null

    override fun actionPerformed(e: AnActionEvent) {
      popup?.let {
        it.cancel()
        popup = null
      }
      popup =
        JBPopupFactory.getInstance()
          .createActionGroupPopup(null, moreActionGroup, e.dataContext, null, true)
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
      e.presentation.isVisible = centerPanel.width > this@FocusModeTabs.width
    }
  }

  var selectedKey: Key? = null
    private set

  /**
   * Notify [tabChangeListener] about change in [selectedKey]. Should only be called if
   * [selectedKey] is modified by interaction with [FocusModeTabs].
   */
  private fun updateSelectedKey(e: AnActionEvent, key: Key?) {
    // Avoid setting same value twice.
    if (selectedKey == key) return
    selectedKey = key
    tabChangeListener(e.dataContext, key)
  }

  private val allTabDropdown = AllTabsDropdown()
  private val labelActions: MutableMap<Key, TabLabelAction> = mutableMapOf()
  private var moreActionGroup: DefaultActionGroup = DefaultActionGroup()
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
      createToolbarWithNavigation(root, "Focus Tabs", FocusModeActionGroup(emptyList()))
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

    // If [FocusTabs] needs update, [previousToolbar] will be removed and new toolbar will be
    // created and added with available [labelActions].
    val needsUpdate =
      labelActions.keys.any { !keys.contains(it) } || keys.any { !labelActions.keys.contains(it) }

    selectedKey = selected

    // Only update toolbar if there are any changes.
    if (needsUpdate) {
      val allGroups = keys.findOrganizationGroups()
      labelActions.clear()
      keys.forEach {
        labelActions[it] = TabLabelAction(it, allGroups.contains(it.settings.organizationGroup))
      }
      // Remove previous toolbar if exists.
      previousToolbar?.let { centerPanel.remove(it) }
      // Create new toolbar.
      val toolbar =
        createToolbarWithNavigation(
            root,
            "Focus Tabs",
            FocusModeActionGroup(labelActions.values.toList()),
          )
          .component
          .apply { background = Colors.DEFAULT_BACKGROUND_COLOR }

      // Update "More" action group.
      moreActionGroup =
        DefaultActionGroup().apply {
          val groupsToAdd = allGroups.toMutableSet()
          var addSeparator = false
          labelActions.forEach {
            // Add header first if needed.
            if (groupsToAdd.contains(it.key.settings.organizationGroup)) {
              addSeparator = true
              groupsToAdd.remove(it.key.settings.organizationGroup)
              this.addSeparator()
              this.add(HeaderAction(it.key.settings.organizationName ?: it.key.settings.name))
            }
            // Add element within the group.
            if (allGroups.contains(it.key.settings.organizationGroup)) {
              addSeparator = true
              this.add(it.value)
            }
            // Add element outside the group.
            else {
              if (addSeparator) {
                this.addSeparator()
              }
              addSeparator = false
              this.add(it.value)
            }
          }
        }

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

  class HeaderAction(label: String) : AnActionButton(label.truncate()) {
    override fun actionPerformed(e: AnActionEvent) {}

    override fun updateButton(e: AnActionEvent) {
      super.updateButton(e)
      e.presentation.isEnabled = false
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }
  }

  private inner class FocusModeActionGroup(actions: List<AnAction>) : DefaultActionGroup(actions) {

    private var setFocusFirstTime = false

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.dataContext.let { updateKeys(keysProvider(it), selectedProvider(it)) }
      if (!setFocusFirstTime) {
        // If we are opening FocusTabs for the first time we want to make sure
        // that the focus of the group is on the selected button and the button
        // is visible.
        setFocusFirstTime = labelActions[selectedKey]?.updateFocus() ?: false
      }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }
  }

  @TestOnly fun getMoreActionsForTests() = moreActionGroup.childActionsOrStubs
}
