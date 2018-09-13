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
package com.android.tools.idea.uibuilder.property2

import com.android.SdkConstants.*
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.property2.support.ToggleShowResolvedValueAction
import com.android.tools.idea.uibuilder.property2.testutils.PropertyTestCase
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.Color
import com.intellij.util.ui.TwoColorsIcon
import org.intellij.lang.annotations.Language

internal const val EXPECTED_ID_TOOLTIP = """
android:id:
Supply an identifier name for this view, to later retrieve it
             with {@link android.view.View#findViewById View.findViewById()} or
             {@link android.app.Activity#findViewById Activity.findViewById()}.
             This must be a
             resource reference; typically you set this using the
             <code>@+</code> syntax to create a new ID resources.
             For example: <code>android:id="@+id/my_id"</code> which
             allows you to later retrieve the view
             with <code>findViewById(R.id.my_id)</code>.
"""

private const val STRINGS = """<?xml version="1.0" encoding="utf-8"?>
<resources>
  <string name="demo">Demo String</string>
  <string name="design">Design Demo</string>
  <dimen name="lineSpacing">13sp</dimen>
</resources>
"""

private const val HELLO_WORLD = "Hello World"

class NelePropertyItemTest : PropertyTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject("/res/values/strings.xml", STRINGS)
  }

  fun testTextProperty() {
    val property = createPropertyItem(ATTR_TEXT, NelePropertyType.STRING, createTextView())
    property.model.showResolvedValues = false
    assertThat(property.name).isEqualTo(ATTR_TEXT)
    assertThat(property.namespace).isEqualTo(ANDROID_URI)
    assertThat(property.type).isEqualTo(NelePropertyType.STRING)
    assertThat(property.value).isEqualTo("@string/demo")
    assertThat(property.isReference).isTrue()
    assertThat(property.resolvedValue).isEqualTo("Demo String")
    assertThat(property.tooltipForName).isEqualTo("android:text:\nText to display.")
    assertThat(property.editingSupport.validation("Some")).isEqualTo(EDITOR_NO_ERROR)
    assertThat(property.libraryName).isEmpty()
    assertThat(property.components).hasSize(1)
    assertThat(property.components[0].tagName).isEqualTo(TEXT_VIEW)
    assertThat(property.colorButton).isNull()
    val browseButton = property.browseButton!!
    assertThat(browseButton.getActionIcon(false)).isEqualTo(StudioIcons.Common.PROPERTY_BOUND)
    assertThat(browseButton.getActionIcon(true)).isEqualTo(StudioIcons.Common.PROPERTY_BOUND_FOCUS)
  }

  fun testUnboundTextProperty() {
    val property = createPropertyItem(ATTR_TEXT, NelePropertyType.STRING, createTextViewWithHardcodedValue())
    assertThat(property.name).isEqualTo(ATTR_TEXT)
    assertThat(property.namespace).isEqualTo(ANDROID_URI)
    assertThat(property.type).isEqualTo(NelePropertyType.STRING)
    assertThat(property.value).isEqualTo("Hardcoded string")
    assertThat(property.isReference).isFalse()
    assertThat(property.resolvedValue).isEqualTo("Hardcoded string")
    assertThat(property.colorButton).isNull()
    val browseButton = property.browseButton!!
    assertThat(browseButton.getActionIcon(false)).isEqualTo(StudioIcons.Common.PROPERTY_UNBOUND)
    assertThat(browseButton.getActionIcon(true)).isEqualTo(StudioIcons.Common.PROPERTY_UNBOUND_FOCUS)
  }

  fun testTextDesignProperty() {
    val property = createPropertyItem(ATTR_TEXT, NelePropertyType.STRING, createTextView())
    val design = property.designProperty
    property.model.showResolvedValues = false
    assertThat(design.name).isEqualTo(ATTR_TEXT)
    assertThat(design.namespace).isEqualTo(TOOLS_URI)
    assertThat(design.type).isEqualTo(NelePropertyType.STRING)
    assertThat(design.value).isEqualTo("@string/design")
    assertThat(design.rawValue).isEqualTo("@string/design")
    assertThat(design.isReference).isTrue()
    assertThat(design.resolvedValue).isEqualTo("Design Demo")
    assertThat(design.tooltipForName).isEqualTo("tools:text:\nText to display.")
    assertThat(property.editingSupport.validation("Some")).isEqualTo(EDITOR_NO_ERROR)
    assertThat(design.libraryName).isEmpty()
    assertThat(design.components).hasSize(1)
    assertThat(design.components[0].tagName).isEqualTo(TEXT_VIEW)
    assertThat(property.colorButton).isNull()
    val browseButton = property.browseButton!!
    assertThat(browseButton.getActionIcon(false)).isEqualTo(StudioIcons.Common.PROPERTY_BOUND)
    assertThat(browseButton.getActionIcon(true)).isEqualTo(StudioIcons.Common.PROPERTY_BOUND_FOCUS)
    assertThat(design.designProperty).isEqualTo(design)
  }

  fun testColorPropertyWithColorWithoutValue() {
    val property = createPropertyItem(ATTR_TEXT_COLOR, NelePropertyType.COLOR_OR_DRAWABLE, createTextView())
    assertThat(property.name).isEqualTo(ATTR_TEXT_COLOR)
    assertThat(property.namespace).isEqualTo(ANDROID_URI)
    assertThat(property.type).isEqualTo(NelePropertyType.COLOR_OR_DRAWABLE)
    assertThat(property.value).isNull()
    assertThat(property.isReference).isFalse()
    val colorButton = property.colorButton!!
    assertThat(colorButton.getActionIcon(false)).isEqualTo(StudioIcons.LayoutEditor.Extras.PIPETTE)
    assertThat(colorButton.getActionIcon(true)).isEqualTo(StudioIcons.LayoutEditor.Extras.PIPETTE)
    val browseButton = property.browseButton!!
    assertThat(browseButton.getActionIcon(false)).isEqualTo(StudioIcons.Common.PROPERTY_UNBOUND)
    assertThat(browseButton.getActionIcon(true)).isEqualTo(StudioIcons.Common.PROPERTY_UNBOUND_FOCUS)
  }

  fun testColorPropertyWithColorConstant() {
    val property = createPropertyItem(ATTR_TEXT_COLOR, NelePropertyType.COLOR_OR_DRAWABLE, createTextViewWithTextColor("#FF990033"))
    assertThat(property.name).isEqualTo(ATTR_TEXT_COLOR)
    assertThat(property.namespace).isEqualTo(ANDROID_URI)
    assertThat(property.type).isEqualTo(NelePropertyType.COLOR_OR_DRAWABLE)
    assertThat(property.value).isEqualTo("#FF990033")
    assertThat(property.isReference).isFalse()
    val colorIcon = ColorIcon(16, Color(0x990033))
    val colorButton = property.colorButton!!
    assertThat(colorButton.getActionIcon(false)).isEqualTo(colorIcon)
    assertThat(colorButton.getActionIcon(true)).isEqualTo(colorIcon)
    val browseButton = property.browseButton!!
    assertThat(browseButton.getActionIcon(false)).isEqualTo(StudioIcons.Common.PROPERTY_UNBOUND)
    assertThat(browseButton.getActionIcon(true)).isEqualTo(StudioIcons.Common.PROPERTY_UNBOUND_FOCUS)
  }

  fun testColorPropertyWithColorStateList() {
    val property = createPropertyItem(ATTR_TEXT_COLOR,
                                      NelePropertyType.COLOR_OR_DRAWABLE,
                                      createTextViewWithTextColor("@android:color/primary_text_dark"))
    property.model.showResolvedValues = false
    assertThat(property.name).isEqualTo(ATTR_TEXT_COLOR)
    assertThat(property.namespace).isEqualTo(ANDROID_URI)
    assertThat(property.type).isEqualTo(NelePropertyType.COLOR_OR_DRAWABLE)
    assertThat(property.value).isEqualTo("@android:color/primary_text_dark")
    assertThat(property.isReference).isTrue()
    val colorIcon = TwoColorsIcon(16, Color(0xFFFFFF), Color(0x000000))
    val colorButton = property.colorButton!!
    assertThat(colorButton.getActionIcon(false)).isEqualTo(colorIcon)
    assertThat(colorButton.getActionIcon(true)).isEqualTo(colorIcon)
    val browseButton = property.browseButton!!
    assertThat(browseButton.getActionIcon(false)).isEqualTo(StudioIcons.Common.PROPERTY_BOUND)
    assertThat(browseButton.getActionIcon(true)).isEqualTo(StudioIcons.Common.PROPERTY_BOUND_FOCUS)
  }

  fun testIsReference() {
    val property = createPropertyItem(ATTR_TEXT, NelePropertyType.STRING, createTextView())
    assertThat(isReferenceValue(property, "hello")).isFalse()
    assertThat(isReferenceValue(property, "@string/hello")).isTrue()
    assertThat(isReferenceValue(property, "@android:string/hello")).isTrue()
    assertThat(isReferenceValue(property, "?backgroundColor")).isTrue()
    // IDs should not be treated as references:
    assertThat(isReferenceValue(property, "@id/hello")).isFalse()
    assertThat(isReferenceValue(property, "@+id/hello")).isFalse()
    assertThat(isReferenceValue(property, "@android:id/hello")).isFalse()
  }

  fun testResolvedValues() {
    myFixture.addFileToProject("res/values/values.xml", VALUE_RESOURCES)
    myFixture.addFileToProject("res/layout/my_layout.xml", "<LinearLayout/>")
    val components = createTextView()
    assertThat(resolvedValue(components, NelePropertyType.BOOLEAN, "@bool/useBorder")).isEqualTo("true")
    assertThat(resolvedValue(components, NelePropertyType.COLOR, "@color/opaqueRed")).isEqualTo("#f00")
    assertThat(resolvedValue(components, NelePropertyType.COLOR, "@color/opaqueRedIndirect")).isEqualTo("#f00")
    assertThat(resolvedValue(components, NelePropertyType.COLOR, "@color/translucentRed")).isEqualTo("#80ff0000")
    assertThat(resolvedValue(components, NelePropertyType.DIMENSION, "@dimen/ballRadius")).isEqualTo("30dp")
    assertThat(resolvedValue(components, NelePropertyType.DIMENSION, "@dimen/fontSize")).isEqualTo("16sp")
    assertThat(resolvedValue(components, NelePropertyType.FRACTION, "@fraction/part")).isEqualTo("0.125")
    assertThat(resolvedValue(components, NelePropertyType.ID, "@id/button_ok")).isEqualTo("@id/button_ok")
    assertThat(resolvedValue(components, NelePropertyType.INTEGER, "@integer/records")).isEqualTo("67")
    assertThat(resolvedValue(components, NelePropertyType.STRING, "@string/hello")).isEqualTo("Hello")

    // The following resources will resolve to a file path. Check that we do NOT show the file:
    assertThat(resolvedValue(components, NelePropertyType.COLOR, "@color/text")).isEqualTo("@android:color/primary_text_dark")
    assertThat(resolvedValue(components, NelePropertyType.COLOR_OR_DRAWABLE, "@drawable/cancel")).isEqualTo("@android:drawable/ic_delete")
    assertThat(resolvedValue(components, NelePropertyType.STYLE, "@style/stdButton")).isEqualTo("@style/stdButton")
    assertThat(resolvedValue(components, NelePropertyType.LAYOUT, "@layout/my_layout")).isEqualTo("@layout/my_layout")
  }

  fun testGetValueWhenDisplayingResolvedValues() {
    val property = createPropertyItem(ATTR_TEXT, NelePropertyType.STRING, createTextView())
    property.model.showResolvedValues = true
    assertThat(property.value).isEqualTo("Demo String")
    assertThat(property.rawValue).isEqualTo("@string/demo")
    assertThat(property.isReference).isTrue()
  }

  fun testGetSameValueFromMultipleComponents() {
    val property = createPropertyItem(ATTR_TEXT, NelePropertyType.STRING, createTextViewAndButtonWithSameTextValue())
    property.model.showResolvedValues = false
    assertThat(property.value).isEqualTo("@string/demo")
    assertThat(property.isReference).isTrue()
    assertThat(property.resolvedValue).isEqualTo("Demo String")
  }

  fun testGetDifferentValueFromMultipleComponents() {
    val property = createPropertyItem(ATTR_TEXT, NelePropertyType.STRING, createTextViewAndButtonWithDifferentTextValue())
    assertThat(property.value).isNull()
    assertThat(property.isReference).isFalse()
    assertThat(property.resolvedValue).isNull()
  }

  fun testSetValueOnMultipleComponents() {
    val components = createTextViewAndButtonWithDifferentTextValue()
    val property = createPropertyItem(ATTR_TEXT, NelePropertyType.STRING, components)
    property.value = HELLO_WORLD

    assertThat(property.value).isEqualTo(HELLO_WORLD)
    assertThat(property.isReference).isFalse()
    assertThat(property.resolvedValue).isEqualTo(HELLO_WORLD)
    assertThat(components[0].getAttribute(ANDROID_URI, ATTR_TEXT)).isEqualTo(HELLO_WORLD)
    assertThat(components[1].getAttribute(ANDROID_URI, ATTR_TEXT)).isEqualTo(HELLO_WORLD)
  }

  fun testGetValueWithDefaultValue() {
    val components = createTextView()
    val property = createPropertyItem(ATTR_TEXT_APPEARANCE, NelePropertyType.STYLE, components)
    val manager = getSceneManager(property)
    manager.putDefaultPropertyValue(components[0], ResourceNamespace.ANDROID, ATTR_TEXT_APPEARANCE, "?attr/textAppearanceSmall")
    waitUntilEventsProcessed(property.model)

    assertThat(property.value).isEqualTo("@android:style/TextAppearance.Material.Small")
    property.model.showResolvedValues = false
    assertThat(property.value).isNull()
  }

  fun testToolTipForValue() {
    val model = NelePropertiesModel(testRootDisposable, myFacet)
    val components = createTextView()
    val emptyProperty = createPropertyItem(ATTR_CONTENT_DESCRIPTION, NelePropertyType.STRING, components, model)
    val hardcodedProperty = createPropertyItem(ATTR_LAYOUT_WIDTH, NelePropertyType.DIMENSION, components, model)
    val referenceProperty = createPropertyItem(ATTR_TEXT, NelePropertyType.STRING, components, model)
    val hardcodedFromDefaultProperty = createPropertyItem(ATTR_LINE_SPACING_EXTRA, NelePropertyType.DIMENSION, components, model)
    val referenceFromDefaultProperty = createPropertyItem(ATTR_TEXT_SIZE, NelePropertyType.DIMENSION, components, model)
    val manager = getSceneManager(hardcodedFromDefaultProperty)
    val keyStroke = KeymapUtil.getShortcutText(ToggleShowResolvedValueAction.SHORTCUT)  // Platform dependent !!!
    referenceFromDefaultProperty.resolver
    manager.putDefaultPropertyValue(components[0], ResourceNamespace.ANDROID, ATTR_LINE_SPACING_EXTRA, "16sp")
    manager.putDefaultPropertyValue(components[0], ResourceNamespace.ANDROID, ATTR_TEXT_SIZE, "@dimen/text_size_button_material")
    waitUntilEventsProcessed(model)

    assertThat(emptyProperty.tooltipForValue).isEmpty()
    assertThat(hardcodedProperty.tooltipForValue).isEmpty()
    assertThat(referenceProperty.tooltipForValue).isEqualTo("\"@string/demo\" = \"Demo String\" ($keyStroke)")
    assertThat(hardcodedFromDefaultProperty.tooltipForValue).isEqualTo("[default] \"16sp\"")
    assertThat(referenceFromDefaultProperty.tooltipForValue)
      .isEqualTo("[default] \"@android:dimen/text_size_button_material\" = \"14sp\" ($keyStroke)")
  }

  fun testCompletion() {
    val model = NelePropertiesModel(testRootDisposable, myFacet)
    val components = createTextView()
    val text = createPropertyItem(ATTR_TEXT, NelePropertyType.STRING, components, model)
    val values = text.editingSupport.completion()
    assertThat(values.size).isAtLeast(25)
    assertThat(values.filter { it.startsWith("@string/") }).containsExactly("@string/demo", "@string/design").inOrder()
    assertThat(values).containsAllOf("@android:string/yes", "@android:string/no", "@android:string/cancel")
  }

  private fun createTextView(): List<NlComponent> {
    return createComponents(
        component(TEXT_VIEW)
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, "wrap_content")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, "wrap_content")
          .withAttribute(ANDROID_URI, ATTR_TEXT, "@string/demo")
          .withAttribute(TOOLS_URI, ATTR_TEXT, "@string/design")
    )
  }

  private fun createTextViewWithHardcodedValue(): List<NlComponent> {
    return createComponents(
      component(TEXT_VIEW)
        .withAttribute(ANDROID_URI, ATTR_TEXT, "Hardcoded string")
        .withAttribute(TOOLS_URI, ATTR_TEXT, "Hardcoded design string")
    )
  }

  private fun createTextViewWithTextColor(textColor: String): List<NlComponent> {
    return createComponents(
      component(TEXT_VIEW)
        .withAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, "wrap_content")
        .withAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, "wrap_content")
        .withAttribute(ANDROID_URI, ATTR_TEXT_COLOR, textColor)
    )
  }

  private fun createTextViewAndButtonWithSameTextValue(): List<NlComponent> {
    return createComponents(
        component(TEXT_VIEW)
          .withAttribute(ANDROID_URI, ATTR_TEXT, "@string/demo")
          .withAttribute(TOOLS_URI, ATTR_TEXT, "@string/design"),
        component(BUTTON)
          .withAttribute(ANDROID_URI, ATTR_TEXT, "@string/demo")
          .withAttribute(TOOLS_URI, ATTR_TEXT, "@string/design")
    )
  }

  private fun createTextViewAndButtonWithDifferentTextValue(): List<NlComponent> {
    return createComponents(
        component(TEXT_VIEW)
          .withAttribute(ANDROID_URI, ATTR_TEXT, "@string/demo")
          .withAttribute(TOOLS_URI, ATTR_TEXT, "@string/design"),
        component(BUTTON)
          .withAttribute(ANDROID_URI, ATTR_TEXT, "other")
          .withAttribute(TOOLS_URI, ATTR_TEXT, "something")
    )
  }

  private fun isReferenceValue(property: NelePropertyItem, value: String): Boolean {
    property.value = value
    return property.isReference
  }

  private fun resolvedValue(components: List<NlComponent>, type: NelePropertyType, value: String): String? {
    val property = createPropertyItem("name", type, components)
    property.value = value
    return property.resolvedValue
  }

  // Ugly hack:
  // The production code is executing the properties creation on a separate thread.
  // This code makes sure that the last scheduled worker thread is finished,
  // then we also need to wait for events on the UI thread.
  private fun waitUntilEventsProcessed(model: NelePropertiesModel) {
    model.lastSelectionUpdate?.get()
    UIUtil.dispatchAllInvocationEvents()
  }

  @Language("XML")
  private val VALUE_RESOURCES = """<?xml version="1.0" encoding="utf-8"?>
    <resources>
      <bool name="useBorder">true</bool>
      <color name="opaqueRed">#f00</color>
      <color name="translucentRed">#80ff0000</color>
      <color name="opaqueRedIndirect">@color/opaqueRed</color>
      <dimen name="ballRadius">30dp</dimen>
      <dimen name="fontSize">16sp</dimen>
      <fraction name="part">0.125</fraction>
      <item type="id" name="button_ok" />
      <integer name="records">67</integer>
      <string name="hello">Hello</string>

      <color name="text">@android:color/primary_text_dark</color>
      <drawable name="cancel">@android:drawable/ic_delete</drawable>
      <style name="stdButton" parent="@android:style/TextAppearance.Material.Widget.Button"/>
    </resources>
  """.trimIndent()
}
