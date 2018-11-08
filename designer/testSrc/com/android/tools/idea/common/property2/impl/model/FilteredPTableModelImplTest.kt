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
package com.android.tools.idea.common.property2.impl.model

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_FONT_FAMILY
import com.android.SdkConstants.ATTR_LAYOUT_GRAVITY
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.ATTR_VISIBLE
import com.android.SdkConstants.VALUE_MATCH_PARENT
import com.android.SdkConstants.VALUE_TOP
import com.android.SdkConstants.VALUE_WRAP_CONTENT
import com.android.tools.adtui.ptable2.PTableColumn
import com.android.tools.idea.common.property2.impl.model.util.TestNewPropertyItem
import com.android.tools.idea.common.property2.impl.model.util.TestPTableModelUpdateListener
import com.android.tools.idea.common.property2.impl.model.util.TestPropertyItem
import com.android.tools.idea.common.property2.impl.model.util.TestPropertyModel
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class FilteredPTableModelImplTest {
  private var model: TestPropertyModel? = null
  private var propHeight: TestPropertyItem? = null
  private var propWidth: TestPropertyItem? = null
  private var propGravity: TestPropertyItem? = null
  private var propText: TestPropertyItem? = null
  private var propVisible: TestPropertyItem? = null

  @Before
  fun init() {
    propHeight = TestPropertyItem(ANDROID_URI, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
    propWidth = TestPropertyItem(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT)
    propGravity = TestPropertyItem(ANDROID_URI, ATTR_LAYOUT_GRAVITY)
    propText = TestPropertyItem(ANDROID_URI, ATTR_TEXT, "Hello")
    propVisible = TestPropertyItem(ANDROID_URI, ATTR_VISIBLE)

    model = TestPropertyModel()
    model!!.add(propHeight!!)
    model!!.add(propWidth!!)
    model!!.add(propGravity!!)
    model!!.add(propText!!)
    model!!.add(propVisible!!)
  }

  @After
  fun cleanUp() {
    model = null

    propHeight = null
    propWidth = null
    propGravity = null
    propText = null
    propVisible = null
  }

  @Test
  fun testFilteredContent() {
    val tableModel = FilteredPTableModelImpl(model!!, { !it.value.isNullOrEmpty() }, false)
    assertThat(tableModel.items).containsExactly(propWidth, propHeight, propText).inOrder()
  }

  @Test
  fun testAddExistingProperty() {
    val tableModel = FilteredPTableModelImpl(model!!, { !it.value.isNullOrEmpty() }, false)
    val listener = TestPTableModelUpdateListener()
    val property = TestPropertyItem(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
    tableModel.addListener(listener)
    tableModel.addNewItem(property)
    assertThat(tableModel.items).containsExactly(propWidth, propHeight, propText).inOrder()
    assertThat(listener.updateCount).isEqualTo(0)
  }

  @Test
  fun testAddNonExistingProperty() {
    val tableModel = FilteredPTableModelImpl(model!!, { !it.value.isNullOrEmpty() }, false)
    val listener = TestPTableModelUpdateListener()
    val property = TestPropertyItem(ANDROID_URI, ATTR_FONT_FAMILY, "Sans")
    tableModel.editedItem = propHeight
    tableModel.addListener(listener)
    tableModel.addNewItem(property)
    assertThat(tableModel.items).containsExactly(propWidth, propHeight, property, propText).inOrder()
    assertThat(listener.updateCount).isEqualTo(1)
    assertThat(listener.nextEditedItem).isEqualTo(propHeight)
  }

  @Test
  fun testAddNewProperty() {
    val tableModel = FilteredPTableModelImpl(model!!, { !it.value.isNullOrEmpty() }, false)
    val listener = TestPTableModelUpdateListener()
    val property = TestNewPropertyItem()
    tableModel.editedItem = propHeight
    tableModel.addListener(listener)
    tableModel.addNewItem(property)
    assertThat(tableModel.items).containsExactly(propWidth, propHeight, propText, property).inOrder()
    assertThat(listener.updateCount).isEqualTo(1)
    assertThat(listener.nextEditedItem).isEqualTo(property)
  }

  @Test
  fun testIsCellEditable() {
    val tableModel = FilteredPTableModelImpl(model!!, { !it.value.isNullOrEmpty() }, false)
    val property = TestNewPropertyItem()
    assertThat(tableModel.isCellEditable(propWidth!!, PTableColumn.NAME)).isFalse()
    assertThat(tableModel.isCellEditable(propWidth!!, PTableColumn.VALUE)).isTrue()
    assertThat(tableModel.isCellEditable(property, PTableColumn.NAME)).isTrue()
    assertThat(tableModel.isCellEditable(property, PTableColumn.VALUE)).isFalse()
    property.delegate = propGravity
    assertThat(tableModel.isCellEditable(property, PTableColumn.NAME)).isTrue()
    assertThat(tableModel.isCellEditable(property, PTableColumn.VALUE)).isTrue()
  }

  @Test
  fun testAcceptMoveToNextEditor() {
    val tableModel = FilteredPTableModelImpl(model!!, { !it.value.isNullOrEmpty() }, false)
    val property = TestNewPropertyItem()
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
    val tableModel = FilteredPTableModelImpl(model!!, { !it.value.isNullOrEmpty() }, false)
    val listener = TestPTableModelUpdateListener()
    tableModel.addListener(listener)
    tableModel.editedItem = propWidth

    propHeight!!.value = ""
    tableModel.refresh()
    assertThat(tableModel.items).containsExactly(propWidth, propText).inOrder()
    assertThat(listener.updateCount).isEqualTo(1)
    assertThat(listener.nextEditedItem).isEqualTo(propWidth)
  }

  @Test
  fun testRefreshWhenHeightIsEditedAndRemoved() {
    val tableModel = FilteredPTableModelImpl(model!!, { !it.value.isNullOrEmpty() }, false)
    val listener = TestPTableModelUpdateListener()
    tableModel.addListener(listener)
    tableModel.editedItem = propHeight

    propHeight!!.value = ""
    tableModel.refresh()
    assertThat(tableModel.items).containsExactly(propWidth, propText).inOrder()
    assertThat(listener.updateCount).isEqualTo(1)
    assertThat(listener.nextEditedItem).isEqualTo(propText)
  }

  @Test
  fun testRefreshWhenGravityIsAssigned() {
    val tableModel = FilteredPTableModelImpl(model!!, { !it.value.isNullOrEmpty() }, false)
    val listener = TestPTableModelUpdateListener()
    tableModel.addListener(listener)
    tableModel.editedItem = propText

    propGravity!!.value = VALUE_TOP
    tableModel.refresh()
    assertThat(tableModel.items).containsExactly(propWidth, propHeight, propGravity, propText).inOrder()
    assertThat(listener.updateCount).isEqualTo(1)
    assertThat(listener.nextEditedItem).isEqualTo(propText)
  }
}
