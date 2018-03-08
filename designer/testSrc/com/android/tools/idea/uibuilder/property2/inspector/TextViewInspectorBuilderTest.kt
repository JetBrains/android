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
package com.android.tools.idea.uibuilder.property2.inspector

import com.android.SdkConstants.*
import com.android.tools.idea.common.property2.api.FormModel
import com.android.tools.idea.common.property2.api.PropertyEditorModel
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.model.HorizontalEditorPanelModel
import com.android.tools.idea.uibuilder.property2.model.ToggleButtonPropertyEditorModel
import com.android.tools.idea.uibuilder.property2.testutils.InspectorTestUtil
import com.android.tools.idea.uibuilder.property2.testutils.LineType
import com.google.common.truth.Truth.assertThat
import icons.StudioIcons.LayoutEditor.Properties.*
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito.mock
import javax.swing.Icon

class TextViewInspectorBuilderTest: AndroidTestCase() {

  fun testAvailableWithRequiredPropertiesPresent() {
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val formModel = mock(FormModel::class.java)
    val builder = TextViewInspectorBuilder(util.editorProvider, formModel)
    addRequiredProperties(util)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(11)
    assertThat(util.inspector.lines[0].type).isEqualTo(LineType.SEPARATOR)
    assertThat(util.inspector.lines[1].type).isEqualTo(LineType.TITLE)
    assertThat(util.inspector.lines[1].title).isEqualTo("TextView")
    assertThat(util.inspector.lines[2].editorModel?.property?.name).isEqualTo(ATTR_TEXT)
    assertThat(util.inspector.lines[2].editorModel?.property?.namespace).isEqualTo(ANDROID_URI)
    assertThat(util.inspector.lines[3].editorModel?.property?.name).isEqualTo(ATTR_TEXT)
    assertThat(util.inspector.lines[3].editorModel?.property?.namespace).isEqualTo(TOOLS_URI)
    assertThat(util.inspector.lines[4].editorModel?.property?.name).isEqualTo(ATTR_CONTENT_DESCRIPTION)
    assertThat(util.inspector.lines[5].editorModel?.property?.name).isEqualTo(ATTR_TEXT_APPEARANCE)
    assertThat(util.inspector.lines[6].editorModel?.property?.name).isEqualTo(ATTR_TYPEFACE)
    assertThat(util.inspector.lines[7].editorModel?.property?.name).isEqualTo(ATTR_TEXT_SIZE)
    assertThat(util.inspector.lines[8].editorModel?.property?.name).isEqualTo(ATTR_LINE_SPACING_EXTRA)
    assertThat(util.inspector.lines[9].editorModel?.property?.name).isEqualTo(ATTR_TEXT_COLOR)
    assertThat(util.inspector.lines[10].editorModel?.property?.name).isEqualTo(ATTR_TEXT_STYLE)
  }

  fun testOptionalPropertiesPresent() {
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val formModel = mock(FormModel::class.java)
    val builder = TextViewInspectorBuilder(util.editorProvider, formModel)
    addRequiredProperties(util)
    util.addProperty(ANDROID_URI, ATTR_FONT_FAMILY, NelePropertyType.STRING)
    addOptionalProperties(util)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(13)
    assertThat(util.inspector.lines[6].editorModel?.property?.name).isEqualTo(ATTR_FONT_FAMILY)
    assertThat(util.inspector.lines[12].editorModel?.property?.name).isEqualTo(ATTR_TEXT_ALIGNMENT)
  }

  fun testTextStyleModel() {
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val formModel = mock(FormModel::class.java)
    val builder = TextViewInspectorBuilder(util.editorProvider, formModel)
    addRequiredProperties(util)
    addOptionalProperties(util)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(13)
    assertThat(util.inspector.lines[6].editorModel?.property?.name).isEqualTo(ATTR_FONT_FAMILY)
    val line = util.inspector.lines[11].editorModel as HorizontalEditorPanelModel
    assertThat(line.models).hasSize(3)
    checkToggleButtonModel(line.models[0], "Bold", TEXT_STYLE_BOLD, "true", "false")
    checkToggleButtonModel(line.models[1], "Italics", TEXT_STYLE_ITALIC, "true", "false")
    checkToggleButtonModel(line.models[2], "All Caps", TEXT_STYLE_UPPERCASE, "true", "false")
  }

  fun testTextAlignmentModel() {
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val formModel = mock(FormModel::class.java)
    val builder = TextViewInspectorBuilder(util.editorProvider, formModel)
    addRequiredProperties(util)
    addOptionalProperties(util)
    builder.attachToInspector(util.inspector, util.properties)
    val line = util.inspector.lines[12].editorModel as HorizontalEditorPanelModel
    assertThat(line.models).hasSize(5)
    checkToggleButtonModel(line.models[0], "Align Start of View", TEXT_ALIGN_LAYOUT_LEFT, TextAlignment.VIEW_START)
    checkToggleButtonModel(line.models[1], "Align Start of Text", TEXT_ALIGN_LEFT, TextAlignment.TEXT_START)
    checkToggleButtonModel(line.models[2], "Align Center", TEXT_ALIGN_CENTER, TextAlignment.CENTER)
    checkToggleButtonModel(line.models[3], "Align End of Text", TEXT_ALIGN_RIGHT, TextAlignment.TEXT_END)
    checkToggleButtonModel(line.models[4], "Align End of View", TEXT_ALIGN_LAYOUT_RIGHT, TextAlignment.VIEW_END)
  }

  private fun checkToggleButtonModel(model: PropertyEditorModel, description: String, icon: Icon,
                                     trueValue: String, falseValue: String = "") {
    val toggleModel = model as ToggleButtonPropertyEditorModel
    assertThat(toggleModel.description).isEqualTo(description)
    assertThat(toggleModel.icon).isEqualTo(icon)
    assertThat(toggleModel.trueValue).isEqualTo(trueValue)
    assertThat(toggleModel.falseValue).isEqualTo(falseValue)
  }

  fun testNotAvailableWhenMissingRequiredProperty() {
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val formModel = mock(FormModel::class.java)
    val builder = TextViewInspectorBuilder(util.editorProvider, formModel)
    for (missing in TextViewInspectorBuilder.REQUIRED_PROPERTIES) {
      addRequiredProperties(util)
      util.removeProperty(ANDROID_URI, missing)
      builder.attachToInspector(util.inspector, util.properties)
      assertThat(util.inspector.lines).isEmpty()
    }
  }

  fun testExpandableSections() {
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val formModel = mock(FormModel::class.java)
    val builder = TextViewInspectorBuilder(util.editorProvider, formModel)
    addRequiredProperties(util)
    util.addProperty(ANDROID_URI, ATTR_FONT_FAMILY, NelePropertyType.STRING)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(12)
    val title = util.inspector.lines[1]
    val textAppearance = util.inspector.lines[5]
    assertThat(title.expandable).isTrue()
    assertThat(title.expanded).isTrue()
    assertThat(title.childProperties)
      .containsExactly(ATTR_TEXT, ATTR_TEXT, ATTR_CONTENT_DESCRIPTION, ATTR_TEXT_APPEARANCE).inOrder()
    assertThat(textAppearance.expandable).isTrue()
    assertThat(textAppearance.expanded).isFalse()
    assertThat(textAppearance.childProperties)
      .containsExactly(ATTR_FONT_FAMILY, ATTR_TYPEFACE, ATTR_TEXT_SIZE, ATTR_LINE_SPACING_EXTRA, ATTR_TEXT_COLOR, ATTR_TEXT_STYLE).inOrder()
  }

  private fun addRequiredProperties(util: InspectorTestUtil) {
    util.addProperty(ANDROID_URI, ATTR_TEXT, NelePropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_CONTENT_DESCRIPTION, NelePropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_TEXT_APPEARANCE, NelePropertyType.STYLE)
    util.addProperty(ANDROID_URI, ATTR_TYPEFACE, NelePropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_TEXT_SIZE, NelePropertyType.FONT_SIZE)
    util.addProperty(ANDROID_URI, ATTR_LINE_SPACING_EXTRA, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_TEXT_STYLE, NelePropertyType.STRING)
    util.addFlagsProperty(ANDROID_URI, ATTR_TEXT_STYLE, listOf(TextStyle.VALUE_BOLD, TextStyle.VALUE_ITALIC))
    util.addProperty(ANDROID_URI, ATTR_TEXT_ALL_CAPS, NelePropertyType.BOOLEAN)
    util.addProperty(ANDROID_URI, ATTR_TEXT_COLOR, NelePropertyType.COLOR)
  }

  private fun addOptionalProperties(util: InspectorTestUtil) {
    util.addProperty(ANDROID_URI, ATTR_FONT_FAMILY, NelePropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_TEXT_ALIGNMENT, NelePropertyType.STRING)
  }
}
