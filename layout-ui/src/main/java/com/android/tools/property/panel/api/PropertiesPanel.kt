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
package com.android.tools.property.panel.api

import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.property.panel.impl.ui.PropertiesPage
import com.android.tools.property.panel.impl.ui.WatermarkPanel
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil.escapeProperty
import java.awt.BorderLayout
import java.util.IdentityHashMap
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.properties.Delegates

private const val RECENT_TAB_PREFIX = "android.last.property.tab."
private const val PROPERTY_TAB_NAME = "tab.name"

/**
 * The top level class for creating UI classes and model classes for a properties panel.
 *
 * Creates the main [component] for the properties panel which at this point contains
 * a property inspector. The panel consists of a main view followed by a tabular view.
 *
 * The content of the inspector is controlled by a list of [PropertiesView]s which
 * must be added to this class using [addView].
 */
class PropertiesPanel<P: PropertyItem>(parentDisposable: Disposable) : Disposable, PropertiesModelListener<P> {

  private var activeModel: PropertiesModel<*>? = null
  private var activeView: PropertiesView<*>? = null
  private val views = IdentityHashMap<PropertiesModel<P>, PropertiesView<P>>()
  private val tabbedPanel = CommonTabbedPane()
  private val watermark = WatermarkPanel()
  private val hidden = JPanel()
  private var updatingPageVisibility = false

  @VisibleForTesting
  val mainPage = PropertiesPage(this)

  @VisibleForTesting
  val pages = mutableListOf<PropertiesPage>()

  val component = JPanel(BorderLayout())
  var filter: String by Delegates.observable("") { _, oldValue, newValue -> filterChanged(oldValue, newValue) }

  init {
    hidden.isVisible = false
    Disposer.register(parentDisposable, this)
    tabbedPanel.addChangeListener { saveMostRecentTabPage() }
  }

  fun addView(view: PropertiesView<P>) {
    views[view.model] = view
    view.model.addListener(this)
  }

  override fun propertiesGenerated(model: PropertiesModel<P>) {
    populateInspector(model)
    if (filter.isNotEmpty()) {
      filterChanged("", filter)
    }
  }

  override fun propertyValuesChanged(model: PropertiesModel<P>) {
    if (model == activeModel) {
      mainPage.propertyValuesChanged()
      pages.forEach { it.propertyValuesChanged() }
    }
  }

  fun enterInFilter(): Boolean {
    if (mainPage.enterInFilter()) {
      return true
    }
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
    mainPage.clear()
    mainPage.dataProviderDelegate = activeModel as? DataProvider
    view.main.attachToInspector(mainPage)
    if (view.tabs.isNotEmpty()) {
      mainPage.addSeparatorBeforeTabs()
    }
    pages.forEach { it.clear() }
    for (index in view.tabs.indices) {
      val tab = view.tabs[index]
      val page = lookupPage(index)
      tab.attachToInspector(page)
      page.component.putClientProperty(PROPERTY_TAB_NAME, tab.name)
      page.dataProviderDelegate = activeModel as? DataProvider
    }
    pages.subList(view.tabs.size, pages.size).clear()
    val preferredTab = PropertiesComponent.getInstance().getValue(RECENT_TAB_PREFIX + escapeProperty(view.id, true))
    watermark.model = view.watermark
    updatePageVisibility(preferredTab)
  }

  private fun saveMostRecentTabPage() {
    if (updatingPageVisibility) {
      return
    }
    val tabName = selectedTab()
    val view = activeView ?: return
    PropertiesComponent.getInstance().setValue(RECENT_TAB_PREFIX + escapeProperty(view.id, true), tabName)
  }

  @VisibleForTesting
  fun selectedTab(): String? {
    val layout = component.layout as? BorderLayout ?: return null
    var component = layout.getLayoutComponent(BorderLayout.CENTER) ?: return null
    if (component == tabbedPanel) {
      component = tabbedPanel.selectedComponent
    }
    return (component as? JComponent)?.getClientProperty(PROPERTY_TAB_NAME) as? String
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
  private fun updatePageVisibility(preferredTabName: String? = null) {
    val view = activeView ?: return
    val visibleTabCount = findVisibleTabCount()
    var preferredTabIndex = -1
    assert(view.tabs.size == pages.size)
    updatingPageVisibility = true
    try {
      component.removeAll()
      tabbedPanel.removeAll()
      if ((filter.isEmpty() || view.main.searchable) && !mainPage.isEmpty) {
        component.add(mainPage.component, BorderLayout.NORTH)
      }
      else {
        hidden.add(mainPage.component)
      }
      for (index in view.tabs.indices) {
        val tab = view.tabs[index]
        val page = pages[index]
        val tabVisible = (filter.isEmpty() || tab.searchable) && !page.isEmpty
        page.component.isVisible = tabVisible
        when {
          !tabVisible -> hidden.add(page.component)
          visibleTabCount == 1 -> component.add(page.component, BorderLayout.CENTER)
          else -> {
            tabbedPanel.add(page.component, tab.name)
            if (tab.name == preferredTabName) {
              preferredTabIndex = tabbedPanel.componentCount - 1
            }
          }
        }
      }
      if (visibleTabCount < 2) {
        hidden.add(tabbedPanel)
      }
      else {
        component.add(tabbedPanel, BorderLayout.CENTER)
        if (preferredTabIndex >= 0) {
          tabbedPanel.selectedIndex = preferredTabIndex
        }
      }
      if (component.componentCount == 0) {
        component.add(watermark, BorderLayout.CENTER)
      }
      else {
        hidden.add(watermark)
      }
      component.add(hidden, BorderLayout.SOUTH)
      component.revalidate()
      component.repaint()
    }
    finally {
      updatingPageVisibility = false
    }
  }

  private fun findVisibleTabCount(): Int {
    val view = activeView ?: return 0
    return view.tabs.indices.count { (filter.isEmpty() || view.tabs[it].searchable) && !pages[it].isEmpty }
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
