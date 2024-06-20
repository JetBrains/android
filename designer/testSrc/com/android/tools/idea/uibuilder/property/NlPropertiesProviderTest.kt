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
package com.android.tools.idea.uibuilder.property

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ELEVATION
import com.android.SdkConstants.ATTR_FONT_FAMILY
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_INPUT_TYPE
import com.android.SdkConstants.ATTR_PADDING
import com.android.SdkConstants.ATTR_SCALE_TYPE
import com.android.SdkConstants.ATTR_SRC
import com.android.SdkConstants.ATTR_SRC_COMPAT
import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.ATTR_TEXT_ALIGNMENT
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.CLASS_VIEW
import com.android.SdkConstants.EDIT_TEXT
import com.android.SdkConstants.FD_RES_XML
import com.android.SdkConstants.FQCN_IMAGE_VIEW
import com.android.SdkConstants.FQCN_LINEAR_LAYOUT
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.PreferenceAttributes.ATTR_ENTRIES
import com.android.SdkConstants.PreferenceAttributes.ATTR_ENTRY_VALUES
import com.android.SdkConstants.PreferenceAttributes.ATTR_ICON
import com.android.SdkConstants.PreferenceTags.LIST_PREFERENCE
import com.android.SdkConstants.PreferenceTags.PREFERENCE_SCREEN
import com.android.SdkConstants.TEXT_VIEW
import com.android.SdkConstants.VIEW
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.property.testutils.APPCOMPAT_IMAGE_VIEW
import com.android.tools.idea.uibuilder.property.testutils.APPCOMPAT_TEXT_VIEW
import com.android.tools.idea.uibuilder.property.testutils.ComponentUtil.component
import com.android.tools.idea.uibuilder.property.testutils.ComponentUtil.createComponents
import com.android.tools.idea.uibuilder.property.testutils.MinApiRule
import com.android.tools.idea.uibuilder.property.testutils.MockAppCompat
import com.android.tools.idea.uibuilder.property.testutils.SupportTestUtil
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.asJava.classes.runReadAction
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private const val CUSTOM_TAG = "com.example.PieChart"
private const val ATTR_LEGEND = "legend"
private const val ATTR_LABEL_POS = "labelPosition"

private fun PropertiesTable<NlPropertyItem>.contains(namespace: String, name: String): Boolean {
  return this.getOrNull(namespace, name) != null
}

private fun PropertiesTable<NlPropertyItem>.doesNotContain(
  namespace: String,
  name: String,
): Boolean {
  return !this.contains(namespace, name)
}

class NlPropertiesProviderTest {
  private val projectRule = AndroidProjectRule.withSdk()

  @get:Rule
  val chain = RuleChain.outerRule(projectRule).around(MinApiRule(projectRule)).around(EdtRule())!!

  private val viewAttrs =
    listOf(ATTR_ID, ATTR_PADDING, ATTR_VISIBILITY, ATTR_TEXT_ALIGNMENT, ATTR_ELEVATION)
  private val frameLayoutAttrs = listOf("layout_gravity")
  private val gridLayoutAttrs = listOf("layout_rowSpan", "layout_column")
  private val linearLayoutAttrs = listOf("layout_weight")
  private val relativeLayoutAttrs = listOf("layout_toLeftOf", "layout_above", "layout_alignTop")

  @Test
  fun testViewAttributes() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val provider = NlPropertiesProvider(facet)
    val model = NlPropertiesModel(projectRule.testRootDisposable, facet)
    val components = createViewTagComponent()
    val properties = runReadAction { provider.getProperties(model, null, components) }
    assertThat(properties.size).isAtLeast(124)
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsAllIn(viewAttrs)
    assertThat(properties.getByNamespace("").keys).contains(ATTR_STYLE)
  }

  @Test
  fun testRootHasAllLayoutAttributes() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val provider = NlPropertiesProvider(facet)
    val model = NlPropertiesModel(projectRule.testRootDisposable, facet)
    val components = createViewTagComponent()
    val properties = runReadAction { provider.getProperties(model, null, components) }
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsAllIn(frameLayoutAttrs)
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsAllIn(gridLayoutAttrs)
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsAllIn(linearLayoutAttrs)
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsAllIn(relativeLayoutAttrs)
  }

  @Test
  fun testSubViewHasLayoutAttributesOfParent() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val provider = NlPropertiesProvider(facet)
    val model = NlPropertiesModel(projectRule.testRootDisposable, facet)
    val components = createComponents(component(TEXT_VIEW))
    val properties = runReadAction { provider.getProperties(model, null, components) }
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsAllIn(linearLayoutAttrs)
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsNoneIn(gridLayoutAttrs)
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsNoneIn(relativeLayoutAttrs)
  }

  @Test
  fun testFontFamilyFromAppCompatForMinApi14() {
    setUpAppCompat()
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val provider = NlPropertiesProvider(facet)
    val model = NlPropertiesModel(projectRule.testRootDisposable, facet)
    val components = createComponents(component(TEXT_VIEW).viewObjectClassName(APPCOMPAT_TEXT_VIEW))
    val properties = runReadAction { provider.getProperties(model, null, components) }
    assertThat(properties.contains(AUTO_URI, ATTR_FONT_FAMILY)).isTrue()
    assertThat(properties.doesNotContain(ANDROID_URI, ATTR_FONT_FAMILY)).isTrue()
  }

  @Test
  fun testFontFamilyFromAndroidForMinApi16() {
    setUpAppCompat()
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val provider = NlPropertiesProvider(facet)
    val model = NlPropertiesModel(projectRule.testRootDisposable, facet)
    val components = createComponents(component(TEXT_VIEW).viewObjectClassName(APPCOMPAT_TEXT_VIEW))
    val properties = runReadAction { provider.getProperties(model, null, components) }
    assertThat(properties.doesNotContain(AUTO_URI, ATTR_FONT_FAMILY)).isTrue()
    assertThat(properties.contains(ANDROID_URI, ATTR_FONT_FAMILY)).isTrue()
  }

  @Test
  fun testSrcCompatIncludedWhenUsingAppCompat() {
    setUpAppCompat()
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val provider = NlPropertiesProvider(facet)
    val model = NlPropertiesModel(projectRule.testRootDisposable, facet)
    val components =
      createComponents(component(IMAGE_VIEW).viewObjectClassName(APPCOMPAT_IMAGE_VIEW))
    val properties = runReadAction { provider.getProperties(model, null, components) }
    assertThat(properties.doesNotContain(ANDROID_URI, ATTR_SRC)).isTrue()
    assertThat(properties.contains(AUTO_URI, ATTR_SRC_COMPAT)).isTrue()
  }

  @RunsInEdt
  @Test
  fun testSrcCompatIncludedWhenUsingAppCompatAndKeepSrcIfPresent() {
    setUpAppCompat()
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val provider = NlPropertiesProvider(facet)
    val model = NlPropertiesModel(projectRule.testRootDisposable, facet)
    val components =
      createComponents(component(IMAGE_VIEW).viewObjectClassName(APPCOMPAT_IMAGE_VIEW))
    components.first().setAttribute(ANDROID_URI, ATTR_SRC, "@drawable/mine")
    val properties = runReadAction { provider.getProperties(model, null, components) }
    assertThat(properties.contains(ANDROID_URI, ATTR_SRC)).isTrue()
    assertThat(properties.contains(AUTO_URI, ATTR_SRC_COMPAT)).isTrue()
  }

  @Test
  fun testSrcCompatNotIncludedWhenNotUsingAppCompat() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val provider = NlPropertiesProvider(facet)
    val model = NlPropertiesModel(projectRule.testRootDisposable, facet)
    val components = createComponents(component(IMAGE_VIEW))
    val properties = runReadAction { provider.getProperties(model, null, components) }
    assertThat(properties.contains(ANDROID_URI, ATTR_SRC)).isTrue()
    assertThat(properties.doesNotContain(AUTO_URI, ATTR_SRC_COMPAT)).isTrue()
  }

  @Test
  fun testCustomViewProperties() {
    SupportTestUtil.setUpCustomView(projectRule.fixture)
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val provider = NlPropertiesProvider(facet)
    val model = NlPropertiesModel(projectRule.testRootDisposable, facet)
    val components = createComponents(component(CUSTOM_TAG))
    val properties = runReadAction { provider.getProperties(model, null, components) }
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsAllIn(viewAttrs)
    assertThat(properties.getByNamespace("").keys).contains(ATTR_STYLE)
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsAllIn(linearLayoutAttrs)
    assertThat(properties.doesNotContain(ANDROID_URI, ATTR_TEXT)).isTrue()
    assertThat(properties.getByNamespace(AUTO_URI).keys).containsAllOf(ATTR_LEGEND, ATTR_LABEL_POS)
  }

  @Test
  fun testComponentName() {
    setUpAppCompat()
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val provider = NlPropertiesProvider(facet)
    val model = NlPropertiesModel(projectRule.testRootDisposable, facet)
    val components =
      createComponents(component(IMAGE_VIEW).viewObjectClassName(APPCOMPAT_IMAGE_VIEW))
    val properties = runReadAction { provider.getProperties(model, null, components) }
    assertThat(properties[ResourceNamespace.TODO().xmlNamespaceUri, ATTR_SRC_COMPAT].componentName)
      .isEqualTo(APPCOMPAT_IMAGE_VIEW)
    assertThat(properties[ANDROID_URI, ATTR_SCALE_TYPE].componentName).isEqualTo(FQCN_IMAGE_VIEW)
    assertThat(properties[ANDROID_URI, ATTR_VISIBILITY].componentName).isEqualTo(CLASS_VIEW)
  }

  @Test
  fun testInputType() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val provider = NlPropertiesProvider(facet)
    val model = NlPropertiesModel(projectRule.testRootDisposable, facet)
    val components = createComponents(ComponentDescriptor(EDIT_TEXT))
    val properties = runReadAction { provider.getProperties(model, null, components) }
    val property = properties[ANDROID_URI, ATTR_INPUT_TYPE]
    assertThat(property).isInstanceOf(InputTypePropertyItem::class.java)
  }

  @Test
  fun testPreferenceListForMinApi26() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val provider = NlPropertiesProvider(facet)
    val model = NlPropertiesModel(projectRule.testRootDisposable, facet)
    val components =
      createComponents(
        component(LIST_PREFERENCE).viewObjectClassName(FQCN_LINEAR_LAYOUT),
        PREFERENCE_SCREEN,
        FD_RES_XML,
      )
    val properties = runReadAction { provider.getProperties(model, null, components) }

    // From ListPreference: (2)
    properties.check(ATTR_ENTRIES, NlPropertyType.STRING_ARRAY)
    properties.check(ATTR_ENTRY_VALUES, NlPropertyType.STRING_ARRAY)

    // From DialogPreference: (6)
    properties.check("dialogTitle", NlPropertyType.STRING)
    properties.check("dialogMessage", NlPropertyType.STRING)
    properties.check("dialogIcon", NlPropertyType.DRAWABLE)
    properties.check("positiveButtonText", NlPropertyType.STRING)
    properties.check("negativeButtonText", NlPropertyType.STRING)
    properties.check("dialogLayout", NlPropertyType.LAYOUT)

    // From Preference: (17)
    properties.check(ATTR_ICON, NlPropertyType.DRAWABLE)
    properties.check("key", NlPropertyType.STRING)
    properties.check("title", NlPropertyType.STRING)
    properties.check("summary", NlPropertyType.STRING)
    properties.check("order", NlPropertyType.INTEGER)
    properties.check("fragment", NlPropertyType.STRING)
    properties.check("layout", NlPropertyType.LAYOUT)
    properties.check("widgetLayout", NlPropertyType.LAYOUT)
    properties.check("enabled", NlPropertyType.THREE_STATE_BOOLEAN)
    properties.check("selectable", NlPropertyType.THREE_STATE_BOOLEAN)
    properties.check("dependency", NlPropertyType.STRING)
    properties.check("persistent", NlPropertyType.THREE_STATE_BOOLEAN)
    properties.check("defaultValue", NlPropertyType.STRING)
    properties.check("shouldDisableView", NlPropertyType.THREE_STATE_BOOLEAN)
    properties.check("recycleEnabled", NlPropertyType.THREE_STATE_BOOLEAN)
    properties.check("singleLineTitle", NlPropertyType.THREE_STATE_BOOLEAN)
    properties.check("iconSpaceReserved", NlPropertyType.THREE_STATE_BOOLEAN)

    assertThat(properties.size).isEqualTo(25)
  }

  private fun PropertiesTable<NlPropertyItem>.check(name: String, type: NlPropertyType) {
    assertThat(contains(ANDROID_URI, name)).isTrue()
    assertThat(get(ANDROID_URI, name).type).isEqualTo(type)
  }

  private fun setUpAppCompat() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    MockAppCompat.setUp(facet, projectRule.fixture)
  }

  private fun createComponents(
    descriptor: ComponentDescriptor,
    parentTag: String = SdkConstants.LINEAR_LAYOUT,
    resourceFolder: String = SdkConstants.FD_RES_LAYOUT,
  ): List<NlComponent> {
    return createComponents(
      projectRule,
      descriptor,
      parentTag = parentTag,
      resourceFolder = resourceFolder,
    )
  }

  private fun createViewTagComponent(): List<NlComponent> {
    val builder =
      NlModelBuilderUtil.model(projectRule, SdkConstants.FD_RES_LAYOUT, "view.xml", component(VIEW))
    val nlModel = builder.build()
    return nlModel.treeReader.components
  }
}
