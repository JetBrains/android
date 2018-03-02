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
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property2.api.PropertiesTable
import com.android.tools.idea.uibuilder.property2.testutils.MockAppCompat
import com.android.tools.idea.uibuilder.property2.testutils.PropertyTestCase
import com.google.common.truth.Truth.assertThat
import org.intellij.lang.annotations.Language

private const val CUSTOM_TAG = "com.example.PieChart"
private const val CUSTOM_NAMESPACE = "http://schemas.android.com/apk/res/com.example"
private const val ATTR_LEGEND = "legend"
private const val ATTR_LABEL_POS = "labelPosition"

private fun PropertiesTable<NelePropertyItem>.contains(namespace: String, name: String): Boolean {
  return this.getOrNull(namespace, name) != null
}

private fun PropertiesTable<NelePropertyItem>.doesNotContain(namespace: String, name: String): Boolean {
  return !this.contains(namespace, name)
}

class NelePropertiesProviderTest : PropertyTestCase() {
  private val viewAttrs = listOf(ATTR_ID, ATTR_PADDING, ATTR_VISIBILITY, ATTR_TEXT_ALIGNMENT, ATTR_ELEVATION)
  private val frameLayoutAttrs = listOf("layout_gravity")
  private val gridLayoutAttrs = listOf("layout_rowSpan", "layout_column")
  private val linearLayoutAttrs = listOf("layout_weight")
  private val relativeLayoutAttrs = listOf("layout_toLeftOf", "layout_above", "layout_alignTop")

  fun testViewAttributes() {
    val provider = createProvider()
    val properties = provider.getProperties(createViewTagComponent())
    assertThat(properties.size).isAtLeast(124)
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsAllIn(viewAttrs)
    assertThat(properties.getByNamespace("").keys).contains(ATTR_STYLE)
  }

  fun testRootHasAllLayoutAttributes() {
    val provider = createProvider()
    val properties = provider.getProperties(createViewTagComponent())
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsAllIn(frameLayoutAttrs)
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsAllIn(gridLayoutAttrs)
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsAllIn(linearLayoutAttrs)
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsAllIn(relativeLayoutAttrs)
  }

  fun testSubViewHasLayoutAttributesOfParent() {
    val provider = createProvider()
    val properties = provider.getProperties(createComponents(component(TEXT_VIEW)))
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsAllIn(linearLayoutAttrs)
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsNoneIn(gridLayoutAttrs)
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsNoneIn(relativeLayoutAttrs)
  }

  fun testFontFamilyFromAppCompatForMinApi14() {
    setUpAppCompat()
    val provider = createProvider()
    val properties = provider.getProperties(createComponents(component(TEXT_VIEW)))
    assertThat(properties.contains(AUTO_URI, ATTR_FONT_FAMILY)).isTrue()
    assertThat(properties.doesNotContain(ANDROID_URI, ATTR_FONT_FAMILY)).isTrue()
  }

  fun testFontFamilyFromAndroidForMinApi16() {
    setUpAppCompat()
    val provider = createProvider()
    val properties = provider.getProperties(createComponents(component(TEXT_VIEW)))
    assertThat(properties.doesNotContain(AUTO_URI, ATTR_FONT_FAMILY)).isTrue()
    assertThat(properties.contains(ANDROID_URI, ATTR_FONT_FAMILY)).isTrue()
  }

  fun testSrcCompatIncludedWhenUsingAppCompat() {
    setUpAppCompat()
    val provider = createProvider()
    val properties = provider.getProperties(createComponents(component(IMAGE_VIEW)))
    assertThat(properties.doesNotContain(ANDROID_URI, ATTR_SRC)).isTrue()
    assertThat(properties.contains(AUTO_URI, ATTR_SRC_COMPAT)).isTrue()
  }

  fun testSrcCompatNotIncludedWhenNotUsingAppCompat() {
    val provider = createProvider()
    val properties = provider.getProperties(createComponents(component(IMAGE_VIEW)))
    assertThat(properties.contains(ANDROID_URI, ATTR_SRC)).isTrue()
    assertThat(properties.doesNotContain(AUTO_URI, ATTR_SRC_COMPAT)).isTrue()
  }

  fun testCustomViewProperties() {
    setUpCustomView()
    val provider = createProvider()
    val properties = provider.getProperties(createComponents(component(CUSTOM_TAG)))
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsAllIn(viewAttrs)
    assertThat(properties.getByNamespace("").keys).contains(ATTR_STYLE)
    assertThat(properties.getByNamespace(ANDROID_URI).keys).containsAllIn(linearLayoutAttrs)
    assertThat(properties.doesNotContain(ANDROID_URI, ATTR_TEXT)).isTrue()
    assertThat(properties.getByNamespace(CUSTOM_NAMESPACE).keys).containsAllOf(ATTR_LEGEND, ATTR_LABEL_POS)
  }

  fun testToolTip() {
    setUpCustomView()
    val provider = createProvider()
    val properties = provider.getProperties(createComponents(component(CUSTOM_TAG)))
    val id = properties[ANDROID_URI, ATTR_ID]
    val legend = properties[CUSTOM_NAMESPACE, ATTR_LEGEND]
    assertThat(id.tooltip.trim()).isEqualTo(EXPECTED_ID_TOOLTIP.trim())
    assertThat(legend.tooltip).isEqualTo("legend")
  }

  private fun setUpAppCompat() {
    MockAppCompat.setUp(this, myFacet, myFixture)
  }

  private fun createProvider(): NelePropertiesProvider {
    val model = NelePropertiesModel(testRootDisposable, myFacet)
    return NelePropertiesProvider(model)
  }

  private fun createViewTagComponent(): List<NlComponent> {
    val builder = model("view.xml", component(VIEW))
    val nlModel = builder.build()
    return nlModel.components
  }

  private fun setUpCustomView() {
    @Language("XML")
    val attrsSrc = """<?xml version="1.0" encoding="utf-8"?>
      <resources>
        <declare-styleable name="PieChart">
          <attr name="legend" format="boolean" />
          <attr name="labelPosition" format="enum">
            <enum name="left" value="0"/>
            <enum name="right" value="1"/>
          </attr>
        </declare-styleable>
      </resources>
      """.trimIndent()

    @Language("JAVA")
    val javaSrc = """
      package com.example;

      import android.content.Context;
      import android.view.View;

      public class PieChart extends View {
          public PieChart(Context context) {
              super(context);
          }
      }
      """.trimIndent()

    myFixture.addFileToProject("res/values/attrs.xml", attrsSrc)
    myFixture.addFileToProject("src/com/example/PieChart.java", javaSrc)
  }
}
