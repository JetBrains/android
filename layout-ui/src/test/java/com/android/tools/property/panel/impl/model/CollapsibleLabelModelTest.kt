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
import com.android.SdkConstants.ATTR_BACKGROUND_TINT
import com.android.SdkConstants.ATTR_COLOR
import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.property.panel.impl.model.util.FakePropertyEditorModel
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.util.text.Matcher
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import org.junit.Test

private const val OUTER_EXPANDED_BY_DEFAULT = true
private const val INNER_EXPANDED_BY_DEFAULT = false
private const val OUTER_GROUP_NAME = "OuterGroup"
private const val INNER_GROUP_NAME = "InnerGroup"

class CollapsibleLabelModelTest {

  class Labels(val properties: PropertiesComponent = PropertiesComponentMock()) {
    private val colorProperty = FakePropertyItem(ANDROID_URI, ATTR_COLOR, "#00FF00")
    private val backgroundTintProperty =
      FakePropertyItem(TOOLS_URI, ATTR_BACKGROUND_TINT, "#00FF00")
    private val textProperty = FakePropertyItem(AUTO_URI, ATTR_TEXT, "hello")
    private val someProperty = FakePropertyItem("SomeNamespace", "some", "world")
    private val styleProperty = FakePropertyItem("", ATTR_STYLE, null)

    private val colorEditor = FakePropertyEditorModel(colorProperty)
    private val backgroundTintEditor = FakePropertyEditorModel(backgroundTintProperty)
    private val textEditor = FakePropertyEditorModel(textProperty)
    private val someEditor = FakePropertyEditorModel(someProperty)
    private val styleEditor = FakePropertyEditorModel(styleProperty)

    val outerGroup = CollapsibleLabelModel(OUTER_GROUP_NAME, null, true, properties)
    val colorItem = CollapsibleLabelModel(ATTR_COLOR, colorEditor, true, properties)
    val innerGroup = CollapsibleLabelModel(INNER_GROUP_NAME, null, true, properties)
    val backgroundTintItem =
      CollapsibleLabelModel(ATTR_BACKGROUND_TINT, backgroundTintEditor, true, properties)
    val textItem = CollapsibleLabelModel(ATTR_TEXT, textEditor, true, properties)
    val someItem = CollapsibleLabelModel("some", someEditor, true, properties)
    val styleItem = CollapsibleLabelModel(ATTR_STYLE, styleEditor, true, properties)

    init {
      outerGroup.makeExpandable(OUTER_EXPANDED_BY_DEFAULT)
      outerGroup.addChild(colorItem)
      outerGroup.addChild(innerGroup)
      outerGroup.addChild(someItem)
      outerGroup.addChild(styleItem)
      innerGroup.makeExpandable(INNER_EXPANDED_BY_DEFAULT)
      innerGroup.addChild(backgroundTintItem)
      innerGroup.addChild(textItem)
    }
  }

  @Test
  fun testCollapseHidesInnerChildren() {
    val test = Labels()
    test.innerGroup.expanded = true
    test.outerGroup.expanded = false
    assertThat(test.outerGroup.visible).isTrue()
    assertThat(test.colorItem.visible).isFalse()
    assertThat(test.someItem.visible).isFalse()
    assertThat(test.innerGroup.visible).isFalse()
    assertThat(test.backgroundTintItem.visible).isFalse()
    assertThat(test.textItem.visible).isFalse()
  }

  @Test
  fun testCollapseWithInnerGroupCollapsed() {
    val test = Labels()
    test.outerGroup.expanded = false
    assertThat(test.outerGroup.visible).isTrue()
    assertThat(test.colorItem.visible).isFalse()
    assertThat(test.someItem.visible).isFalse()
    assertThat(test.innerGroup.visible).isFalse()
    assertThat(test.backgroundTintItem.visible).isFalse()
    assertThat(test.textItem.visible).isFalse()
  }

  @Test
  fun testExpandRestoresInnerChildrenWhenExpanded() {
    val test = Labels()
    test.innerGroup.expanded = true
    test.outerGroup.expanded = false
    test.outerGroup.expanded = true
    assertThat(test.outerGroup.visible).isTrue()
    assertThat(test.colorItem.visible).isTrue()
    assertThat(test.someItem.visible).isTrue()
    assertThat(test.innerGroup.visible).isTrue()
    assertThat(test.backgroundTintItem.visible).isTrue()
    assertThat(test.textItem.visible).isTrue()
  }

  @Test
  fun testExpandRestoredInnerChildrenWhenCollapsed() {
    val test = Labels()
    test.outerGroup.expanded = false
    test.outerGroup.expanded = true
    assertThat(test.outerGroup.visible).isTrue()
    assertThat(test.colorItem.visible).isTrue()
    assertThat(test.someItem.visible).isTrue()
    assertThat(test.innerGroup.visible).isTrue()
    assertThat(test.backgroundTintItem.visible).isFalse()
    assertThat(test.textItem.visible).isFalse()
  }

  @Test
  fun testIconOfExpandedGroup() {
    val test = Labels()
    assertThat(test.outerGroup.icon).isEqualTo(UIUtil.getTreeExpandedIcon())
  }

  @Test
  fun testIconOfCollapsedGroup() {
    val test = Labels()
    test.outerGroup.expanded = false
    assertThat(test.outerGroup.icon).isEqualTo(UIUtil.getTreeCollapsedIcon())
  }

  @Test
  fun testIconOfPropertyItemWithoutNamespace() {
    val test = Labels()
    assertThat(test.styleItem.icon).isNull()
  }

  @Test
  fun testIconOfAndroidPropertyItem() {
    val test = Labels()
    assertThat(test.colorItem.icon).isNull()
  }

  @Test
  fun testIconOfToolsPropertyItem() {
    val test = Labels()
    assertThat(test.backgroundTintItem.icon)
      .isEqualTo(StudioIcons.LayoutEditor.Properties.TOOLS_ATTRIBUTE)
  }

  @Test
  fun testIconOfApplicationPropertyItem() {
    val test = Labels()
    assertThat(test.textItem.icon).isNull()
  }

  @Test
  fun testUpdatePropertiesWhenCollapsing() {
    val test = Labels()
    test.outerGroup.expanded = false
    test.innerGroup.expanded = false
    assertThat(
        test.properties.getBoolean(KEY_PREFIX + test.outerGroup.name, OUTER_EXPANDED_BY_DEFAULT)
      )
      .isFalse()
    assertThat(
        test.properties.getBoolean(KEY_PREFIX + test.innerGroup.name, INNER_EXPANDED_BY_DEFAULT)
      )
      .isFalse()
  }

  @Test
  fun testUpdatePropertiesWhenExpanded() {
    val test = Labels()
    test.outerGroup.expanded = true
    test.innerGroup.expanded = true
    assertThat(
        test.properties.getBoolean(KEY_PREFIX + test.outerGroup.name, OUTER_EXPANDED_BY_DEFAULT)
      )
      .isTrue()
    assertThat(
        test.properties.getBoolean(KEY_PREFIX + test.innerGroup.name, INNER_EXPANDED_BY_DEFAULT)
      )
      .isTrue()
  }

  @Test
  fun testInitialExpandedStateIsReadFromProperties() {
    val properties = PropertiesComponentMock()
    properties.setValue(KEY_PREFIX + OUTER_GROUP_NAME, true, OUTER_EXPANDED_BY_DEFAULT)
    properties.setValue(KEY_PREFIX + INNER_GROUP_NAME, true, INNER_EXPANDED_BY_DEFAULT)
    val test = Labels(properties)
    assertThat(test.outerGroup.expanded).isTrue()
    assertThat(test.innerGroup.expanded).isTrue()
    assertThat(test.colorItem.visible).isTrue()
    assertThat(test.textItem.visible).isTrue()
  }

  @Test
  fun testInitialCollapsedStateIsReadFromProperties() {
    val properties = PropertiesComponentMock()
    properties.setValue(KEY_PREFIX + OUTER_GROUP_NAME, false, OUTER_EXPANDED_BY_DEFAULT)
    properties.setValue(KEY_PREFIX + INNER_GROUP_NAME, false, INNER_EXPANDED_BY_DEFAULT)
    val test = Labels(properties)
    assertThat(test.outerGroup.expanded).isFalse()
    assertThat(test.innerGroup.expanded).isFalse()
    assertThat(test.colorItem.visible).isFalse()
    assertThat(test.textItem.visible).isFalse()
  }

  @Test
  fun testInitialOuterCollapsedInnerExpandedStateIsReadFromProperties() {
    val properties = PropertiesComponentMock()
    properties.setValue(KEY_PREFIX + OUTER_GROUP_NAME, false, OUTER_EXPANDED_BY_DEFAULT)
    properties.setValue(KEY_PREFIX + INNER_GROUP_NAME, true, INNER_EXPANDED_BY_DEFAULT)
    val test = Labels(properties)
    assertThat(test.outerGroup.expanded).isFalse()
    assertThat(test.outerGroup.expanded).isFalse()
    assertThat(test.colorItem.visible).isFalse()
    assertThat(test.textItem.visible).isFalse()
  }

  @Test
  fun testHiddenIsPropagated() {
    val test = Labels()
    test.innerGroup.hidden = true
    assertThat(test.backgroundTintItem.hidden).isTrue()
    assertThat(test.textItem.hidden).isTrue()
  }

  @Test
  fun testLabelWithoutEditorIsNotAMatch() {
    val test = Labels()
    assertThat(test.outerGroup.isMatch(Matcher { it.startsWith("Outer") })).isFalse()
  }
}
