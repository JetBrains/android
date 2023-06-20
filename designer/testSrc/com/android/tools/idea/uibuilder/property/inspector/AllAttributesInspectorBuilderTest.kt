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

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.Dependencies
import com.android.tools.idea.testing.addManifest
import com.android.tools.idea.uibuilder.property.NlPropertiesModel
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.inspector.groups.CONSTRAINT_GROUP_NAME
import com.android.tools.idea.uibuilder.property.support.NlControlTypeProvider
import com.android.tools.idea.uibuilder.property.support.NlEnumSupportProvider
import com.android.tools.idea.uibuilder.property.testutils.InspectorTestUtil
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.ptable.PTableGroupItem
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private const val CONSTRAINT_LAYOUT_ID = "constraint"

@RunsInEdt
class AllAttributesInspectorBuilderTest {

  @JvmField @Rule
  val projectRule = AndroidProjectRule.withSdk()

  @JvmField @Rule
  val edtRule = EdtRule()

  @Test
  fun testAllAttributes() {
    addManifest(projectRule.fixture)
    Dependencies.add(projectRule.fixture, CONSTRAINT_LAYOUT_ID)
    val util = InspectorTestUtil(projectRule, SdkConstants.TEXT_VIEW, parentTag = AndroidXConstants.CONSTRAINT_LAYOUT.oldName())
    util.loadProperties()
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    util.checkTitle(0, InspectorSection.ALL.title)
    val items = util.checkTable(1).tableModel.items
    assertThat(util.inspector.lines).hasSize(2)

    // Check that these 6 attributes are present in alphabetical order:
    assertThat(items.map { it.name })
      .containsAllOf(
        SdkConstants.ATTR_CONTENT_DESCRIPTION,
        SdkConstants.ATTR_LAYOUT_HEIGHT,
        SdkConstants.ATTR_LAYOUT_MARGIN,
        SdkConstants.ATTR_LAYOUT_WIDTH,
        SdkConstants.ATTR_TEXT,
        SdkConstants.ATTR_TEXT_COLOR,
        SdkConstants.ATTR_TEXT_SIZE,
        SdkConstants.ATTR_VISIBILITY
      ).inOrder()

    // Layout Margin is a group:
    val margin = items.find { it.name == SdkConstants.ATTR_LAYOUT_MARGIN } as PTableGroupItem
    assertThat(margin.children.map { it.name })
      .containsExactly(
        SdkConstants.ATTR_LAYOUT_MARGIN,
        SdkConstants.ATTR_LAYOUT_MARGIN_START,
        SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
        SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
        SdkConstants.ATTR_LAYOUT_MARGIN_END,
        SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
        SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM
      ).inOrder()

    // Padding is a group:
    val padding = items.find { it.name == SdkConstants.ATTR_PADDING } as PTableGroupItem
    assertThat(padding.children.map { it.name })
      .containsExactly(
        SdkConstants.ATTR_PADDING,
        SdkConstants.ATTR_PADDING_START,
        SdkConstants.ATTR_PADDING_LEFT,
        SdkConstants.ATTR_PADDING_TOP,
        SdkConstants.ATTR_PADDING_END,
        SdkConstants.ATTR_PADDING_RIGHT,
        SdkConstants.ATTR_PADDING_BOTTOM
      ).inOrder()

    // Constraints is a group:
    val constraints = items.find { it.name == CONSTRAINT_GROUP_NAME } as PTableGroupItem
    assertThat(constraints.children.map { it.name })
      .containsExactly(
        SdkConstants.ATTR_BARRIER_ALLOWS_GONE_WIDGETS,
        SdkConstants.ATTR_BARRIER_DIRECTION,
        SdkConstants.ATTR_LAYOUT_CHAIN_HELPER_USE_RTL,
        SdkConstants.ATTR_LAYOUT_CONSTRAINTSET,
        SdkConstants.CONSTRAINT_REFERENCED_IDS,
        SdkConstants.ATTR_FLOW_FIRST_HORIZONTAL_BIAS,
        SdkConstants.ATTR_FLOW_FIRST_HORIZONTAL_STYLE,
        SdkConstants.ATTR_FLOW_FIRST_VERTICAL_BIAS,
        SdkConstants.ATTR_FLOW_FIRST_VERTICAL_STYLE,
        SdkConstants.ATTR_FLOW_HORIZONTAL_ALIGN,
        SdkConstants.ATTR_FLOW_HORIZONTAL_BIAS,
        SdkConstants.ATTR_FLOW_HORIZONTAL_GAP,
        SdkConstants.ATTR_FLOW_HORIZONTAL_STYLE,
        SdkConstants.ATTR_FLOW_LAST_HORIZONTAL_BIAS,
        SdkConstants.ATTR_FLOW_LAST_HORIZONTAL_STYLE,
        SdkConstants.ATTR_FLOW_LAST_VERTICAL_BIAS,
        SdkConstants.ATTR_FLOW_LAST_VERTICAL_STYLE,
        SdkConstants.ATTR_FLOW_MAX_ELEMENTS_WRAP,
        SdkConstants.ATTR_FLOW_VERTICAL_ALIGN,
        SdkConstants.ATTR_FLOW_VERTICAL_BIAS,
        SdkConstants.ATTR_FLOW_VERTICAL_GAP,
        SdkConstants.ATTR_FLOW_VERTICAL_STYLE,
        SdkConstants.ATTR_FLOW_WRAP_MODE,
        SdkConstants.ATTR_LAYOUT_CONSTRAINED_HEIGHT,
        SdkConstants.ATTR_LAYOUT_CONSTRAINED_WIDTH,
        SdkConstants.ATTR_LAYOUT_BASELINE_CREATOR,
        SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF,
        SdkConstants.ATTR_LAYOUT_BOTTOM_CREATOR,
        SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
        SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
        SdkConstants.ATTR_LAYOUT_CONSTRAINT_CIRCLE,
        SdkConstants.ATTR_LAYOUT_CONSTRAINT_CIRCLE_ANGLE,
        SdkConstants.ATTR_LAYOUT_CONSTRAINT_CIRCLE_RADIUS,
        SdkConstants.ATTR_LAYOUT_DIMENSION_RATIO,
        SdkConstants.ATTR_LAYOUT_END_TO_END_OF,
        SdkConstants.ATTR_LAYOUT_END_TO_START_OF,
        SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN,
        SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END,
        SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT,
        SdkConstants.ATTR_LAYOUT_HEIGHT_DEFAULT,
        SdkConstants.ATTR_LAYOUT_HEIGHT_MAX,
        SdkConstants.ATTR_LAYOUT_HEIGHT_MIN,
        SdkConstants.ATTR_LAYOUT_HEIGHT_PERCENT,
        SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS,
        SdkConstants.ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE,
        SdkConstants.ATTR_LAYOUT_HORIZONTAL_WEIGHT,
        SdkConstants.ATTR_LAYOUT_LEFT_CREATOR,
        SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF,
        SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
        SdkConstants.ATTR_LAYOUT_RIGHT_CREATOR,
        SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
        SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
        SdkConstants.ATTR_LAYOUT_START_TO_END_OF,
        SdkConstants.ATTR_LAYOUT_START_TO_START_OF,
        SdkConstants.ATTR_LAYOUT_CONSTRAINT_TAG,
        SdkConstants.ATTR_LAYOUT_TOP_CREATOR,
        SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
        SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF,
        SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS,
        SdkConstants.ATTR_LAYOUT_VERTICAL_CHAIN_STYLE,
        SdkConstants.ATTR_LAYOUT_VERTICAL_WEIGHT,
        SdkConstants.ATTR_LAYOUT_WIDTH_DEFAULT,
        SdkConstants.ATTR_LAYOUT_WIDTH_MAX,
        SdkConstants.ATTR_LAYOUT_WIDTH_MIN,
        SdkConstants.ATTR_LAYOUT_WIDTH_PERCENT,
        SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X,
        SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y,
        SdkConstants.ATTR_LAYOUT_GONE_MARGIN_BOTTOM,
        SdkConstants.ATTR_LAYOUT_GONE_MARGIN_END,
        SdkConstants.ATTR_LAYOUT_GONE_MARGIN_LEFT,
        SdkConstants.ATTR_LAYOUT_GONE_MARGIN_RIGHT,
        SdkConstants.ATTR_LAYOUT_GONE_MARGIN_START,
        SdkConstants.ATTR_LAYOUT_GONE_MARGIN_TOP,
        SdkConstants.ATTR_LAYOUT_OPTIMIZATION_LEVEL,
        SdkConstants.ATTR_MAX_HEIGHT,
        SdkConstants.ATTR_MAX_WIDTH,
        SdkConstants.ATTR_MIN_HEIGHT,
        SdkConstants.ATTR_MIN_WIDTH
      ).inOrder()
  }
}

@RunsInEdt
class AllAttributesInspectorBuilderVisibilityTest {
  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val chain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @Before
  fun before() {
    projectRule.replaceService(PropertiesComponent::class.java, PropertiesComponentMock())
  }

  @Test
  fun testVisible() {
    val util = InspectorTestUtil(projectRule, SdkConstants.TEXT_VIEW, parentTag = AndroidXConstants.CONSTRAINT_LAYOUT.oldName())
    util.addProperty(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT, NlPropertyType.STRING)
    val builder = createBuilder(util.model)
    InspectorSection.ALL.visible = true
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.checkTitle(0, InspectorSection.ALL.title).hidden).isFalse()
    assertThat(util.checkTable(1).hidden).isFalse()
  }

  @Test
  fun testHidden() {
    val util = InspectorTestUtil(projectRule, SdkConstants.TEXT_VIEW, parentTag = AndroidXConstants.CONSTRAINT_LAYOUT.oldName())
    util.addProperty(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT, NlPropertyType.STRING)
    val builder = createBuilder(util.model)
    InspectorSection.ALL.visible = false
    builder.attachToInspector(util.inspector, util.properties)
    val model = util.checkTable(0)
    assertThat(model.hidden).isTrue()
    model.filter = "t"
    assertThat(model.hidden).isFalse()
    model.filter = ""
    assertThat(model.hidden).isTrue()
  }
}

private fun createBuilder(model: NlPropertiesModel): AllAttributesInspectorBuilder {
  val enumSupportProvider = NlEnumSupportProvider(model)
  val controlTypeProvider = NlControlTypeProvider(enumSupportProvider)
  val editorProvider = EditorProvider.create(enumSupportProvider, controlTypeProvider)
  return AllAttributesInspectorBuilder(model, controlTypeProvider, editorProvider)
}
