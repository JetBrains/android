/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.properties

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_PADDING_TOP
import com.android.SdkConstants.ATTR_TEXT_SIZE
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.util.DemoExample
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.util.FileOpenCaptureRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Rectangle
import com.android.tools.idea.layoutinspector.properties.PropertyType as Type

abstract class InspectorPropertyItemTestBase(protected val projectRule: AndroidProjectRule) {
  protected val fileOpenCaptureRule = FileOpenCaptureRule(projectRule)
  protected var model: InspectorModel? = null

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(fileOpenCaptureRule).around(EdtRule())!!

  @Before
  fun setUp() {
    val project = projectRule.project
    model = model(project, FakeTreeSettings(), body = DemoExample.setUpDemo(projectRule.fixture))
    projectRule.replaceService(PropertiesComponent::class.java, PropertiesComponentMock())
    model!!.resourceLookup.dpi = 560
    PropertiesSettings.dimensionUnits = DimensionUnits.PIXELS
  }

  @After
  fun tearDown() {
    model = null
  }

  protected fun dimensionDpPropertyOf(value: String?): InspectorPropertyItem {
    val nodeId = fakeComposeNode.drawId
    return InspectorPropertyItem(ANDROID_URI, "x", Type.DIMENSION_DP, value, PropertySection.DECLARED, null, nodeId, model!!)
  }

  protected fun dimensionSpPropertyOf(value: String?): InspectorPropertyItem {
    val nodeId = fakeComposeNode.drawId
    return InspectorPropertyItem(ANDROID_URI, "textSize", Type.DIMENSION_SP, value, PropertySection.DECLARED, null, nodeId, model!!)
  }

  protected fun dimensionEmPropertyOf(value: String?): InspectorPropertyItem {
    val nodeId = fakeComposeNode.drawId
    return InspectorPropertyItem(ANDROID_URI, "lineSpacing", Type.DIMENSION_EM, value, PropertySection.DECLARED, null, nodeId, model!!)
  }

  protected fun dimensionPropertyOf(value: String?): InspectorPropertyItem {
    val nodeId = model!!["title"]!!.drawId
    return InspectorPropertyItem(ANDROID_URI, ATTR_PADDING_TOP, Type.DIMENSION, value, PropertySection.DECLARED, null, nodeId, model!!)
  }

  protected fun dimensionFloatPropertyOf(value: String?): InspectorPropertyItem {
    val nodeId = model!!["title"]!!.drawId
    return InspectorPropertyItem(ANDROID_URI, ATTR_PADDING_TOP, Type.DIMENSION_FLOAT, value, PropertySection.DECLARED, null, nodeId,
                                 model!!)
  }

  protected fun textSizePropertyOf(value: String?): InspectorPropertyItem {
    val nodeId = model!!["title"]!!.drawId
    return InspectorPropertyItem(ANDROID_URI, ATTR_TEXT_SIZE, Type.DIMENSION_FLOAT, value, PropertySection.DECLARED, null, nodeId, model!!)
  }

  private val fakeComposeNode: ComposeViewNode =
    ComposeViewNode(-2L, "Text", null, Rectangle(20, 20, 600, 200), null, "",
                    0, 0, 0, "Text.kt", composePackageHash = 1777, composeOffset = 420, composeLineNumber = 17, 0, 0)

  protected fun browseProperty(attrName: String, type: Type, source: ResourceReference?) {
    val node = model!!["title"]!!
    val property = InspectorPropertyItem(
      ANDROID_URI, attrName, attrName, type, null, PropertySection.DECLARED, source ?: node.layout, node.drawId, model!!)
    property.helpSupport.browse()
  }
}

@RunsInEdt
class InspectorPropertyItemTest: InspectorPropertyItemTestBase(AndroidProjectRule.onDisk()) {
  @Test
  fun testFormatDimensionInPixels() {
    assertThat(dimensionPropertyOf("").value).isEqualTo("")
    assertThat(dimensionPropertyOf("-1").value).isEqualTo("-1")
    assertThat(dimensionPropertyOf("-2147483648").value).isEqualTo("-2147483648")
    assertThat(dimensionPropertyOf("84").value).isEqualTo("84px")
    assertThat(dimensionPropertyOf("2168").value).isEqualTo("2168px")

    model!!.resourceLookup.dpi = -1
    assertThat(dimensionPropertyOf("").value).isEqualTo("")
    assertThat(dimensionPropertyOf("-1").value).isEqualTo("-1")
    assertThat(dimensionPropertyOf("-2147483648").value).isEqualTo("-2147483648")
    assertThat(dimensionPropertyOf("84").value).isEqualTo("84px")
    assertThat(dimensionPropertyOf("2168").value).isEqualTo("2168px")
  }

  @Test
  fun testFormatDimensionInDp() {
    PropertiesSettings.dimensionUnits = DimensionUnits.DP
    assertThat(dimensionPropertyOf("").value).isEqualTo("")
    assertThat(dimensionPropertyOf("-1").value).isEqualTo("-1")
    assertThat(dimensionPropertyOf("-2147483648").value).isEqualTo("-2147483648")
    assertThat(dimensionPropertyOf("84").value).isEqualTo("24dp")
    assertThat(dimensionPropertyOf("2168").value).isEqualTo("619dp")

    model!!.resourceLookup.dpi = null
    assertThat(dimensionPropertyOf("").value).isEqualTo("")
    assertThat(dimensionPropertyOf("-1").value).isEqualTo("-1")
    assertThat(dimensionPropertyOf("-2147483648").value).isEqualTo("-2147483648")
    assertThat(dimensionPropertyOf("84").value).isEqualTo("84px")
    assertThat(dimensionPropertyOf("2168").value).isEqualTo("2168px")
  }

  @Test
  fun testFormatDimensionFloatInPixels() {
    assertThat(dimensionFloatPropertyOf("").value).isEqualTo("")
    assertThat(dimensionFloatPropertyOf("0").value).isEqualTo("0px")
    assertThat(dimensionFloatPropertyOf("0.5").value).isEqualTo("0.5px")
    assertThat(dimensionFloatPropertyOf("0.499999999").value).isEqualTo("0.5px")
    assertThat(dimensionFloatPropertyOf("0.1234567").value).isEqualTo("0.123px")

    model!!.resourceLookup.dpi = null
    assertThat(dimensionFloatPropertyOf("").value).isEqualTo("")
    assertThat(dimensionFloatPropertyOf("0").value).isEqualTo("0px")
    assertThat(dimensionFloatPropertyOf("0.5").value).isEqualTo("0.5px")
    assertThat(dimensionFloatPropertyOf("0.499999999").value).isEqualTo("0.5px")
    assertThat(dimensionFloatPropertyOf("0.1234567").value).isEqualTo("0.123px")
  }

  @Test
  fun testFormatDimensionFloatInDp() {
    PropertiesSettings.dimensionUnits = DimensionUnits.DP
    assertThat(dimensionFloatPropertyOf("").value).isEqualTo("")
    assertThat(dimensionFloatPropertyOf("0").value).isEqualTo("0dp")
    assertThat(dimensionFloatPropertyOf("1.75").value).isEqualTo("0.5dp")
    assertThat(dimensionFloatPropertyOf("1.749").value).isEqualTo("0.5dp")
    assertThat(dimensionFloatPropertyOf("1.234567").value).isEqualTo("0.353dp")

    model!!.resourceLookup.dpi = null
    assertThat(dimensionFloatPropertyOf("").value).isEqualTo("")
    assertThat(dimensionFloatPropertyOf("0").value).isEqualTo("0px")
    assertThat(dimensionFloatPropertyOf("0.5").value).isEqualTo("0.5px")
    assertThat(dimensionFloatPropertyOf("0.499999999").value).isEqualTo("0.5px")
    assertThat(dimensionFloatPropertyOf("0.1234567").value).isEqualTo("0.123px")
  }

  @Test
  fun testFormatTextSizeInPixels() {
    assertThat(textSizePropertyOf("").value).isEqualTo("")
    assertThat(textSizePropertyOf("49.0").value).isEqualTo("49.0px")
    model!!.resourceLookup.fontScale = 0.9f
    assertThat(textSizePropertyOf("44.0").value).isEqualTo("44.0px")

    model!!.resourceLookup.dpi = null
    assertThat(textSizePropertyOf("").value).isEqualTo("")
    assertThat(textSizePropertyOf("49.0").value).isEqualTo("49.0px")
    model!!.resourceLookup.fontScale = 0.9f
    assertThat(textSizePropertyOf("44.0").value).isEqualTo("44.0px")
  }

  @Test
  fun testFormatTextSizeInSp() {
    PropertiesSettings.dimensionUnits = DimensionUnits.DP
    model!!.resourceLookup.fontScale = 1.0f
    assertThat(textSizePropertyOf("").value).isEqualTo("")
    assertThat(textSizePropertyOf("49.0").value).isEqualTo("14.0sp")
    model!!.resourceLookup.fontScale = 0.9f
    assertThat(textSizePropertyOf("44.0").value).isEqualTo("14.0sp")
    model!!.resourceLookup.fontScale = 1.3f
    assertThat(textSizePropertyOf("64.0").value).isEqualTo("14.1sp")

    model!!.resourceLookup.dpi = null
    assertThat(textSizePropertyOf("").value).isEqualTo("")
    assertThat(textSizePropertyOf("49.0").value).isEqualTo("49.0px")
    model!!.resourceLookup.fontScale = 0.9f
    assertThat(textSizePropertyOf("44.0").value).isEqualTo("44.0px")
  }

  @Test
  fun testFormatDimensionDpInDp() {
    PropertiesSettings.dimensionUnits = DimensionUnits.DP
    assertThat(dimensionDpPropertyOf("").value).isEqualTo("")
    assertThat(dimensionDpPropertyOf("0").value).isEqualTo("0dp")
    assertThat(dimensionDpPropertyOf("1.75").value).isEqualTo("1.75dp")
    assertThat(dimensionDpPropertyOf("1.74978").value).isEqualTo("1.75dp")
    assertThat(dimensionDpPropertyOf("1.234567").value).isEqualTo("1.235dp")

    model!!.resourceLookup.dpi = null
    assertThat(dimensionDpPropertyOf("").value).isEqualTo("")
    assertThat(dimensionDpPropertyOf("0").value).isEqualTo("0dp")
    assertThat(dimensionDpPropertyOf("0.5").value).isEqualTo("0.5dp")
    assertThat(dimensionDpPropertyOf("0.499999999").value).isEqualTo("0.5dp")
    assertThat(dimensionDpPropertyOf("0.1234567").value).isEqualTo("0.123dp")
  }

  @Test
  fun testFormatDimensionDpInPixels() {
    PropertiesSettings.dimensionUnits = DimensionUnits.PIXELS
    assertThat(dimensionDpPropertyOf("").value).isEqualTo("")
    assertThat(dimensionDpPropertyOf("0").value).isEqualTo("0px")
    assertThat(dimensionDpPropertyOf("0.5").value).isEqualTo("1.75px")
    assertThat(dimensionDpPropertyOf("0.4999").value).isEqualTo("1.75px")
    assertThat(dimensionDpPropertyOf("1.23456").value).isEqualTo("4.321px")

    model!!.resourceLookup.dpi = null
    assertThat(dimensionDpPropertyOf("").value).isEqualTo("")
    assertThat(dimensionDpPropertyOf("0").value).isEqualTo("0dp")
    assertThat(dimensionDpPropertyOf("0.5").value).isEqualTo("0.5dp")
    assertThat(dimensionDpPropertyOf("0.499999999").value).isEqualTo("0.5dp")
    assertThat(dimensionDpPropertyOf("0.1234567").value).isEqualTo("0.123dp")
  }

  @Test
  fun testFormatDimensionSpInDp() {
    PropertiesSettings.dimensionUnits = DimensionUnits.DP
    assertThat(dimensionSpPropertyOf("").value).isEqualTo("")
    assertThat(dimensionSpPropertyOf("12.0").value).isEqualTo("12.0sp")
    model!!.resourceLookup.fontScale = 0.9f
    assertThat(dimensionSpPropertyOf("12.0").value).isEqualTo("12.0sp")

    model!!.resourceLookup.dpi = null
    assertThat(dimensionSpPropertyOf("").value).isEqualTo("")
    assertThat(dimensionSpPropertyOf("12.0").value).isEqualTo("12.0sp")
    model!!.resourceLookup.fontScale = 0.9f
    assertThat(dimensionSpPropertyOf("12.0").value).isEqualTo("12.0sp")
  }

  @Test
  fun testFormatDimensionSpInPixels() {
    PropertiesSettings.dimensionUnits = DimensionUnits.PIXELS
    model!!.resourceLookup.fontScale = 1.0f
    assertThat(dimensionSpPropertyOf("").value).isEqualTo("")
    assertThat(dimensionSpPropertyOf("14.0").value).isEqualTo("49.0px")
    model!!.resourceLookup.fontScale = 0.9f
    assertThat(dimensionSpPropertyOf("14.0").value).isEqualTo("44.1px")
    model!!.resourceLookup.fontScale = 1.3f
    assertThat(dimensionSpPropertyOf("14.0").value).isEqualTo("63.7px")

    model!!.resourceLookup.dpi = null
    assertThat(dimensionSpPropertyOf("").value).isEqualTo("")
    assertThat(dimensionSpPropertyOf("14.0").value).isEqualTo("14.0sp")
    model!!.resourceLookup.fontScale = 0.9f
    assertThat(dimensionSpPropertyOf("14.0").value).isEqualTo("14.0sp")
  }

  @Test
  fun testFormatDimensionEmInDp() {
    PropertiesSettings.dimensionUnits = DimensionUnits.DP
    assertThat(dimensionEmPropertyOf("").value).isEqualTo("")
    assertThat(dimensionEmPropertyOf("1.5").value).isEqualTo("1.5em")
    model!!.resourceLookup.fontScale = 0.9f
    assertThat(dimensionEmPropertyOf("1.5").value).isEqualTo("1.5em")

    model!!.resourceLookup.dpi = null
    assertThat(dimensionEmPropertyOf("").value).isEqualTo("")
    assertThat(dimensionEmPropertyOf("1.5").value).isEqualTo("1.5em")
    model!!.resourceLookup.fontScale = 0.9f
    assertThat(dimensionEmPropertyOf("1.5").value).isEqualTo("1.5em")
  }

  @Test
  fun testFormatDimensionEmInPixels() {
    assertThat(dimensionEmPropertyOf("").value).isEqualTo("")
    assertThat(dimensionEmPropertyOf("1.5").value).isEqualTo("1.5em")
    model!!.resourceLookup.fontScale = 0.9f
    assertThat(dimensionEmPropertyOf("1.5").value).isEqualTo("1.5em")

    model!!.resourceLookup.dpi = null
    assertThat(dimensionEmPropertyOf("").value).isEqualTo("")
    assertThat(dimensionEmPropertyOf("1.5").value).isEqualTo("1.5em")
    model!!.resourceLookup.fontScale = 0.9f
    assertThat(dimensionEmPropertyOf("1.5").value).isEqualTo("1.5em")
  }

  @Test
  fun testBrowseBackgroundInLayout() {
    browseProperty(ATTR_BACKGROUND, Type.DRAWABLE, null)
    fileOpenCaptureRule.checkEditor("demo.xml", 14, "framework:background=\"@drawable/battery\"")
  }
}

@RunsInEdt
class InspectorPropertyItemTestWithSdk: InspectorPropertyItemTestBase(AndroidProjectRule.withSdk()) {
  @Test
  fun testBrowseTextSizeFromTextAppearance() {
    val textAppearance = ResourceReference.style(ResourceNamespace.ANDROID, "TextAppearance.Material.Body1")
    browseProperty(ATTR_TEXT_SIZE, Type.INT32, textAppearance)
    fileOpenCaptureRule.checkEditor("styles_material.xml", 228, "<item name=\"textSize\">@dimen/text_size_body_1_material</item>")
  }
}
