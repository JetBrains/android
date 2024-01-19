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
import com.android.tools.idea.res.RESOURCE_ICON_SIZE
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.createAndroidProjectBuilderForDefaultTestProjectStructure
import com.android.tools.idea.testing.ui.FileOpenCaptureRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ColorIcon
import java.awt.Color
import java.awt.Rectangle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

abstract class InspectorPropertyItemTestBase(protected val projectRule: AndroidProjectRule) {
  protected val fileOpenCaptureRule = FileOpenCaptureRule(projectRule)
  protected var model: InspectorModel? = null

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(fileOpenCaptureRule).around(EdtRule())!!

  @Before
  fun setUp() {
    val project = projectRule.project
    model = runInEdtAndGet {
      model(
        projectRule.testRootDisposable,
        project,
        FakeTreeSettings(),
        body = DemoExample.setUpDemo(projectRule.fixture),
      )
    }
    projectRule.replaceService(PropertiesComponent::class.java, PropertiesComponentMock())
    model!!.resourceLookup.dpi = 560
    PropertiesSettings.dimensionUnits = DimensionUnits.PIXELS
  }

  @After
  fun tearDown() {
    model = null
  }

  protected suspend fun dimensionDpPropertyOf(value: String?): InspectorPropertyItem =
    createTestProperty(
      "x",
      PropertyType.DIMENSION_DP,
      value,
      null,
      emptyList(),
      fakeComposeNode,
      model!!,
    )

  protected suspend fun dimensionSpPropertyOf(value: String?): InspectorPropertyItem =
    createTestProperty(
      "textSize",
      PropertyType.DIMENSION_SP,
      value,
      null,
      emptyList(),
      fakeComposeNode,
      model!!,
    )

  protected suspend fun dimensionEmPropertyOf(value: String?): InspectorPropertyItem =
    createTestProperty(
      "lineSpacing",
      PropertyType.DIMENSION_EM,
      value,
      null,
      emptyList(),
      fakeComposeNode,
      model!!,
    )

  protected suspend fun dimensionPropertyOf(value: String?): InspectorPropertyItem {
    val node = model!!["title"]!!
    return createTestProperty(
      ATTR_PADDING_TOP,
      PropertyType.DIMENSION,
      value,
      null,
      emptyList(),
      node,
      model!!,
    )
  }

  protected suspend fun dimensionFloatPropertyOf(value: String?): InspectorPropertyItem {
    val node = model!!["title"]!!
    return createTestProperty(
      ATTR_PADDING_TOP,
      PropertyType.DIMENSION_FLOAT,
      value,
      null,
      emptyList(),
      node,
      model!!,
    )
  }

  protected suspend fun textSizePropertyOf(value: String?): InspectorPropertyItem {
    val node = model!!["title"]!!
    return createTestProperty(
      ATTR_TEXT_SIZE,
      PropertyType.DIMENSION_FLOAT,
      value,
      null,
      emptyList(),
      node,
      model!!,
    )
  }

  private val fakeComposeNode: ComposeViewNode =
    ComposeViewNode(
      -2L,
      "Text",
      null,
      Rectangle(20, 20, 600, 200),
      null,
      "",
      0,
      0,
      0,
      "Text.kt",
      composePackageHash = 1777,
      composeOffset = 420,
      composeLineNumber = 17,
      0,
      0,
    )

  protected suspend fun browseProperty(
    attrName: String,
    type: PropertyType,
    source: ResourceReference?,
  ) {
    val node = model!!["title"]!!
    val property =
      createTestProperty(attrName, type, null, source ?: node.layout, emptyList(), node, model!!)
    runInEdtAndWait { property.helpSupport.browse() }
  }

  protected suspend fun colorPropertyOf(value: String?): InspectorPropertyItem =
    createTestProperty(
      "color",
      PropertyType.COLOR,
      value,
      null,
      emptyList(),
      fakeComposeNode,
      model!!,
    )
}

class InspectorPropertyItemTest : InspectorPropertyItemTestBase(AndroidProjectRule.onDisk()) {
  @Test
  fun testFormatDimensionInPixels() = runBlocking {
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
  fun testFormatDimensionInDp() = runBlocking {
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
  fun testFormatDimensionFloatInPixels() = runBlocking {
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
  fun testFormatDimensionFloatInDp() = runBlocking {
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
  fun testFormatTextSizeInPixels() = runBlocking {
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
  fun testFormatTextSizeInSp() = runBlocking {
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
  fun testFormatDimensionDpInDp() = runBlocking {
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
  fun testFormatDimensionDpInPixels() = runBlocking {
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
  fun testFormatDimensionSpInDp() = runBlocking {
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
  fun testFormatDimensionSpInPixels() = runBlocking {
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
  fun testFormatDimensionEmInDp() = runBlocking {
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
  fun testFormatDimensionEmInPixels() = runBlocking {
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
  fun testBrowseBackgroundInLayout() = runBlocking {
    browseProperty(ATTR_BACKGROUND, PropertyType.DRAWABLE, null)
    fileOpenCaptureRule.checkEditor("demo.xml", 14, "framework:background=\"@drawable/battery\"")
  }

  @Test
  fun testColor() = runBlocking {
    assertThat(colorPropertyOf("#0000FF").colorButton?.actionIcon)
      .isEqualTo(JBUIScale.scaleIcon(ColorIcon(RESOURCE_ICON_SIZE, Color(0x0000FF), false)))
  }
}

class InspectorPropertyItemTestWithSdk :
  InspectorPropertyItemTestBase(
    AndroidProjectRule.withAndroidModel(
        createAndroidProjectBuilderForDefaultTestProjectStructure()
          .copy(applicationIdFor = { variant -> "com.example" })
      )
      .named(InspectorPropertyItemTestWithSdk::class.simpleName)
  ) {

  @Test
  fun testBrowseTextSizeFromTextAppearance() = runBlocking {
    val textAppearance =
      ResourceReference.style(ResourceNamespace.ANDROID, "TextAppearance.Material.Body1")
    browseProperty(ATTR_TEXT_SIZE, PropertyType.INT32, textAppearance)
    fileOpenCaptureRule.checkEditor(
      "styles_material.xml",
      228,
      "<item name=\"textSize\">@dimen/text_size_body_1_material</item>",
    )
  }
}
