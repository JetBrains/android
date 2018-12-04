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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION
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
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.SdkConstants.ATTR_TEXT_SIZE
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.SdkConstants.VALUE_WRAP_CONTENT
import com.android.tools.adtui.ptable2.PTableGroupItem
import com.android.tools.idea.common.property2.api.EditorProvider
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.support.NeleControlTypeProvider
import com.android.tools.idea.uibuilder.property2.support.NeleEnumSupportProvider
import com.android.tools.idea.uibuilder.property2.testutils.InspectorTestUtil
import com.android.tools.idea.uibuilder.property2.testutils.LineType
import com.google.common.truth.Truth
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class AllAttributesInspectorBuilderTest {

  @JvmField @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @JvmField @Rule
  val edtRule = EdtRule()

  @Test
  fun testAllAttributes() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    Truth.assertThat(util.inspector.lines).hasSize(2)
    Truth.assertThat(util.inspector.lines[0].type).isEqualTo(LineType.TITLE)
    Truth.assertThat(util.inspector.lines[1].type).isEqualTo(LineType.TABLE)

    Truth.assertThat(util.inspector.lines[0].title).isEqualTo("All Attributes")
    Truth.assertThat(util.inspector.lines[0].expandable).isTrue()

    // Check all 6 attributes:
    Truth.assertThat(util.inspector.lines[1].tableModel?.items?.map { it.name })
      .containsExactly(
        ATTR_CONTENT_DESCRIPTION,
        ATTR_LAYOUT_HEIGHT,
        ATTR_LAYOUT_MARGIN,
        ATTR_LAYOUT_WIDTH,
        ATTR_TEXT,
        ATTR_TEXT_COLOR,
        ATTR_TEXT_SIZE,
        ATTR_VISIBILITY
      ).inOrder()

    // Layout Margin is a group:
    val margin = util.inspector.lines[1].tableModel!!.items[2] as PTableGroupItem
    Truth.assertThat(margin.children.map { it.name })
      .containsExactly(
        ATTR_LAYOUT_MARGIN,
        ATTR_LAYOUT_MARGIN_START,
        ATTR_LAYOUT_MARGIN_LEFT,
        ATTR_LAYOUT_MARGIN_TOP,
        ATTR_LAYOUT_MARGIN_END,
        ATTR_LAYOUT_MARGIN_RIGHT,
        ATTR_LAYOUT_MARGIN_BOTTOM
      ).inOrder()
  }

  private fun addProperties(util: InspectorTestUtil) {
    util.addProperty(ANDROID_URI, ATTR_TEXT, NelePropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_TEXT_SIZE, NelePropertyType.FONT_SIZE)
    util.addProperty(ANDROID_URI, ATTR_TEXT_COLOR, NelePropertyType.COLOR)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_WIDTH, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_HEIGHT, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_CONTENT_DESCRIPTION, NelePropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN_START, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN_END, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN_TOP, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN_BOTTOM, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_VISIBILITY, NelePropertyType.ENUM)

    util.properties[ANDROID_URI, ATTR_TEXT].value = "Testing"
    util.properties[ANDROID_URI, ATTR_LAYOUT_WIDTH].value = VALUE_WRAP_CONTENT
    util.properties[ANDROID_URI, ATTR_LAYOUT_HEIGHT].value = VALUE_WRAP_CONTENT
  }

  private fun createBuilder(model: NelePropertiesModel): AllAttributesInspectorBuilder {
    val enumSupportProvider = NeleEnumSupportProvider()
    val controlTypeProvider = NeleControlTypeProvider(enumSupportProvider)
    val editorProvider = EditorProvider.create(enumSupportProvider, controlTypeProvider)
    return AllAttributesInspectorBuilder(model, controlTypeProvider, editorProvider)
  }
}
