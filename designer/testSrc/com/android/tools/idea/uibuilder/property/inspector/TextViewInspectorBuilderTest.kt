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
package com.android.tools.idea.uibuilder.property.inspector

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION
import com.android.SdkConstants.ATTR_FONT_FAMILY
import com.android.SdkConstants.ATTR_LINE_SPACING_EXTRA
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.ATTR_TEXT_ALIGNMENT
import com.android.SdkConstants.ATTR_TEXT_ALL_CAPS
import com.android.SdkConstants.ATTR_TEXT_APPEARANCE
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.SdkConstants.ATTR_TEXT_SIZE
import com.android.SdkConstants.ATTR_TEXT_STYLE
import com.android.SdkConstants.ATTR_TYPEFACE
import com.android.SdkConstants.TEXT_VIEW
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.TextAlignment
import com.android.SdkConstants.TextStyle
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.model.HorizontalEditorPanelModel
import com.android.tools.idea.uibuilder.property.model.ToggleButtonPropertyEditorModel
import com.android.tools.idea.uibuilder.property.testutils.InspectorTestUtil
import com.android.tools.property.panel.api.PropertyEditorModel
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import icons.StudioIcons.LayoutEditor.Properties.TEXT_ALIGN_CENTER
import icons.StudioIcons.LayoutEditor.Properties.TEXT_ALIGN_LAYOUT_LEFT
import icons.StudioIcons.LayoutEditor.Properties.TEXT_ALIGN_LAYOUT_RIGHT
import icons.StudioIcons.LayoutEditor.Properties.TEXT_ALIGN_LEFT
import icons.StudioIcons.LayoutEditor.Properties.TEXT_ALIGN_RIGHT
import icons.StudioIcons.LayoutEditor.Properties.TEXT_STYLE_BOLD
import icons.StudioIcons.LayoutEditor.Properties.TEXT_STYLE_ITALIC
import icons.StudioIcons.LayoutEditor.Properties.TEXT_STYLE_UPPERCASE
import javax.swing.Icon
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class TextViewInspectorBuilderTest {
  @JvmField @Rule val projectRule = AndroidProjectRule.inMemory()

  @JvmField @Rule val edtRule = EdtRule()

  @Test
  fun testAvailableWithRequiredPropertiesPresent() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW)
    val builder = TextViewInspectorBuilder(util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    addRequiredProperties(util)
    builder.attachToInspector(util.inspector, util.properties) { generator.title }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, ANDROID_URI, ATTR_TEXT)
    util.checkEditor(2, TOOLS_URI, ATTR_TEXT)
    util.checkEditor(3, ANDROID_URI, ATTR_CONTENT_DESCRIPTION)
    util.checkEditor(4, ANDROID_URI, ATTR_TEXT_APPEARANCE)
    util.checkEditor(5, ANDROID_URI, ATTR_TYPEFACE)
    util.checkEditor(6, ANDROID_URI, ATTR_TEXT_SIZE)
    util.checkEditor(7, ANDROID_URI, ATTR_LINE_SPACING_EXTRA)
    util.checkEditor(8, ANDROID_URI, ATTR_TEXT_COLOR)
    util.checkEditor(9, ANDROID_URI, ATTR_TEXT_STYLE)
    assertThat(util.inspector.lines).hasSize(10)
  }

  @Test
  fun testOptionalPropertiesPresent() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW)
    val builder = TextViewInspectorBuilder(util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    addRequiredProperties(util)
    util.addProperty(ANDROID_URI, ATTR_FONT_FAMILY, NlPropertyType.STRING)
    addOptionalProperties(util)
    builder.attachToInspector(util.inspector, util.properties) { generator.title }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, ANDROID_URI, ATTR_TEXT)
    util.checkEditor(2, TOOLS_URI, ATTR_TEXT)
    util.checkEditor(3, ANDROID_URI, ATTR_CONTENT_DESCRIPTION)
    util.checkEditor(4, ANDROID_URI, ATTR_TEXT_APPEARANCE)
    util.checkEditor(5, ANDROID_URI, ATTR_FONT_FAMILY)
    util.checkEditor(6, ANDROID_URI, ATTR_TYPEFACE)
    util.checkEditor(7, ANDROID_URI, ATTR_TEXT_SIZE)
    util.checkEditor(8, ANDROID_URI, ATTR_LINE_SPACING_EXTRA)
    util.checkEditor(9, ANDROID_URI, ATTR_TEXT_COLOR)
    util.checkEditor(10, ANDROID_URI, ATTR_TEXT_STYLE)
    util.checkEditor(11, ANDROID_URI, ATTR_TEXT_ALIGNMENT)
    assertThat(util.inspector.lines).hasSize(12)
  }

  @Test
  fun testTextStyleModel() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW)
    val builder = TextViewInspectorBuilder(util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    addRequiredProperties(util)
    addOptionalProperties(util)
    builder.attachToInspector(util.inspector, util.properties) { generator.title }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, ANDROID_URI, ATTR_TEXT)
    util.checkEditor(2, TOOLS_URI, ATTR_TEXT)
    util.checkEditor(3, ANDROID_URI, ATTR_CONTENT_DESCRIPTION)
    util.checkEditor(4, ANDROID_URI, ATTR_TEXT_APPEARANCE)
    util.checkEditor(5, ANDROID_URI, ATTR_FONT_FAMILY)
    util.checkEditor(6, ANDROID_URI, ATTR_TYPEFACE)
    util.checkEditor(7, ANDROID_URI, ATTR_TEXT_SIZE)
    util.checkEditor(8, ANDROID_URI, ATTR_LINE_SPACING_EXTRA)
    util.checkEditor(9, ANDROID_URI, ATTR_TEXT_COLOR)
    util.checkEditor(10, ANDROID_URI, ATTR_TEXT_STYLE)
    util.checkEditor(11, ANDROID_URI, ATTR_TEXT_ALIGNMENT)
    assertThat(util.inspector.lines).hasSize(12)
    val line = util.inspector.lines[10].editorModel as HorizontalEditorPanelModel
    assertThat(line.models).hasSize(3)
    checkToggleButtonModel(line.models[0], "Bold", TEXT_STYLE_BOLD, "true", "false")
    checkToggleButtonModel(line.models[1], "Italics", TEXT_STYLE_ITALIC, "true", "false")
    checkToggleButtonModel(line.models[2], "All Caps", TEXT_STYLE_UPPERCASE, "true", "false")
  }

  @Test
  fun testTextAlignmentModel() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW)
    val builder = TextViewInspectorBuilder(util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    addRequiredProperties(util)
    addOptionalProperties(util)
    builder.attachToInspector(util.inspector, util.properties) { generator.title }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, ANDROID_URI, ATTR_TEXT)
    util.checkEditor(2, TOOLS_URI, ATTR_TEXT)
    util.checkEditor(3, ANDROID_URI, ATTR_CONTENT_DESCRIPTION)
    util.checkEditor(4, ANDROID_URI, ATTR_TEXT_APPEARANCE)
    util.checkEditor(5, ANDROID_URI, ATTR_FONT_FAMILY)
    util.checkEditor(6, ANDROID_URI, ATTR_TYPEFACE)
    util.checkEditor(7, ANDROID_URI, ATTR_TEXT_SIZE)
    util.checkEditor(8, ANDROID_URI, ATTR_LINE_SPACING_EXTRA)
    util.checkEditor(9, ANDROID_URI, ATTR_TEXT_COLOR)
    util.checkEditor(10, ANDROID_URI, ATTR_TEXT_STYLE)
    util.checkEditor(11, ANDROID_URI, ATTR_TEXT_ALIGNMENT)
    assertThat(util.inspector.lines).hasSize(12)
    val line = util.inspector.lines[11].editorModel as HorizontalEditorPanelModel
    assertThat(line.models).hasSize(5)
    checkToggleButtonModel(
      line.models[0],
      "Align Start of View",
      TEXT_ALIGN_LAYOUT_LEFT,
      TextAlignment.VIEW_START
    )
    checkToggleButtonModel(
      line.models[1],
      "Align Start of Text",
      TEXT_ALIGN_LEFT,
      TextAlignment.TEXT_START
    )
    checkToggleButtonModel(line.models[2], "Align Center", TEXT_ALIGN_CENTER, TextAlignment.CENTER)
    checkToggleButtonModel(
      line.models[3],
      "Align End of Text",
      TEXT_ALIGN_RIGHT,
      TextAlignment.TEXT_END
    )
    checkToggleButtonModel(
      line.models[4],
      "Align End of View",
      TEXT_ALIGN_LAYOUT_RIGHT,
      TextAlignment.VIEW_END
    )
  }

  private fun checkToggleButtonModel(
    model: PropertyEditorModel,
    description: String,
    icon: Icon,
    trueValue: String,
    falseValue: String = ""
  ) {
    val toggleModel = model as ToggleButtonPropertyEditorModel
    assertThat(toggleModel.description).isEqualTo(description)
    assertThat(toggleModel.icon).isEqualTo(icon)
    assertThat(toggleModel.trueValue).isEqualTo(trueValue)
    assertThat(toggleModel.falseValue).isEqualTo(falseValue)
  }

  @Test
  fun testNotAvailableWhenMissingRequiredProperty() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW)
    val builder = TextViewInspectorBuilder(util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    for (missing in TextViewInspectorBuilder.REQUIRED_PROPERTIES) {
      addRequiredProperties(util)
      util.removeProperty(ANDROID_URI, missing)
      builder.attachToInspector(util.inspector, util.properties) { generator.title }
      assertThat(util.inspector.lines).isEmpty()
    }
  }

  @Test
  fun testExpandableSections() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW)
    val builder = TextViewInspectorBuilder(util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    addRequiredProperties(util)
    util.addProperty(ANDROID_URI, ATTR_FONT_FAMILY, NlPropertyType.STRING)
    builder.attachToInspector(util.inspector, util.properties) { generator.title }
    assertThat(util.inspector.lines).hasSize(11)
    val title = util.inspector.lines[0]
    val textAppearance = util.inspector.lines[4]
    assertThat(title.expandable).isTrue()
    assertThat(title.expanded).isTrue()
    assertThat(title.childProperties)
      .containsExactly(ATTR_TEXT, ATTR_TEXT, ATTR_CONTENT_DESCRIPTION, ATTR_TEXT_APPEARANCE)
      .inOrder()
    assertThat(textAppearance.expandable).isTrue()
    assertThat(textAppearance.expanded).isFalse()
    assertThat(textAppearance.childProperties)
      .containsExactly(
        ATTR_FONT_FAMILY,
        ATTR_TYPEFACE,
        ATTR_TEXT_SIZE,
        ATTR_LINE_SPACING_EXTRA,
        ATTR_TEXT_COLOR,
        ATTR_TEXT_STYLE
      )
      .inOrder()
  }

  private fun addRequiredProperties(util: InspectorTestUtil) {
    util.addProperty(ANDROID_URI, ATTR_TEXT, NlPropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_CONTENT_DESCRIPTION, NlPropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_TEXT_APPEARANCE, NlPropertyType.STYLE)
    util.addProperty(ANDROID_URI, ATTR_TYPEFACE, NlPropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_TEXT_SIZE, NlPropertyType.FONT_SIZE)
    util.addProperty(ANDROID_URI, ATTR_LINE_SPACING_EXTRA, NlPropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_TEXT_STYLE, NlPropertyType.STRING)
    util.addFlagsProperty(
      ANDROID_URI,
      ATTR_TEXT_STYLE,
      listOf(TextStyle.VALUE_BOLD, TextStyle.VALUE_ITALIC)
    )
    util.addProperty(ANDROID_URI, ATTR_TEXT_ALL_CAPS, NlPropertyType.THREE_STATE_BOOLEAN)
    util.addProperty(ANDROID_URI, ATTR_TEXT_COLOR, NlPropertyType.COLOR)
  }

  private fun addOptionalProperties(util: InspectorTestUtil) {
    util.addProperty(ANDROID_URI, ATTR_FONT_FAMILY, NlPropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_TEXT_ALIGNMENT, NlPropertyType.STRING)
  }
}
