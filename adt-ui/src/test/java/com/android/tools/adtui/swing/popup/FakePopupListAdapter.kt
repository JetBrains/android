/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.adtui.swing.popup

import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.ui.GenericListComponentUpdater
import com.intellij.openapi.ui.JBListUpdater
import com.intellij.openapi.ui.ListComponentUpdater
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.ui.popup.PopupChooserBuilder.PopupComponentAdapter
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ListUtil
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.speedSearch.ListWithFilter
import com.intellij.util.Consumer
import com.intellij.util.ObjectUtils
import java.awt.event.KeyEvent
import java.awt.event.MouseListener
import java.util.function.Predicate
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.ListCellRenderer
import javax.swing.border.Border

// The code here is copied from the package-private class com.intellij.ui.popup.PopupListAdapter
class FakePopupListAdapter<T>(val builder: PopupChooserBuilder<T>, val list: JList<T>) : PopupComponentAdapter<T> {
  private lateinit var listWithFilter: ListWithFilter<T>

  override fun getComponent() = list

  override fun setRenderer(renderer: ListCellRenderer<in T>?) {
    list.cellRenderer = renderer
  }

  override fun setItemChosenCallback(callback: Consumer<in T>) {
    builder.setItemChoosenCallback {
      list.selectedValue?.let { callback.consume(it) }
    }
  }

  override fun setItemsChosenCallback(callback: Consumer<in MutableSet<T>>) {
    builder.setItemChoosenCallback {
      callback.consume(list.selectedValuesList?.toMutableSet() ?: mutableSetOf())
    }
  }

  override fun createScrollPane(): JScrollPane {
    return listWithFilter.scrollPane
  }

  override fun hasOwnScrollPane() = true

  override fun getKeyEventHandler(): Predicate<KeyEvent> {
    return Predicate { obj: KeyEvent -> obj.isConsumed }
  }

  override fun buildFinalComponent(): JComponent {
    @Suppress("UNCHECKED_CAST") listWithFilter = ListWithFilter.wrap(list, ListWrapper(builder, list),
                                                                     builder.itemsNamer) as ListWithFilter<T>
    listWithFilter.setAutoPackHeight(builder.isAutoPackHeightOnFiltering)
    return listWithFilter
  }

  override fun addMouseListener(listener: MouseListener?) {
    list.addMouseListener(listener)
  }

  override fun autoSelect() {
    if (list.selectedIndex == -1) {
      list.selectedIndex = 0
    }
  }

  override fun getBackgroundUpdater(): GenericListComponentUpdater<T>? {
    return JBListUpdater(list as JBList<*>?)
  }

  override fun setSelectedValue(preselection: T, shouldScroll: Boolean) {
    list.setSelectedValue(preselection, shouldScroll)
  }

  override fun setItemSelectedCallback(c: Consumer<in T>) {
    list.addListSelectionListener {
      val selectedValue: T = list.selectedValue
      c.consume(selectedValue)
    }
  }

  override fun setSelectionMode(selection: Int) {
    list.selectionMode = selection
  }

  override fun checkResetFilter(): Boolean {
    return listWithFilter.resetFilter()
  }
}

private class ListWrapper<T>(
  builder: PopupChooserBuilder<T>,
  private val list: JList<T>
) : JBScrollPane(-1), DataProvider {
  init {
    list.visibleRowCount = builder.visibleRowCount
    setViewportView(list)
    if (builder.isAutoselectOnMouseMove) {
      ListUtil.installAutoSelectOnMouseMove(list)
    }
    ScrollingUtil.installActions(list)
    border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
  }

  override fun getData(dataId: String): Any? {
    return getDataImplForList(list, dataId)
  }

  override fun setBorder(border: Border) {
    if (list != null) {
      list.border = border
    }
  }

  override fun requestFocus() {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
      IdeFocusManager.getGlobalInstance().requestFocus(list, true)
    }
  }

  @Synchronized
  override fun addMouseListener(l: MouseListener?) {
    list.addMouseListener(l)
  }

  private fun getDataImplForList(list: JList<*>, dataId: String): Any? {
    if (PlatformCoreDataKeys.SELECTED_ITEM.`is`(dataId)) {
      val index = list.selectedIndex
      return if (index > -1) list.selectedValue else ObjectUtils.NULL
    }
    else if (PlatformCoreDataKeys.SELECTED_ITEMS.`is`(dataId)) {
      return list.selectedValuesList.map {
        it ?: ObjectUtils.NULL
      }.toTypedArray()
    }
    return null
  }
}