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
package com.android.tools.property.panel.impl.model

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CONSTRAINT_SET_START
import com.android.SdkConstants.ATTR_FONT_FAMILY
import com.android.SdkConstants.ATTR_LAYOUT_GRAVITY
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_TOP
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.ATTR_VISIBLE
import com.android.SdkConstants.VALUE_MATCH_PARENT
import com.android.SdkConstants.VALUE_TOP
import com.android.SdkConstants.VALUE_WRAP_CONTENT
import com.android.tools.property.panel.api.FilteredPTableModel
import com.android.tools.property.panel.api.GroupSpec
import com.android.tools.property.panel.impl.model.util.FakeNewPropertyItem
import com.android.tools.property.panel.impl.model.util.FakePTableModelUpdateListener
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.android.tools.property.panel.impl.model.util.FakePropertyModel
import com.android.tools.property.ptable.PTableColumn
import com.android.tools.property.ptable.PTableGroupItem
import com.android.tools.property.ptable.PTableItem
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val MARGIN_GROUP_NAME = "margin"

class FilteredPTableModelImplTest {
  private var model: FakePropertyModel? = null
  private var propHeight: FakePropertyItem? = null
  private var propWidth: FakePropertyItem? = null
  private var propGravity: FakePropertyItem? = null
  private var propText: FakePropertyItem? = null
  private var propVisible: FakePropertyItem? = null
  private var propMarginBottom: FakePropertyItem? = null
  private var propMarginEnd: FakePropertyItem? = null
  private var propMarginLeft: FakePropertyItem? = null
  private var propMarginRight: FakePropertyItem? = null
  private var propMarginStart: FakePropertyItem? = null
  private var propMarginTop: FakePropertyItem? = null
  private var propMargin: FakePropertyItem? = null
  private var alternateSortOrder: Comparator<PTableItem>? = null
  private val itemFilter: (FakePropertyItem) -> Boolean = { !it.value.isNullOrEmpty() }
  private val insertOp: (String, String) -> FakePropertyItem? = { name, value ->
    FakePropertyItem(ANDROID_URI, name, value)
  }
  private val deleteOp: (FakePropertyItem) -> Unit = { it.value = null }

  @Before
  fun init() {
    propHeight = FakePropertyItem(ANDROID_URI, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
    propWidth = FakePropertyItem(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT)
    propGravity = FakePropertyItem(ANDROID_URI, ATTR_LAYOUT_GRAVITY)
    propText = FakePropertyItem(ANDROID_URI, ATTR_TEXT, "Hello")
    propVisible = FakePropertyItem(ANDROID_URI, ATTR_VISIBLE)
    propMarginBottom = FakePropertyItem(ANDROID_URI, ATTR_LAYOUT_MARGIN_BOTTOM)
    propMarginEnd = FakePropertyItem(ANDROID_URI, ATTR_LAYOUT_MARGIN_END)
    propMarginLeft = FakePropertyItem(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT)
    propMarginRight = FakePropertyItem(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT)
    propMarginStart = FakePropertyItem(ANDROID_URI, ATTR_LAYOUT_MARGIN_START)
    propMarginTop = FakePropertyItem(ANDROID_URI, ATTR_LAYOUT_MARGIN_TOP)
    propMargin = FakePropertyItem(ANDROID_URI, ATTR_LAYOUT_MARGIN)

    model = FakePropertyModel()
    model!!.add(propHeight!!)
    model!!.add(propWidth!!)
    model!!.add(propGravity!!)
    model!!.add(propText!!)
    model!!.add(propVisible!!)
    model!!.add(propMarginBottom!!)
    model!!.add(propMarginEnd!!)
    model!!.add(propMarginLeft!!)
    model!!.add(propMarginRight!!)
    model!!.add(propMarginStart!!)
    model!!.add(propMarginTop!!)
    model!!.add(propMargin!!)
    alternateSortOrder =
      Comparator.comparingInt<PTableItem> { it.name.length }.thenComparing(PTableItem::name)
  }

  @After
  fun cleanUp() {
    model = null

    propHeight = null
    propWidth = null
    propGravity = null
    propText = null
    propVisible = null
    propMarginBottom = null
    propMarginEnd = null
    propMarginLeft = null
    propMarginRight = null
    propMarginStart = null
    propMarginTop = null
    propMargin = null
  }

  @Test
  fun testFilteredContent() {
    val tableModel =
      FilteredPTableModel(model!!, { !it.value.isNullOrEmpty() }, keepNewAfterFlyAway = false)
    assertThat(tableModel.items.map { it.name })
      .containsExactly(ATTR_LAYOUT_HEIGHT, ATTR_LAYOUT_WIDTH, ATTR_TEXT)
      .inOrder()
  }

  @Test
  fun testAddExistingProperty() {
    val tableModel =
      FilteredPTableModel(model!!, { !it.value.isNullOrEmpty() }, keepNewAfterFlyAway = false)
    val listener = FakePTableModelUpdateListener()
    val property = FakePropertyItem(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
    tableModel.addListener(listener)
    tableModel.addNewItem(property)
    assertThat(tableModel.items.map { it.name })
      .containsExactly(ATTR_LAYOUT_HEIGHT, ATTR_LAYOUT_WIDTH, ATTR_TEXT)
      .inOrder()
    assertThat(listener.updateCount).isEqualTo(0)
  }

  @Test
  fun testAddExistingPropertyAlternateOrder() {
    val tableModel =
      FilteredPTableModel(
        model!!,
        itemFilter,
        insertOp,
        deleteOp,
        alternateSortOrder!!,
        keepNewAfterFlyAway = false,
      )
    val listener = FakePTableModelUpdateListener()
    val property = FakePropertyItem(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
    tableModel.addListener(listener)
    tableModel.addNewItem(property)
    assertThat(tableModel.items.map { it.name })
      .containsExactly(ATTR_TEXT, ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT)
      .inOrder()
    assertThat(listener.updateCount).isEqualTo(0)
  }

  @Test
  fun testAddNonExistingProperty() {
    val tableModel =
      FilteredPTableModel(
        model!!,
        itemFilter,
        insertOp,
        deleteOp,
        alternateSortOrder!!,
        keepNewAfterFlyAway = false,
      )
    val listener = FakePTableModelUpdateListener()
    val property = FakePropertyItem(ANDROID_URI, ATTR_FONT_FAMILY, "Sans")
    tableModel.editedItem = propHeight
    tableModel.addListener(listener)
    tableModel.addNewItem(property)
    assertThat(tableModel.items.map { it.name })
      .containsExactly(ATTR_TEXT, ATTR_FONT_FAMILY, ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT)
      .inOrder()
    assertThat(listener.updateCount).isEqualTo(1)
    assertThat(listener.nextEditedItem).isEqualTo(propHeight)
  }

  @Test
  fun testAddNewProperty() {
    val tableModel =
      FilteredPTableModel(
        model!!,
        itemFilter,
        insertOp,
        deleteOp,
        alternateSortOrder!!,
        keepNewAfterFlyAway = false,
      )
    val listener = FakePTableModelUpdateListener()
    val property = FakeNewPropertyItem()
    tableModel.editedItem = propHeight
    tableModel.addListener(listener)
    tableModel.addNewItem(property)
    assertThat(tableModel.items.map { it.name })
      .containsExactly(ATTR_TEXT, ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, "")
      .inOrder()
    assertThat(listener.updateCount).isEqualTo(1)
    assertThat(listener.nextEditedItem).isEqualTo(property)
  }

  @Test
  fun testAddNonExistingPropertyToModelWithNewProperty() {
    val tableModel =
      FilteredPTableModel(
        model!!,
        itemFilter,
        insertOp,
        deleteOp,
        alternateSortOrder!!,
        keepNewAfterFlyAway = false,
      )
    tableModel.addNewItem(FakeNewPropertyItem())
    val listener = FakePTableModelUpdateListener()
    val property = FakePropertyItem(ANDROID_URI, ATTR_FONT_FAMILY, "Sans")
    tableModel.editedItem = propHeight
    tableModel.addListener(listener)
    tableModel.addNewItem(property)
    assertThat(tableModel.items.map { it.name })
      .containsExactly(ATTR_TEXT, ATTR_FONT_FAMILY, ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, "")
      .inOrder()
    assertThat(listener.updateCount).isEqualTo(1)
    assertThat(listener.nextEditedItem).isEqualTo(propHeight)
  }

  @Test
  fun testAddNonExistingPropertyTEndOfModelWithNewProperty() {
    val tableModel =
      FilteredPTableModel(
        model!!,
        itemFilter,
        insertOp,
        deleteOp,
        alternateSortOrder!!,
        keepNewAfterFlyAway = false,
      )
    tableModel.addNewItem(FakeNewPropertyItem())
    val listener = FakePTableModelUpdateListener()
    val property = FakePropertyItem(ANDROID_URI, ATTR_CONSTRAINT_SET_START, "@id/btn")
    tableModel.editedItem = propHeight
    tableModel.addListener(listener)
    tableModel.addNewItem(property)
    assertThat(tableModel.items.map { it.name })
      .containsExactly(
        ATTR_TEXT,
        ATTR_LAYOUT_WIDTH,
        ATTR_LAYOUT_HEIGHT,
        ATTR_CONSTRAINT_SET_START,
        "",
      )
      .inOrder()
    assertThat(listener.updateCount).isEqualTo(1)
    assertThat(listener.nextEditedItem).isEqualTo(propHeight)
  }

  @Test
  fun testIsCellEditable() {
    val tableModel =
      FilteredPTableModel(
        model!!,
        { true },
        insertOp,
        deleteOp,
        alternateSortOrder!!,
        listOf(MarginGroup()),
      )
    val property = FakeNewPropertyItem()
    val group = tableModel.items[1]
    assertThat(tableModel.isCellEditable(propWidth!!, PTableColumn.NAME)).isFalse()
    assertThat(tableModel.isCellEditable(propWidth!!, PTableColumn.VALUE)).isTrue()
    assertThat(tableModel.isCellEditable(group, PTableColumn.NAME)).isTrue()
    assertThat(tableModel.isCellEditable(group, PTableColumn.VALUE)).isTrue()
    assertThat(tableModel.isCellEditable(property, PTableColumn.NAME)).isTrue()
    assertThat(tableModel.isCellEditable(property, PTableColumn.VALUE)).isFalse()
    property.delegate = propGravity
    assertThat(tableModel.isCellEditable(property, PTableColumn.NAME)).isTrue()
    assertThat(tableModel.isCellEditable(property, PTableColumn.VALUE)).isTrue()
  }

  @Test
  fun testSupportsInsertableItems() {
    val tableModel1 =
      FilteredPTableModel(
        model!!,
        { true },
        insertOp,
        null,
        alternateSortOrder!!,
        listOf(MarginGroup()),
      )
    assertThat(tableModel1.supportsInsertableItems()).isTrue()
    val tableModel2 =
      FilteredPTableModel(
        model!!,
        { true },
        null,
        null,
        alternateSortOrder!!,
        listOf(MarginGroup()),
      )
    assertThat(tableModel2.supportsInsertableItems()).isFalse()
  }

  @Test
  fun testSupportsRemovableItems() {
    val tableModel1 =
      FilteredPTableModel(
        model!!,
        { true },
        null,
        deleteOp,
        alternateSortOrder!!,
        listOf(MarginGroup()),
      )
    assertThat(tableModel1.supportsRemovableItems()).isTrue()
    val tableModel2 =
      FilteredPTableModel(
        model!!,
        { true },
        null,
        null,
        alternateSortOrder!!,
        listOf(MarginGroup()),
      )
    assertThat(tableModel2.supportsRemovableItems()).isFalse()
  }

  @Test
  fun testAcceptMoveToNextEditor() {
    val tableModel =
      FilteredPTableModel(
        model!!,
        itemFilter,
        insertOp,
        deleteOp,
        alternateSortOrder!!,
        keepNewAfterFlyAway = false,
      )
    val property = FakeNewPropertyItem()
    assertThat(tableModel.acceptMoveToNextEditor(propWidth!!, PTableColumn.NAME)).isTrue()
    assertThat(tableModel.acceptMoveToNextEditor(propWidth!!, PTableColumn.VALUE)).isTrue()
    assertThat(tableModel.acceptMoveToNextEditor(property, PTableColumn.NAME)).isTrue()
    assertThat(tableModel.acceptMoveToNextEditor(property, PTableColumn.VALUE)).isTrue()
    property.delegate = propGravity
    assertThat(tableModel.acceptMoveToNextEditor(property, PTableColumn.NAME)).isTrue()
    assertThat(tableModel.acceptMoveToNextEditor(property, PTableColumn.VALUE)).isFalse()
  }

  @Test
  fun testRefreshWhenHeightIsRemoved() {
    val tableModel =
      FilteredPTableModel(
        model!!,
        itemFilter,
        insertOp,
        deleteOp,
        alternateSortOrder!!,
        keepNewAfterFlyAway = false,
      )
    val listener = FakePTableModelUpdateListener()
    tableModel.addListener(listener)
    tableModel.editedItem = propWidth

    propHeight!!.value = ""
    tableModel.refresh()
    assertThat(tableModel.items.map { it.name })
      .containsExactly(ATTR_TEXT, ATTR_LAYOUT_WIDTH)
      .inOrder()
    assertThat(listener.updateCount).isEqualTo(1)
    assertThat(listener.nextEditedItem).isEqualTo(propWidth)
  }

  @Test
  fun testRefreshWhenWidthIsEditedAndRemoved() {
    val tableModel =
      FilteredPTableModel(
        model!!,
        itemFilter,
        insertOp,
        deleteOp,
        alternateSortOrder!!,
        keepNewAfterFlyAway = false,
      )
    val listener = FakePTableModelUpdateListener()
    tableModel.addListener(listener)
    tableModel.editedItem = propWidth

    propWidth!!.value = ""
    tableModel.refresh()
    assertThat(tableModel.items.map { it.name })
      .containsExactly(ATTR_TEXT, ATTR_LAYOUT_HEIGHT)
      .inOrder()
    assertThat(listener.updateCount).isEqualTo(1)
    assertThat(listener.nextEditedItem?.name).isEqualTo(ATTR_LAYOUT_HEIGHT)
  }

  @Test
  fun testRefreshWhenGravityIsAssigned() {
    val tableModel =
      FilteredPTableModel(
        model!!,
        itemFilter,
        insertOp,
        deleteOp,
        alternateSortOrder!!,
        keepNewAfterFlyAway = false,
      )
    val listener = FakePTableModelUpdateListener()
    tableModel.addListener(listener)
    tableModel.editedItem = propText

    propGravity!!.value = VALUE_TOP
    tableModel.refresh()
    assertThat(tableModel.items.map { it.name })
      .containsExactly(ATTR_TEXT, ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, ATTR_LAYOUT_GRAVITY)
      .inOrder()
    assertThat(listener.updateCount).isEqualTo(1)
    assertThat(listener.nextEditedItem).isEqualTo(propText)
  }

  @Test
  fun testSortedGroup() {
    val tableModel =
      FilteredPTableModel(
        model!!,
        { true },
        insertOp,
        deleteOp,
        alternateSortOrder!!,
        listOf(MarginGroup()),
        false,
      )
    val items = tableModel.items
    assertThat(items.map { it.name })
      .containsExactly(
        ATTR_TEXT,
        MARGIN_GROUP_NAME,
        ATTR_VISIBLE,
        ATTR_LAYOUT_WIDTH,
        ATTR_LAYOUT_HEIGHT,
        ATTR_LAYOUT_GRAVITY,
      )
      .inOrder()
    val group = items[1] as PTableGroupItem
    assertThat(group.children.map { it.name })
      .containsExactly(
        ATTR_LAYOUT_MARGIN,
        ATTR_LAYOUT_MARGIN_END,
        ATTR_LAYOUT_MARGIN_TOP,
        ATTR_LAYOUT_MARGIN_LEFT,
        ATTR_LAYOUT_MARGIN_RIGHT,
        ATTR_LAYOUT_MARGIN_START,
        ATTR_LAYOUT_MARGIN_BOTTOM,
      )
      .inOrder()
  }

  @Test
  fun testCustomCursor() {
    val hasCustomCursor: (FakePropertyItem) -> Boolean = { it.name == ATTR_TEXT }
    val tableModel =
      FilteredPTableModel(
        model!!,
        { true },
        insertOp,
        deleteOp,
        alternateSortOrder!!,
        listOf(MarginGroup()),
        hasCustomCursor = hasCustomCursor,
      )
    val group = tableModel.items.single { it.name == "margin" }
    val text = tableModel.items.single { it.name == propText!!.name }
    val height = tableModel.items.single { it.name == propHeight!!.name }
    assertThat(tableModel.hasCustomCursor(group, PTableColumn.VALUE)).isFalse()
    assertThat(tableModel.hasCustomCursor(text, PTableColumn.VALUE)).isTrue()
    assertThat(tableModel.hasCustomCursor(height, PTableColumn.VALUE)).isFalse()
  }

  private inner class MarginGroup : GroupSpec<FakePropertyItem> {
    override val name = "margin"

    override val value: String
      get() =
        "[${part(propMargin)}, ${part(propMarginLeft, propMarginStart)}, " +
          "${part(propMarginTop)}, ${part(propMarginRight, propMarginEnd)}, ${part(propMarginBottom)}]"

    override val itemFilter: (FakePropertyItem) -> Boolean
      get() = {
        it == propMargin ||
          it == propMarginLeft ||
          it == propMarginRight ||
          it == propMarginStart ||
          it == propMarginEnd ||
          it == propMarginTop ||
          it == propMarginBottom
      }

    override val comparator: Comparator<PTableItem>
      get() = alternateSortOrder!!

    private fun part(property: FakePropertyItem?, override: FakePropertyItem? = null): String {
      return override?.value ?: property?.value ?: "?"
    }
  }
}
