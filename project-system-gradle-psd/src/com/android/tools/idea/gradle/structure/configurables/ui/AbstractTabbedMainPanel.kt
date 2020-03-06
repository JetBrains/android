/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.structure.configurables.ui.CrossModuleUiStateComponent
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.navigation.History
import com.intellij.ui.navigation.Place
import com.intellij.ui.navigation.Place.goFurther
import com.intellij.ui.navigation.Place.queryFurther
import javax.swing.SwingConstants

/**
 * A base class for multi-tab configuration panels implementing [Place.Navigator] interface to maintain navigation history.
 *
 * Implementations should add their tabs by calling [addTab] method.
 */
abstract class AbstractTabbedMainPanel(
    context: PsContext,
    private val placeName: String
) : AbstractMainPanel(context), CrossModuleUiStateComponent {

  private var inQuietSelection = false

  private val tabbedPane = JBTabbedPane(SwingConstants.TOP).also {
    it.tabComponentInsets = null
    add(it)
    it.addChangeListener {
      if (topLevelAncestor != null) {
        ensureSelectedTabComponentInstantiated()
        context.project.ideProject.logUsageTopNavigateTo(getCurrentModelTab())
      }
    }
  }

  private val tabPanels: MutableList<ModelPanel<*>> = mutableListOf()

  protected fun <T> addTab(panel: ModelPanel<T>) {
    tabbedPane.addTab(panel.title, Wrapper())
    tabPanels.add(panel)
    Disposer.register(this, panel)
  }

  private fun getModelPanelAt(index: Int): ModelPanel<*> = index.takeIf { it >= 0 }.let { tabPanels[index] }

  private fun getCurrentModelTab(): ModelPanel<*> = getModelPanelAt(tabbedPane.selectedIndex)

  private fun ensureSelectedTabComponentInstantiated() {
    val selectedIndex = tabbedPane.selectedIndex
    if (selectedIndex >= 0) {
      val wrapper = tabbedPane.getComponentAt(selectedIndex) as Wrapper
      if (wrapper.componentCount == 0) {
        wrapper.setContent(tabPanels[selectedIndex].getComponent())
      }
    }
  }

  override fun addNotify() {
    super.addNotify()
    ensureSelectedTabComponentInstantiated()
  }

  override fun dispose() = Unit

  override fun setHistory(history: History?) {
    if (history != null) {
      super.setHistory(history)
      tabPanels.forEach { it.setHistory(history) }
      tabbedPane.addChangeListener {
        if (!inQuietSelection) {
          context.uiSettings.setLastSelectedTab(tabbedPane.selectedTitle.orEmpty())
          history.pushQueryPlace()
        }
      }
    }
  }

  override fun queryPlace(place: Place) {
    place.putPath(placeName, tabbedPane.selectedTitle)
    val modelPanel = tabPanels.getOrNull(tabbedPane.selectedIndex)
    queryFurther(modelPanel, place)
  }

  override fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback {
    fun navigateToTab(panel: ModelPanel<*>): ActionCallback {
      tabbedPane.selectedTitle = panel.title
      return goFurther(panel, place, requestFocus)
    }

    val path = place?.getPath(placeName)
    val tabPanel = findPanel(path as String?)

    return if (tabPanel != null) {
      navigateToTab(tabPanel)
    }
    else {
      ActionCallback.DONE
    }
  }

  private fun findPanel(path: String?): ModelPanel<*>? = tabPanels.find { it.title == path }

  abstract fun PsUISettings.getLastSelectedTab(): String?
  abstract fun PsUISettings.setLastSelectedTab(value: String)

  override fun restoreUiState() {
    // First, restore the state of the tabs before they may try to override it on selection change.
    for (panelWithUiState in tabPanels.mapNotNull { it as? CrossModuleUiStateComponent }) {
      panelWithUiState.restoreUiState()
    }
    // Then restore the tab selection itself.
    val panel = findPanel(context.uiSettings.getLastSelectedTab())
    if (panel != null) {
      inQuietSelection = true
      try {
        tabbedPane.selectedTitle = panel.title
      }
      finally {
        inQuietSelection = false
      }
    }
  }
}

private var JBTabbedPane.selectedTitle: String
  get() = if (selectedIndex >= 0) getTitleAt(selectedIndex) else ""
  set(title) = (0 until tabCount).find { getTitleAt(it) == title }?.let { selectedIndex = it } ?: Unit
