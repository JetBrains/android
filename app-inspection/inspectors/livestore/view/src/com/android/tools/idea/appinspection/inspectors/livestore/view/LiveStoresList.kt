/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.livestore.view

import com.android.tools.appinspection.livestore.protocol.LiveStoreDefinition
import com.android.tools.idea.appinspection.inspectors.livestore.model.LiveStoreInspectorClient
import com.intellij.ui.components.JBList
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * A UI list component that contains a list of all livestores.
 *
 * This class exposes a [component] which is a [JList] that one can listen to for selection changed
 * events.
 */
class LiveStoresList(client: LiveStoreInspectorClient) {
  private val storesList: JBList<LiveStoreDefinition>

  private class StoreRenderer : ListCellRenderer<LiveStoreDefinition> {
    private val defaultRenderer = DefaultListCellRenderer()
    override fun getListCellRendererComponent(list: JList<out LiveStoreDefinition>,
                                              value: LiveStoreDefinition,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      val label = defaultRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
      label.text = value.name
      return label
    }
  }

  init {
    val storesListModel = DefaultListModel<LiveStoreDefinition>()
    storesList = JBList(storesListModel)
    storesList.cellRenderer = StoreRenderer()

    client.addStoresChangedListener {
      storesListModel.clear()
      client.stores.forEach { storesListModel.addElement(it) }
      storesList.selectedIndex = 0
    }
  }

  val component: JList<LiveStoreDefinition> = storesList
}