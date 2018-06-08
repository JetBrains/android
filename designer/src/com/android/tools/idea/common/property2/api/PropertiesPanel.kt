/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.property2.api

import com.android.annotations.VisibleForTesting
import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.idea.common.property2.impl.ui.PropertiesPage
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.BorderLayout
import java.util.*
import javax.swing.JPanel
import kotlin.properties.Delegates

/**
 * The top level class for creating UI classes and model classes for a properties panel.
 *
 * Creates the main [component] for the properties panel which at this point contains
 * a property inspector. Separate views such as a tabular view may be added at a later
 * point.
 * The content of the inspector is controlled by a list of [PropertiesView]s which
 * must be added to this class using [addView].
 */
class PropertiesPanel(parentDisposable: Disposable) : Disposable, PropertiesModelListener {

  private var activeModel: PropertiesModel<*>? = null
  private var activeView: PropertiesView<*>? = null
  private val views = IdentityHashMap<PropertiesModel<*>, PropertiesView<*>>()
  private val tabbedPanel = CommonTabbedPane()
  private val hidden = JPanel()

  @VisibleForTesting
  val pages = mutableListOf<PropertiesPage>()

  val component = JPanel(BorderLayout())
  var filter: String by Delegates.observable("", { _, oldValue, newValue -> filterChanged(oldValue, newValue) })

  init {
    hidden.isVisible = false
    Disposer.register(parentDisposable, this)
  }

  fun addView(view: PropertiesView<*>) {
    views[view.model] = view
    view.model.addListener(this)
  }

  override fun propertiesGenerated(model: PropertiesModel<*>) {
    populateInspector(model)
  }

  override fun propertyValuesChanged(model: PropertiesModel<*>) {
    if (model == activeModel) {
      pages.forEach { it.propertyValuesChanged() }
    }
  }

  fun enterInFilter(): Boolean {
    return pages.firstOrNull { it.component.isVisible }?.enterInFilter() == true
  }

  /**
   * Populate the inspector from the specified [model].
   *
   * Create a page for each tab with the components specified in the tab.
   */
  private fun populateInspector(model: PropertiesModel<*>) {
    val view = views[model] ?: return
    if (activeModel != model) {
      activeModel?.deactivate()
      activeModel = model
      activeView = view
    }
    pages.forEach { it.clear() }
    for (index in view.tabs.indices) {
      val tab = view.tabs[index]
      val page = lookupPage(index)
      tab.attachToInspector(page)
    }
    pages.subList(view.tabs.size, pages.size).clear()
    updatePageVisibility()
  }

  /**
   * Update the visibility of the current pages.
   *
   * This will be called after the inspector is repopulated and after a filter changed.
   * What the user will see depends on how many visible tabs we have.
   *  - If there are multiple visible tabs, add each tab page to the [tabbedPanel]
   *  - If there is only 1 visible tab, show the page of that tab and hide the other pages and the [tabbedPanel]
   *
   *  Hidden pages (and the [tabbedPanel]) are retained for quick display, and are kept in the
   *  swing component tree such that LookAndFeel changes are applied while they are hidden.
   *  The [hidden] panel is always hidden, and serves as the keeper of other hidden pages.
   */
  private fun updatePageVisibility() {
    val view = activeView ?: return
    val visibleTabCount = findVisibleTabCount()
    assert(view.tabs.size == pages.size)
    component.removeAll()
    tabbedPanel.removeAll()
    for (index in view.tabs.indices) {
      val tab = view.tabs[index]
      val page = pages[index]
      val tabVisible = filter.isEmpty() || tab.searchable
      page.component.isVisible = tabVisible
      when {
        !tabVisible -> hidden.add(page.component)
        visibleTabCount == 1 -> component.add(page.component, BorderLayout.CENTER)
        else -> tabbedPanel.add(page.component, tab.name)
      }
    }
    if (visibleTabCount < 2) {
      hidden.add(tabbedPanel)
    }
    else {
      component.add(tabbedPanel, BorderLayout.CENTER)
    }
    component.add(hidden, BorderLayout.SOUTH)
    component.revalidate()
    component.repaint()
  }

  private fun findVisibleTabCount(): Int {
    val view = activeView ?: return 0
    if (filter.isEmpty()) {
      return view.tabs.size
    }
    return view.tabs.count { it.searchable }
  }

  private fun filterChanged(oldValue: String, newValue: String) {
    for (page in pages) {
      page.filter = newValue
    }
    if (oldValue.isEmpty().xor(newValue.isEmpty())) {
      updatePageVisibility()
    }
  }

  private fun lookupPage(pageIndex: Int): PropertiesPage {
    while (pageIndex >= pages.size) {
      pages.add(PropertiesPage(this))
    }
    return pages[pageIndex]
  }

  override fun dispose() {
    views.keys.forEach { it.removeListener(this) }
    pages.forEach { it.clear() }
  }
}
