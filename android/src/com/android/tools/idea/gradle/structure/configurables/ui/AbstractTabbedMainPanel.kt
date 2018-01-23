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
import com.intellij.openapi.util.ActionCallback
import com.intellij.ui.TabbedPaneWrapper
import com.intellij.ui.navigation.History
import com.intellij.ui.navigation.Place
import com.intellij.ui.navigation.Place.goFurther
import com.intellij.ui.navigation.Place.queryFurther

/**
 * A base class for multi-tab configuration panels implementing [Place.Navigator] interface to maintain navigation history.
 *
 * Implementations should add their tabs by calling [addTab] method.
 */
abstract class AbstractTabbedMainPanel(
    context: PsContext,
    private val placeName: String
) : AbstractMainPanel(context), PanelWithUiState {

  private var inQuietSelection = false

  @Suppress("LeakingThis")
  private val tabbedPane = TabbedPaneWrapper(this).also {
    add(it.component)
  }

  private val tabPanels: MutableList<ModelPanel<*>> = mutableListOf()

  protected fun <T> addTab(panel: ModelPanel<T>) {
    tabbedPane.addTab(panel.title, panel.createComponent())
    tabPanels.add(panel)
  }

  override fun dispose() = Unit

  override fun setHistory(history: History?) {
    if (history != null) {
      super.setHistory(history)
      tabPanels.forEach { it.setHistory(history) }
      tabbedPane.addChangeListener {
        if (!inQuietSelection) {
          PsUISettings.getInstance().setLastSelectedTab(tabbedPane.selectedTitle.orEmpty())
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
    val panel = findPanel(PsUISettings.getInstance().getLastSelectedTab())
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