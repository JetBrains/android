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

import com.android.SdkConstants
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.common.property2.impl.model.util.PropertyModelTestUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class InspectorPanelModelTest {

  class Inspector {
    val model = InspectorPanelModel()

    private val colorProperty = PropertyModelTestUtil.makeProperty(SdkConstants.ANDROID_URI, "color", "#00FF00")
    private val backgroundProperty = PropertyModelTestUtil.makeProperty(SdkConstants.TOOLS_URI, "background", "#00FF00")
    private val textProperty = PropertyModelTestUtil.makeProperty(SdkConstants.AUTO_URI, "text", "hello")
    private val textAppProperty = PropertyModelTestUtil.makeProperty(SdkConstants.AUTO_URI, "textApp", "")
    private val someProperty = PropertyModelTestUtil.makeProperty("SomeNamespace", "some", "world")

    private val colorEditor = PropertyModelTestUtil.makePropertyEditorModel(colorProperty)
    private val backgroundEditor = PropertyModelTestUtil.makePropertyEditorModel(backgroundProperty)
    val textEditor = PropertyModelTestUtil.makePropertyEditorModel(textProperty)
    val textAppEditor = PropertyModelTestUtil.makePropertyEditorModel(textAppProperty)
    val someEditor = PropertyModelTestUtil.makePropertyEditorModel(someProperty)

    private val properties = PropertiesComponentMock()

    val outerGroup = CollapsibleLabelModel("OuterGroup", null, properties)
    val colorItem = CollapsibleLabelModel("color", colorEditor, properties)
    val innerGroup = CollapsibleLabelModel("textApp", textAppEditor, properties)
    val backgroundItem = CollapsibleLabelModel("backgroundTint", backgroundEditor, properties)
    val textItem = CollapsibleLabelModel("text", textEditor, properties)
    val someItem = CollapsibleLabelModel("some", someEditor, properties)
    val genericLine = GenericInspectorLineModel()

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
    val listener = mock(ValueChangedListener::class.java)
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
}
