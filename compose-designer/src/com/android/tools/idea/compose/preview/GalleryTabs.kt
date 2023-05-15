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
package com.android.tools.idea.compose.preview

import com.android.tools.adtui.util.ActionToolbarUtil
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.util.ui.JBFont
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

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
          ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
        )
        .apply { font = JBFont.medium() }

    override fun actionPerformed(e: AnActionEvent) {
      selectedKey = key
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      return selectedKey == key
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) selectedKey = key
    }
  }

  var selectedKey: Key? = null
    private set(value) {
      // Avoid setting same value twice.
      if (field == value) return
      field = value
      tabChangeListener(value)
    }

  private val labelActions: MutableMap<Key, TabLabelAction> = mutableMapOf()

  init {
    updateKeys(keys)
  }

  /**
   * If [GalleryTabs] needs update, [previousToolbar] will be removed and new toolbar will be
   * created and added with available [labelActions].
   */
  private var needsUpdate = false

  /**
   * Update all available [Key]s. Toolbar is recreated if there are any changes in [keys]. It also
   * insures that if there are no changes in [keys] toolbar will not be updated.
   */
  fun updateKeys(keys: Set<Key>) {
    // Remove all keys what don's exist anymore.
    val keysToRemove = labelActions.filterKeys { !keys.contains(it) }.keys
    keysToRemove.forEach {
      labelActions.remove(it)
      needsUpdate = true
    }
    // Add new added keys.
    keys.forEach {
      labelActions.computeIfAbsent(it) { key -> TabLabelAction(key).also { needsUpdate = true } }
    }
    // Only update toolbar if there are any changes.
    if (needsUpdate) {
      needsUpdate = false
      // Remove previous toolbar if exists.
      previousToolbar?.let { remove(it) }
      // Create new toolbar.
      val toolbar = createToolbar(labelActions.values.toList())
      add(toolbar, BorderLayout.CENTER)
      previousToolbar = toolbar
      // If selectedKey was removed, select first key.
      selectedKey = if (keys.contains(selectedKey)) selectedKey else keys.firstOrNull()
    }
  }

  private var previousToolbar: JComponent? = null

  /** Creates [ActionToolbarImpl] with [actions]. */
  private fun createToolbar(actions: List<AnAction>) =
    ActionToolbarImpl("Gallery Tabs", DefaultActionGroup(actions), true).apply {
      targetComponent = root
      ActionToolbarUtil.makeToolbarNavigable(this)
      layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
      setMinimumButtonSize(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    }
}
