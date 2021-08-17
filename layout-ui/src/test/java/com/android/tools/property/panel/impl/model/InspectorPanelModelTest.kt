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

import com.android.SdkConstants
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_BACKGROUND_TINT
import com.android.SdkConstants.ATTR_COLOR
import com.android.SdkConstants.ATTR_TEXT
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.property.panel.impl.model.util.FakePTableModel
import com.android.tools.property.panel.impl.model.util.FakePropertyEditorModel
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.android.tools.property.panel.impl.model.util.FakeTableLineModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.verify

class InspectorPanelModelTest {

  class Inspector {
    val model = InspectorPanelModel()

    private val colorProperty = FakePropertyItem(SdkConstants.ANDROID_URI, ATTR_COLOR, "#00FF00")
    private val backgroundProperty = FakePropertyItem(SdkConstants.TOOLS_URI, ATTR_BACKGROUND, "#00FF00")
    private val textProperty = FakePropertyItem(SdkConstants.AUTO_URI, ATTR_TEXT, "hello")
    private val textAppProperty = FakePropertyItem(SdkConstants.AUTO_URI, "textApp", "")
    private val someProperty = FakePropertyItem("SomeNamespace", "some", "world")
    private val otherProperty = FakePropertyItem("OtherNamespace", "other", "cup")

    private val colorEditor = FakePropertyEditorModel(colorProperty)
    private val backgroundEditor = FakePropertyEditorModel(backgroundProperty)
    val textEditor = FakePropertyEditorModel(textProperty)
    val textAppEditor = FakePropertyEditorModel(textAppProperty)
    val someEditor = FakePropertyEditorModel(someProperty)
    private val otherEditor = FakePropertyEditorModel(otherProperty)

    private val properties = PropertiesComponentMock()

    val outerGroup = CollapsibleLabelModel("OuterGroup", null, true, properties)
    val colorItem = CollapsibleLabelModel(ATTR_COLOR, colorEditor, true, properties)
    val innerGroup = CollapsibleLabelModel("textApp", textAppEditor, true, properties)
    val backgroundItem = CollapsibleLabelModel(ATTR_BACKGROUND_TINT, backgroundEditor, true, properties)
    val textItem = CollapsibleLabelModel(ATTR_TEXT, textEditor, true, properties)
    val someItem = CollapsibleLabelModel("some", someEditor, true, properties)
    val otherItem = CollapsibleLabelModel("other", otherEditor, false, properties)
    val genericLine = GenericInspectorLineModel()
    val tableLineModel1 = FakeTableLineModel(FakePTableModel(false, emptyMap(), emptyList()), mock(), true)
    val tableLineModel2 = FakeTableLineModel(FakePTableModel(false, emptyMap(), emptyList()), mock(), false)

    init {
      outerGroup.makeExpandable(true)
      outerGroup.addChild(colorItem)
      outerGroup.addChild(innerGroup)
      outerGroup.addChild(someItem)
      innerGroup.makeExpandable(true)
      innerGroup.addChild(backgroundItem)
      innerGroup.addChild(textItem)
      model.add(outerGroup)
      model.add(colorItem)
      model.add(innerGroup)
      model.add(backgroundItem)
      model.add(textItem)
      model.add(genericLine)
      model.add(someItem)
      model.add(tableLineModel1)
      model.add(tableLineModel2)
      model.add(otherItem)
    }
  }

  @Test
  fun testFilter() {
    // setup
    val inspector = Inspector()
    inspector.innerGroup.expanded = false
    inspector.model.filter = "tex"

    // test
    assertThat(inspector.outerGroup.visible).isFalse()
    assertThat(inspector.colorItem.visible).isFalse()
    assertThat(inspector.innerGroup.visible).isTrue()
    assertThat(inspector.backgroundItem.visible).isFalse()
    assertThat(inspector.textItem.visible).isTrue()
    assertThat(inspector.genericLine.visible).isFalse()
    assertThat(inspector.someItem.visible).isFalse()
    assertThat(inspector.tableLineModel1.visible).isFalse()
    assertThat(inspector.tableLineModel1.filter).isEqualTo("tex")
    assertThat(inspector.tableLineModel2.visible).isFalse()
    assertThat(inspector.otherItem.visible).isFalse()
  }

  @Test
  fun testFilter2() {
    // setup
    val inspector = Inspector()
    inspector.innerGroup.expanded = false
    inspector.model.filter = "o"

    // test
    assertThat(inspector.outerGroup.visible).isFalse()
    assertThat(inspector.colorItem.visible).isTrue()
    assertThat(inspector.innerGroup.visible).isFalse()
    assertThat(inspector.backgroundItem.visible).isTrue()
    assertThat(inspector.textItem.visible).isFalse()
    assertThat(inspector.genericLine.visible).isFalse()
    assertThat(inspector.someItem.visible).isTrue()
    assertThat(inspector.tableLineModel1.visible).isFalse()
    assertThat(inspector.tableLineModel1.filter).isEqualTo("o")
    assertThat(inspector.tableLineModel2.visible).isFalse()
    assertThat(inspector.otherItem.visible).isFalse()
  }

  @Test
  fun testResetFilterKeepsInnerGroupCollapsed() {
    // setup
    val inspector = Inspector()
    inspector.innerGroup.expanded = false
    inspector.model.filter = "tex"

    // test
    inspector.model.filter = ""
    assertThat(inspector.backgroundItem.visible).isFalse()
    assertThat(inspector.textItem.visible).isFalse()
  }

  @Test
  fun testFilterCausesValueChangeNotification() {
    // setup
    val inspector = Inspector()
    val listener = mock<ValueChangedListener>()
    inspector.model.addValueChangedListener(listener)

    // test
    inspector.model.filter = "te"
    verify(listener).valueChanged()
  }

  @Test
  fun testEnterInFilterWithNoFilterSet() {
    val inspector = Inspector()
    assertThat(inspector.model.enterInFilter()).isFalse()
    assertThat(inspector.textAppEditor.focusWasRequested).isFalse()
  }

  @Test
  fun testEnterInFilterWithMultipleMatchingProperties() {
    val inspector = Inspector()
    inspector.model.filter = "tex"
    assertThat(inspector.model.enterInFilter()).isFalse()
    assertThat(inspector.textAppEditor.focusWasRequested).isFalse()
  }

  @Test
  fun testEnterInFilter() {
    val inspector = Inspector()
    inspector.model.filter = "textAp"
    inspector.model.enterInFilter()
    assertThat(inspector.model.enterInFilter()).isTrue()
    assertThat(inspector.textAppEditor.focusWasRequested).isTrue()
  }

  @Test
  fun testMoveToNextEditor() {
    val inspector = Inspector()
    inspector.model.moveToNextLineEditor(inspector.textItem)
    assertThat(inspector.someEditor.focusWasRequested).isTrue()
  }

  @Test
  fun testShowValues() {
    val inspector = Inspector()
    assertThat(inspector.textEditor.value).isEqualTo("hello")
    assertThat(inspector.someEditor.value).isEqualTo("world")
  }

  @Test
  fun testListenersAreConcurrentModificationSafe() {
    // Make sure that ConcurrentModificationException is NOT generated from the code below:
    val model = Inspector().model
    val listener = RecursiveValueChangedListener(model)
    model.addValueChangedListener(listener)
    model.filter = "More"
    assertThat(listener.called).isTrue()
  }

  private class RecursiveValueChangedListener(private val model: InspectorPanelModel) : ValueChangedListener {
    var called = false

    override fun valueChanged() {
      model.addValueChangedListener(RecursiveValueChangedListener(model))
      called = true
    }
  }
}
