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
import com.android.tools.idea.common.property2.api.PropertyEditorModel
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.testutils.InspectorTestUtil
import com.android.tools.idea.uibuilder.property2.testutils.LineType
import com.google.common.truth.Truth.assertThat
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.AndroidTestCase

class ProgressBarInspectorBuilderTest: AndroidTestCase() {

  fun testNotApplicableWhenRequiredPropertyIsMissing() {
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, PROGRESS_BAR)
    val builder = ProgressBarInspectorBuilder(util.editorProvider)
    for (missing in ProgressBarInspectorBuilder.REQUIRED_PROPERTIES) {
      addRequiredProperties(util)
      util.removeProperty(ANDROID_URI, missing)
      builder.attachToInspector(util.inspector, util.properties)
      assertThat(util.inspector.lines).isEmpty()
    }
  }

  fun testAvailableWithRequiredPropertiesPresent() {
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, PROGRESS_BAR)
    val builder = ProgressBarInspectorBuilder(util.editorProvider)
    addRequiredProperties(util)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(10)
    assertThat(util.inspector.lines[0].type).isEqualTo(LineType.SEPARATOR)
    assertThat(util.inspector.lines[1].type).isEqualTo(LineType.TITLE)
    assertThat(util.inspector.lines[1].title).isEqualTo("ProgressBar")
    assertThat(util.inspector.lines[2].editorModel?.property?.name).isEqualTo(ATTR_STYLE)
    assertThat(util.inspector.lines[3].editorModel?.property?.name).isEqualTo(ATTR_PROGRESS_DRAWABLE)
    assertThat(util.inspector.lines[4].editorModel?.property?.name).isEqualTo(ATTR_INDETERMINATE_DRAWABLE)
    assertThat(util.inspector.lines[5].editorModel?.property?.name).isEqualTo(ATTR_MAXIMUM)
    assertThat(util.inspector.lines[6].editorModel?.property?.name).isEqualTo(ATTR_PROGRESS)
    assertThat(util.inspector.lines[7].editorModel?.property?.name).isEqualTo(ATTR_VISIBILITY)
    assertThat(util.inspector.lines[8].editorModel?.property?.name).isEqualTo(ATTR_VISIBILITY)
    assertThat(util.inspector.lines[9].editorModel?.property?.name).isEqualTo(ATTR_INDETERMINATE)
  }

  fun testOptionalPropertiesPresent() {
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, PROGRESS_BAR)
    val builder = ProgressBarInspectorBuilder(util.editorProvider)
    addRequiredProperties(util)
    addOptionalProperties(util)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(12)
    assertThat(util.inspector.lines[0].type).isEqualTo(LineType.SEPARATOR)
    assertThat(util.inspector.lines[1].type).isEqualTo(LineType.TITLE)
    assertThat(util.inspector.lines[1].title).isEqualTo("ProgressBar")
    assertThat(util.inspector.lines[2].editorModel?.property?.name).isEqualTo(ATTR_STYLE)
    assertThat(util.inspector.lines[3].editorModel?.property?.name).isEqualTo(ATTR_PROGRESS_DRAWABLE)
    assertThat(util.inspector.lines[4].editorModel?.property?.name).isEqualTo(ATTR_INDETERMINATE_DRAWABLE)
    assertThat(util.inspector.lines[5].editorModel?.property?.name).isEqualTo(ATTR_PROGRESS_TINT)
    assertThat(util.inspector.lines[6].editorModel?.property?.name).isEqualTo(ATTR_INDETERMINATE_TINT)
    assertThat(util.inspector.lines[7].editorModel?.property?.name).isEqualTo(ATTR_MAXIMUM)
    assertThat(util.inspector.lines[8].editorModel?.property?.name).isEqualTo(ATTR_PROGRESS)
    assertThat(util.inspector.lines[9].editorModel?.property?.name).isEqualTo(ATTR_VISIBILITY)
    assertThat(util.inspector.lines[10].editorModel?.property?.name).isEqualTo(ATTR_VISIBILITY)
    assertThat(util.inspector.lines[11].editorModel?.property?.name).isEqualTo(ATTR_INDETERMINATE)
  }

  fun testInitialHiddenLines() {
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, PROGRESS_BAR)
    val builder = ProgressBarInspectorBuilder(util.editorProvider)
    addRequiredProperties(util)
    addOptionalProperties(util)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(getHiddenProperties(util)).containsExactly(ATTR_INDETERMINATE_DRAWABLE, ATTR_INDETERMINATE_TINT)
  }

  fun testInitialHiddenLinesWithIndeterminateOn() {
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, PROGRESS_BAR)
    val builder = ProgressBarInspectorBuilder(util.editorProvider)
    addRequiredProperties(util)
    addOptionalProperties(util)
    util.properties[ANDROID_URI, ATTR_INDETERMINATE].value = VALUE_TRUE
    UIUtil.dispatchAllInvocationEvents()
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(getHiddenProperties(util)).containsExactly(ATTR_PROGRESS_DRAWABLE, ATTR_PROGRESS_TINT, ATTR_MAXIMUM, ATTR_PROGRESS)
  }

  fun testInitialHiddenLinesWithIndeterminateOff() {
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, PROGRESS_BAR)
    val builder = ProgressBarInspectorBuilder(util.editorProvider)
    addRequiredProperties(util)
    addOptionalProperties(util)
    util.properties[ANDROID_URI, ATTR_INDETERMINATE].value = VALUE_FALSE
    UIUtil.dispatchAllInvocationEvents()
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(getHiddenProperties(util)).containsExactly(ATTR_INDETERMINATE_DRAWABLE, ATTR_INDETERMINATE_TINT)
  }

  fun testUpdateHiddenLinesAfterValueChange() {
    // setup
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, PROGRESS_BAR)
    val builder = ProgressBarInspectorBuilder(util.editorProvider)
    addRequiredProperties(util)
    addOptionalProperties(util)
    builder.attachToInspector(util.inspector, util.properties)
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
    util.addProperty(ANDROID_URI, ATTR_PROGRESS_DRAWABLE, NelePropertyType.COLOR_OR_DRAWABLE)
    util.addProperty(ANDROID_URI, ATTR_INDETERMINATE_DRAWABLE, NelePropertyType.COLOR_OR_DRAWABLE)
    util.addProperty(ANDROID_URI, ATTR_MAXIMUM, NelePropertyType.INTEGER)
    util.addProperty(ANDROID_URI, ATTR_PROGRESS, NelePropertyType.INTEGER)
    util.addProperty(ANDROID_URI, ATTR_INDETERMINATE, NelePropertyType.BOOLEAN)
    util.addProperty(ANDROID_URI, ATTR_VISIBILITY, NelePropertyType.BOOLEAN)
  }

  private fun addOptionalProperties(util: InspectorTestUtil) {
    util.addProperty(ANDROID_URI, ATTR_PROGRESS_TINT, NelePropertyType.COLOR_OR_DRAWABLE)
    util.addProperty(ANDROID_URI, ATTR_INDETERMINATE_TINT, NelePropertyType.COLOR_OR_DRAWABLE)
  }

  private fun getHiddenProperties(util: InspectorTestUtil): List<String> {
    return util.inspector.lines.filter { it.hidden }.map { it.editorModel!!.property.name }
  }

  private fun getIndeterminateModel(util: InspectorTestUtil): PropertyEditorModel {
    return util.inspector.lines.last().editorModel!!
  }
}
