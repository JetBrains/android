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

import com.android.tools.idea.common.editor.ActionsToolbar
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.preview.Colors
import com.android.tools.idea.preview.actions.findPreviewManager
import com.android.tools.idea.preview.flow.PreviewFlowManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.preview.PreviewElement
import com.intellij.openapi.actionSystem.DataContext
import java.awt.BorderLayout
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent
import javax.swing.JPanel
import org.jetbrains.annotations.TestOnly

/**
 * If Focus mode is enabled, one preview at a time is available with dropdown to select between
 * them. Focus mode is always enabled for Essentials mode. To listen for selection changes, use
 * [addSelectionListener].
 */
class FocusMode(rootComponent: JComponent, vararg additionalComponents: JComponent) {

  /**
   * Listener for selection changes in the [FocusModeTabs]. When a new [PreviewElementKey] is
   * selected, it updates the [PreviewModeManager] to set the mode to [PreviewMode.Focus] with the
   * selected element.
   */
  private val selectionListener: (DataContext, PreviewElementKey?) -> Unit = { dataContext, key ->
    val previewElement = key?.element
    dataContext.findPreviewManager(PreviewModeManager.KEY)?.let { previewManager ->
      previewElement?.let { previewManager.setMode(PreviewMode.Focus(previewElement)) }
    }
  }

  /**
   * Additional listeners for selection changes in the [FocusModeTabs]. These listeners are notified
   * after the internal [selectionListener] has been executed.
   */
  private val additionalSelectionListeners = CopyOnWriteArrayList<(PreviewElement<*>?) -> Unit>()

  /**
   * Provides the set of available [PreviewElementKey]s based on the current [DataContext].
   */
  private val keysProvider: (DataContext) -> Set<PreviewElementKey> = { dataContext ->
    dataContext
      .findPreviewManager(PreviewFlowManager.KEY)
      ?.allPreviewElementsFlow
      ?.value
      ?.asCollection()
      ?.map { element -> PreviewElementKey(element) }
      ?.toSet() ?: emptySet()
  }

  /**
   * Provides the currently selected [PreviewElementKey] based on the current [DataContext].
   */
  private val selectedProvider: (DataContext) -> PreviewElementKey? = { dataContext ->
    dataContext.findPreviewManager(PreviewModeManager.KEY)?.let { previewManager ->
      (previewManager.mode.value as? PreviewMode.Focus)?.selected?.let { PreviewElementKey(it) }
    }
  }

  private val focusModeTabs =
    FocusModeTabs(rootComponent, selectedProvider, keysProvider) { dataContext, key ->
        selectionListener(dataContext, key)
        additionalSelectionListeners.forEach { it(key?.element) }
      }
      .apply {
        component.border = ActionsToolbar.BORDER
        component.background = Colors.DEFAULT_BACKGROUND_COLOR
      }

  /** [JPanel] for [FocusMode]. */
  val component: JComponent = JPanel(BorderLayout())

  init {
    component.add(focusModeTabs, BorderLayout.NORTH)
    additionalComponents.forEach { component.add(it, BorderLayout.CENTER) }
  }

  /**
   * Simulates a selected [PreviewElementKey] change, firing the [selectionListener]. Intended to be
   * used in tests only.
   */
  @TestOnly
  fun triggerSelectionChange(context: DataContext, previewElement: PreviewElement<*>) {
    selectionListener(context, PreviewElementKey(previewElement))
  }

  @get:TestOnly
  val selectedKey: PreviewElementKey?
    get() = focusModeTabs.selectedKey

  fun addSelectionListener(listener: (PreviewElement<*>?) -> Unit) {
    additionalSelectionListeners.add(listener)
  }
}
