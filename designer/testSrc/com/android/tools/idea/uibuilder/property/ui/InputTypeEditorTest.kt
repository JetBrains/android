/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.ui

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_INPUT_TYPE
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property.InputTypePropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.testutils.SupportTestUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import javax.swing.ComboBoxModel
import javax.swing.JCheckBox

@RunsInEdt
class InputTypeEditorTest {
  private val projectRule = AndroidProjectRule.withSdk()

  @get:Rule
  val chain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @Test
  fun testTextTypes() {
    val property = createProperty()
    val editor = InputTypeEditor(property)
    editor.typeModel.selectedItem = "text"
    UIUtil.dispatchAllInvocationEvents()
    assertThat(editor.typeModel.selectedItem).isEqualTo("text")
    assertThat(editor.typeModel.displayStrings).containsExactly(
      "text",
      "number",
      "datetime",
      "phone"
    ).inOrder()
    assertThat(editor.variationModel.displayStrings).containsExactly(
      "text",
      "textEmailAddress",
      "textEmailSubject",
      "textFilter",
      "textLongMessage",
      "textPassword",
      "textPersonName",
      "textPhonetic",
      "textPostalAddress",
      "textShortMessage",
      "textUri",
      "textVisiblePassword",
      "textWebEditText",
      "textWebEmailAddress",
      "textWebPassword"
    ).inOrder()
    assertThat(editor.flagsModel.flags.map { it.name }).containsExactly(
      "textAutoComplete",
      "textAutoCorrect",
      "textCapCharacters",
      "textCapSentences",
      "textCapWords",
      "textEnableTextConversionSuggestions",
      "textImeMultiLine",
      "textMultiLine",
      "textNoSuggestions"
    ).inOrder()
  }

  @Test
  fun testNumberTypes() {
    val property = createProperty()
    val editor = InputTypeEditor(property)
    editor.typeModel.selectedItem = "number"
    UIUtil.dispatchAllInvocationEvents()
    assertThat(editor.typeModel.selectedItem).isEqualTo("number")
    assertThat(editor.typeModel.displayStrings).containsExactly(
      "text",
      "number",
      "datetime",
      "phone"
    ).inOrder()
    assertThat(editor.variationModel.displayStrings).containsExactly(
      "number",
      "numberPassword"
    ).inOrder()
    assertThat(editor.flagsModel.flags.map { it.name }).containsExactly(
      "numberDecimal",
      "numberSigned"
    ).inOrder()
  }

  @Test
  fun testDatetimeTypes() {
    val property = createProperty()
    val editor = InputTypeEditor(property)
    editor.typeModel.selectedItem = "datetime"
    UIUtil.dispatchAllInvocationEvents()
    assertThat(editor.typeModel.selectedItem).isEqualTo("datetime")
    assertThat(editor.typeModel.displayStrings).containsExactly(
      "text",
      "number",
      "datetime",
      "phone"
    ).inOrder()
    assertThat(editor.variationModel.displayStrings).containsExactly(
      "datetime",
      "date",
      "time"
    ).inOrder()
    assertThat(editor.flagsModel.flags.map { it.name }).isEmpty()
  }

  @Test
  fun testPhoneTypes() {
    val property = createProperty()
    val editor = InputTypeEditor(property)
    editor.typeModel.selectedItem = "phone"
    UIUtil.dispatchAllInvocationEvents()
    assertThat(editor.typeModel.selectedItem).isEqualTo("phone")
    assertThat(editor.typeModel.displayStrings).containsExactly(
      "text",
      "number",
      "datetime",
      "phone"
    ).inOrder()
    assertThat(editor.variationModel.displayStrings).containsExactly("phone")
    assertThat(editor.flagsModel.flags.map { it.name }).isEmpty()
  }

  @Test
  fun testTextVariations() {
    val property = createProperty()
    val editor = InputTypeEditor(property)
    editor.typeModel.selectedItem = "text"
    UIUtil.dispatchAllInvocationEvents()
    assertThat(editor.typeModel.selectedItem).isEqualTo("text")
    assertThat(property.value).isEqualTo("text")

    assertThat(editor.variationModel.selectedItem).isEqualTo("text")
    for (variation in editor.variationModel.elements.subList(1, editor.variationModel.size)) {
      editor.variationModel.selectedItem = variation
      UIUtil.dispatchAllInvocationEvents()
      assertThat(editor.variationModel.selectedItem).isSameAs(variation)
      assertThat(property.value).isEqualTo("text|$variation")
    }
    editor.variationModel.selectedItem = editor.variationModel.elements.first()
    UIUtil.dispatchAllInvocationEvents()
    assertThat(editor.variationModel.selectedItem).isSameAs(editor.variationModel.elements.first())
    assertThat(property.value).isEqualTo("text")
  }

  @Test
  fun testNumberVariations() {
    val property = createProperty()
    val editor = InputTypeEditor(property)
    editor.typeModel.selectedItem = "number"
    UIUtil.dispatchAllInvocationEvents()
    assertThat(editor.typeModel.selectedItem).isEqualTo("number")
    assertThat(property.value).isEqualTo("number")
    assertThat(editor.variationModel.selectedItem).isEqualTo("number")
    for (variation in editor.variationModel.elements.subList(1, editor.variationModel.size)) {
      editor.variationModel.selectedItem = variation
      UIUtil.dispatchAllInvocationEvents()
      assertThat(editor.variationModel.selectedItem).isSameAs(variation)
      assertThat(property.value).isEqualTo("number|$variation")
    }
    editor.variationModel.selectedItem = editor.variationModel.elements.first()
    UIUtil.dispatchAllInvocationEvents()
    assertThat(editor.variationModel.selectedItem).isSameAs(editor.variationModel.elements.first())
    assertThat(property.value).isEqualTo("number")
  }

  @Test
  fun testTextFlags() {
    val property = createProperty()
    property.value = "text|textPersonName|textCapWords"
    val editor = InputTypeEditor(property)
    assertThat(editor.typeModel.selectedItem).isEqualTo("text")
    assertThat(editor.variationModel.selectedItem).isEqualTo("textPersonName")
    val checkBoxes = UIUtil.findComponentsOfType(editor, JCheckBox::class.java)
    checkBoxes.forEach { checkBox ->
      assertThat(checkBox.isSelected).isEqualTo(checkBox.text == "textCapWords")
    }

    checkBoxes[0].isSelected = true
    UIUtil.dispatchAllInvocationEvents()
    assertThat(property.value).isEqualTo("text|textPersonName|textAutoComplete|textCapWords")
    checkBoxes[1].isSelected = true
    UIUtil.dispatchAllInvocationEvents()
    assertThat(property.value).isEqualTo("text|textPersonName|textAutoComplete|textAutoCorrect|textCapWords")
    editor.variationModel.selectedItem = "textLongMessage"
    UIUtil.dispatchAllInvocationEvents()
    assertThat(property.value).isEqualTo("text|textLongMessage|textAutoComplete|textAutoCorrect|textCapWords")
    editor.typeModel.selectedItem = "number"
    UIUtil.dispatchAllInvocationEvents()
    assertThat(property.value).isEqualTo("number")
  }


  private fun createProperty(): InputTypePropertyItem {
    val util = SupportTestUtil(projectRule, createTestLayout())
    util.waitForPropertiesUpdate(1) // The layout is selected initially. Wait to avoid exceptions during clean up.
    val model = util.model
    val resourceManagers = ModuleResourceManagers.getInstance(model.facet)
    val frameworkResourceManager = resourceManagers.frameworkResourceManager!!
    val systemAttrDefinitions = frameworkResourceManager.attributeDefinitions!!
    val definition = systemAttrDefinitions.getAttrDefinition(ResourceReference.attr(ResourceNamespace.ANDROID, ATTR_INPUT_TYPE))!!
    return InputTypePropertyItem(ANDROID_URI, ATTR_INPUT_TYPE, NlPropertyType.FLAGS, definition, "", "", model, util.components)
  }

  private fun createTestLayout(): ComponentDescriptor =
    ComponentDescriptor(SdkConstants.RELATIVE_LAYOUT)
      .withBounds(0, 0, 1000, 1000)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        ComponentDescriptor(SdkConstants.EDIT_TEXT)
          .withBounds(0, 0, 100, 100)
          .id("@+id/edit-text")
          .withAttribute(ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH, "100dp")
          .withAttribute(ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT, "100dp")
          .withAttribute(ANDROID_URI, SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT, "true")
          .withAttribute(ANDROID_URI, SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START, "true"))

  private val <T> ComboBoxModel<T>.elements: List<T>
    get() {
      val elements = mutableListOf<T>()
      for (i in 0 until size) {
        elements.add(getElementAt(i))
      }
      return elements
    }

  private val <T> ComboBoxModel<T>.displayStrings: List<String>
    get() = elements.map { it.toString() }
}