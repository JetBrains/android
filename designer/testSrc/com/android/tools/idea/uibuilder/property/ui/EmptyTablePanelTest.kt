/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.ui

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_TEXT
import com.android.testutils.MockitoKt.mock
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.android.tools.property.panel.impl.model.util.FakeTableLineModel
import com.android.tools.property.ptable.PTableItem
import com.android.tools.property.ptable.PTableModel
import com.android.tools.property.ptable.PTableModelUpdateListener
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import kotlin.properties.Delegates
import org.junit.Test

class EmptyTablePanelTest {

  @Test
  fun testUpdates() {
    val addAction = AddAction()
    val pTableModel = MyPTableModel()
    val tableLineModel = FakeTableLineModel(pTableModel, mock(), false)
    val panel = EmptyTablePanel(addAction, tableLineModel)
    assertThat(panel.isVisible).isTrue()

    pTableModel.items = listOf(FakePropertyItem(ANDROID_URI, ATTR_TEXT))
    assertThat(panel.isVisible).isFalse()

    pTableModel.items = emptyList()
    assertThat(panel.isVisible).isTrue()
  }

  private class AddAction : AnAction(null, "Declared Attributes", null) {
    override fun actionPerformed(event: AnActionEvent) {
      TODO(
        "not implemented"
      ) // To change body of created functions use File | Settings | File Templates.
    }
  }

  private class MyPTableModel : PTableModel {
    private val listeners = mutableListOf<PTableModelUpdateListener>()

    override var items: List<PTableItem> by
      Delegates.observable(emptyList()) { _, _, _ -> fireUpdate() }

    override var editedItem: PTableItem? = null

    override fun addItem(item: PTableItem): PTableItem {
      val newItems = ArrayList(items)
      newItems.add(item)
      items = newItems
      return item
    }

    override fun removeItem(item: PTableItem) {
      val newItems = ArrayList(items)
      newItems.remove(item)
      items = newItems
    }

    override fun addListener(listener: PTableModelUpdateListener) {
      listeners.add(listener)
    }

    private fun fireUpdate() {
      listeners.forEach { it.itemsUpdated(true, null) }
    }
  }
}
