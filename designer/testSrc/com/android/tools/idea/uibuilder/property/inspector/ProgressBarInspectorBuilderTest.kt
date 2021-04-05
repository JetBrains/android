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
import com.android.SdkConstants.ATTR_INDETERMINATE
import com.android.SdkConstants.ATTR_INDETERMINATE_DRAWABLE
import com.android.SdkConstants.ATTR_INDETERMINATE_TINT
import com.android.SdkConstants.ATTR_MAXIMUM
import com.android.SdkConstants.ATTR_PROGRESS
import com.android.SdkConstants.ATTR_PROGRESS_DRAWABLE
import com.android.SdkConstants.ATTR_PROGRESS_TINT
import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.SdkConstants.PROGRESS_BAR
import com.android.SdkConstants.VALUE_FALSE
import com.android.SdkConstants.VALUE_TRUE
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property.NelePropertyType
import com.android.tools.idea.uibuilder.property.testutils.InspectorTestUtil
import com.android.tools.property.panel.api.PropertyEditorModel
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class ProgressBarInspectorBuilderTest {
  @JvmField @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @JvmField @Rule
  val edtRule = EdtRule()

  @Test
  fun testNotApplicableWhenRequiredPropertyIsMissing() {
    val util = InspectorTestUtil(projectRule, PROGRESS_BAR)
    val builder = ProgressBarInspectorBuilder(util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    for (missing in ProgressBarInspectorBuilder.REQUIRED_PROPERTIES) {
      addRequiredProperties(util)
      util.removeProperty(ANDROID_URI, missing)
      builder.attachToInspector(util.inspector, util.properties) { generator.title }
      assertThat(util.inspector.lines).isEmpty()
    }
  }

  @Test
  fun testAvailableWithRequiredPropertiesPresent() {
    val util = InspectorTestUtil(projectRule, PROGRESS_BAR)
    val builder = ProgressBarInspectorBuilder(util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    addRequiredProperties(util)
    builder.attachToInspector(util.inspector, util.properties) { generator.title }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, "", ATTR_STYLE)
    util.checkEditor(2, ANDROID_URI, ATTR_PROGRESS_DRAWABLE)
    util.checkEditor(3, ANDROID_URI, ATTR_INDETERMINATE_DRAWABLE)
    util.checkEditor(4, ANDROID_URI, ATTR_MAXIMUM)
    util.checkEditor(5, ANDROID_URI, ATTR_PROGRESS)
    util.checkEditor(6, ANDROID_URI, ATTR_INDETERMINATE)
    assertThat(util.inspector.lines).hasSize(7)
  }

  @Test
  fun testOptionalPropertiesPresent() {
    val util = InspectorTestUtil(projectRule, PROGRESS_BAR)
    val builder = ProgressBarInspectorBuilder(util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    addRequiredProperties(util)
    addOptionalProperties(util)
    builder.attachToInspector(util.inspector, util.properties) { generator.title }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, "", ATTR_STYLE)
    util.checkEditor(2, ANDROID_URI, ATTR_PROGRESS_DRAWABLE)
    util.checkEditor(3, ANDROID_URI, ATTR_INDETERMINATE_DRAWABLE)
    util.checkEditor(4, ANDROID_URI, ATTR_PROGRESS_TINT)
    util.checkEditor(5, ANDROID_URI, ATTR_INDETERMINATE_TINT)
    util.checkEditor(6, ANDROID_URI, ATTR_MAXIMUM)
    util.checkEditor(7, ANDROID_URI, ATTR_PROGRESS)
    util.checkEditor(8, ANDROID_URI, ATTR_INDETERMINATE)
    assertThat(util.inspector.lines).hasSize(9)
  }

  @Test
  fun testInitialHiddenLines() {
    val util = InspectorTestUtil(projectRule, PROGRESS_BAR)
    val builder = ProgressBarInspectorBuilder(util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    addRequiredProperties(util)
    addOptionalProperties(util)
    builder.attachToInspector(util.inspector, util.properties) { generator.title }
    assertThat(getHiddenProperties(util)).containsExactly(ATTR_INDETERMINATE_DRAWABLE, ATTR_INDETERMINATE_TINT)
  }

  @Test
  fun testInitialHiddenLinesWithIndeterminateOn() {
    val util = InspectorTestUtil(projectRule, PROGRESS_BAR)
    val builder = ProgressBarInspectorBuilder(util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    addRequiredProperties(util)
    addOptionalProperties(util)
    util.properties[ANDROID_URI, ATTR_INDETERMINATE].value = VALUE_TRUE
    UIUtil.dispatchAllInvocationEvents()
    builder.attachToInspector(util.inspector, util.properties) { generator.title }
    assertThat(getHiddenProperties(util)).containsExactly(ATTR_PROGRESS_DRAWABLE, ATTR_PROGRESS_TINT, ATTR_MAXIMUM, ATTR_PROGRESS)
  }

  @Test
  fun testInitialHiddenLinesWithIndeterminateOff() {
    val util = InspectorTestUtil(projectRule, PROGRESS_BAR)
    val builder = ProgressBarInspectorBuilder(util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    addRequiredProperties(util)
    addOptionalProperties(util)
    util.properties[ANDROID_URI, ATTR_INDETERMINATE].value = VALUE_FALSE
    UIUtil.dispatchAllInvocationEvents()
    builder.attachToInspector(util.inspector, util.properties) { generator.title }
    assertThat(getHiddenProperties(util)).containsExactly(ATTR_INDETERMINATE_DRAWABLE, ATTR_INDETERMINATE_TINT)
  }

  @Test
  fun testUpdateHiddenLinesAfterValueChange() {
    // setup
    val util = InspectorTestUtil(projectRule, PROGRESS_BAR)
    val builder = ProgressBarInspectorBuilder(util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    addRequiredProperties(util)
    addOptionalProperties(util)
    builder.attachToInspector(util.inspector, util.properties) { generator.title }
    assertThat(getHiddenProperties(util)).containsExactly(ATTR_INDETERMINATE_DRAWABLE, ATTR_INDETERMINATE_TINT)
    val model = getIndeterminateModel(util)

    // test
    util.properties[ANDROID_URI, ATTR_INDETERMINATE].value = VALUE_TRUE
    UIUtil.dispatchAllInvocationEvents()
    model.refresh()
    assertThat(getHiddenProperties(util)).containsExactly(ATTR_PROGRESS_DRAWABLE, ATTR_PROGRESS_TINT, ATTR_MAXIMUM, ATTR_PROGRESS)

    util.properties[ANDROID_URI, ATTR_INDETERMINATE].value = VALUE_FALSE
    UIUtil.dispatchAllInvocationEvents()
    model.refresh()
    assertThat(getHiddenProperties(util)).containsExactly(ATTR_INDETERMINATE_DRAWABLE, ATTR_INDETERMINATE_TINT)
  }

  private fun addRequiredProperties(util: InspectorTestUtil) {
    util.addProperty("", ATTR_STYLE, NelePropertyType.STYLE)
    util.addProperty(ANDROID_URI, ATTR_PROGRESS_DRAWABLE, NelePropertyType.DRAWABLE)
    util.addProperty(ANDROID_URI, ATTR_INDETERMINATE_DRAWABLE, NelePropertyType.DRAWABLE)
    util.addProperty(ANDROID_URI, ATTR_MAXIMUM, NelePropertyType.INTEGER)
    util.addProperty(ANDROID_URI, ATTR_PROGRESS, NelePropertyType.INTEGER)
    util.addProperty(ANDROID_URI, ATTR_INDETERMINATE, NelePropertyType.THREE_STATE_BOOLEAN)
    util.addProperty(ANDROID_URI, ATTR_VISIBILITY, NelePropertyType.THREE_STATE_BOOLEAN)
  }

  private fun addOptionalProperties(util: InspectorTestUtil) {
    util.addProperty(ANDROID_URI, ATTR_PROGRESS_TINT, NelePropertyType.COLOR_STATE_LIST)
    util.addProperty(ANDROID_URI, ATTR_INDETERMINATE_TINT, NelePropertyType.COLOR_STATE_LIST)
  }

  private fun getHiddenProperties(util: InspectorTestUtil): List<String> {
    return util.inspector.lines.filter { it.hidden }.map { it.editorModel!!.property.name }
  }

  private fun getIndeterminateModel(util: InspectorTestUtil): PropertyEditorModel {
    return util.inspector.lines.last().editorModel!!
  }
}
