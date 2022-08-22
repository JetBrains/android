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
package com.android.tools.idea.compose.pickers.preview

import com.android.sdklib.devices.Device
import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.compose.pickers.base.model.PsiPropertiesModel
import com.android.tools.idea.compose.pickers.base.property.PsiPropertyItem
import com.android.tools.idea.compose.pickers.base.tracking.ComposePickerTracker
import com.android.tools.idea.compose.pickers.common.tracking.NoOpTracker
import com.android.tools.idea.compose.pickers.preview.enumsupport.UiMode
import com.android.tools.idea.compose.pickers.preview.enumsupport.UiModeWithNightMaskEnumValue
import com.android.tools.idea.compose.pickers.preview.enumsupport.devices.DeviceEnumValueBuilder
import com.android.tools.idea.compose.pickers.preview.enumsupport.devices.ReferencePhoneConfig
import com.android.tools.idea.compose.pickers.preview.model.PreviewPickerPropertiesModel
import com.android.tools.idea.compose.pickers.preview.property.DimUnit
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder
import com.android.tools.idea.compose.preview.namespaceVariations
import com.android.tools.idea.compose.preview.util.ComposePreviewElement
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.Sdks
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private fun ComposePreviewElement.annotationText(): String =
  ReadAction.compute<String, Throwable> { previewElementDefinitionPsi?.element?.text ?: "" }

@RunWith(Parameterized::class)
class PreviewPickerTests(previewAnnotationPackage: String, composableAnnotationPackage: String) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}.Preview {1}.Composable")
    val namespaces = namespaceVariations
  }

  private val composableAnnotationFqName = "$composableAnnotationPackage.Composable"
  private val previewToolingPackage = previewAnnotationPackage

  @get:Rule
  val projectRule =
    ComposeProjectRule(
      previewAnnotationPackage = previewAnnotationPackage,
      composableAnnotationPackage = composableAnnotationPackage
    )

  @get:Rule val edtRule = EdtRule()
  private val fixture
    get() = projectRule.fixture
  private val project
    get() = projectRule.project
  private val module
    get() = projectRule.fixture.module

  @After
  fun teardown() {
    // Flag might not get cleared if a test that overrides it fails
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.clearOverride()
  }

  @RunsInEdt
  @Test
  fun `the psi model reads the preview annotation correctly`() = runBlocking {
    @Language("kotlin")
    val fileContent =
      """
      import $composableAnnotationFqName
      import $previewToolingPackage.Preview

      @Composable
      @Preview
      fun PreviewNoParameters() {
      }

      @Composable
      @Preview("named")
      fun PreviewWithName() {
      }

      @Composable
      @Preview
      fun PreviewParameters() {
      }

      private const val nameFromConst = "Name from Const"

      @Composable
      @Preview(nameFromConst)
      fun PreviewWithNameFromConst() {
      }
    """.trimIndent()

    val file = fixture.configureByText("Test.kt", fileContent)
    val previews =
      AnnotationFilePreviewElementFinder.findPreviewMethods(fixture.project, file.virtualFile)
        .toList()
    ReadAction.run<Throwable> {
      previews[0].also { noParametersPreview ->
        val parsed =
          PreviewPickerPropertiesModel.fromPreviewElement(
            project,
            module,
            noParametersPreview.previewElementDefinitionPsi,
            NoOpTracker
          )
        assertNotNull(parsed.properties["", "name"])
        assertNull(parsed.properties.getOrNull("", "name2"))
      }
      previews[1].also { namedPreview ->
        val parsed =
          PreviewPickerPropertiesModel.fromPreviewElement(
            project,
            module,
            namedPreview.previewElementDefinitionPsi,
            NoOpTracker
          )
        assertEquals("named", parsed.properties["", "name"].value)
      }
      previews[3].also { namedPreviewFromConst ->
        val parsed =
          PreviewPickerPropertiesModel.fromPreviewElement(
            project,
            module,
            namedPreviewFromConst.previewElementDefinitionPsi,
            NoOpTracker
          )
        assertEquals("Name from Const", parsed.properties["", "name"].value)
      }
    }
  }

  @RunsInEdt
  @Test
  fun `updating model updates the psi correctly`() = runBlocking {
    Sdks.addLatestAndroidSdk(fixture.projectDisposable, module)

    @Language("kotlin")
    val annotationWithParameters =
      """
      import $composableAnnotationFqName
      import $previewToolingPackage.Preview

      @Composable
      @Preview(name = "Test")
      fun PreviewNoParameters() {
      }
      """.trimIndent()

    assertUpdatingModelUpdatesPsiCorrectly(annotationWithParameters)

    @Language("kotlin")
    val emptyAnnotation =
      """
      import $composableAnnotationFqName
      import $previewToolingPackage.Preview

      @Composable
      @Preview
      fun PreviewNoParameters() {
      }
      """.trimIndent()

    assertUpdatingModelUpdatesPsiCorrectly(emptyAnnotation)
  }

  @RunsInEdt
  @Test
  fun `supported parameters displayed correctly`() = runBlocking {
    @Language("kotlin")
    val fileContent =
      """
      import $composableAnnotationFqName
      import $previewToolingPackage.Preview

      @Composable
      @Preview(name = "Test", fontScale = 1.2f, backgroundColor = 4294901760)
      fun PreviewWithParemeters() {
      }
    """.trimIndent()

    val model = getFirstModel(fileContent)
    assertNotNull(model.properties["", "backgroundColor"].colorButton)
    assertEquals("1.2", runReadAction { model.properties["", "fontScale"].value })
    assertEquals("0xFFFF0000", runReadAction { model.properties["", "backgroundColor"].value })

    model.properties["", "fontScale"].value = "0.5"
    model.properties["", "backgroundColor"].value = "0x00FF00"

    assertEquals("0.5", runReadAction { model.properties["", "fontScale"].value })
    assertEquals("0x0000FF00", runReadAction { model.properties["", "backgroundColor"].value })
  }

  @RunsInEdt
  @Test
  fun `preview default values`() = runBlocking {
    @Language("kotlin")
    val fileContent =
      """
      import $composableAnnotationFqName
      import $previewToolingPackage.Preview

      @Composable
      @Preview(name = "Test")
      fun PreviewNoParameters() {
      }
    """.trimIndent()

    Sdks.addLatestAndroidSdk(fixture.projectDisposable, module)
    val model = getFirstModel(fileContent)
    assertEquals("1f", model.properties["", "fontScale"].defaultValue)
    assertEquals("false", model.properties["", "showBackground"].defaultValue)
    assertEquals("false", model.properties["", "showDecoration"].defaultValue)
    assertEquals("Default (en-US)", model.properties["", "locale"].defaultValue)
    assertTrue(model.properties["", "apiLevel"].defaultValue!!.toInt() > 0)

    // Note that uiMode and device, are displayed through a ComboBox option and don't actually
    // display these values
    assertEquals("Undefined", model.properties["", "uiMode"].defaultValue)
    assertEquals("Default", model.properties["", "Device"].defaultValue)

    // Hardware properties
    assertEquals("1080", model.properties["", "Width"].defaultValue)
    assertEquals("2340", model.properties["", "Height"].defaultValue)
    assertEquals("px", model.properties["", "DimensionUnit"].defaultValue)
    assertEquals("portrait", model.properties["", "Orientation"].defaultValue)
    assertEquals("440", model.properties["", "Density"].defaultValue)
    assertEquals("false", model.properties["", "IsRound"].defaultValue)
    assertEquals("0", model.properties["", "ChinSize"].defaultValue)

    // We hide the default value of some values when the value's behavior is undefined
    assertEquals(null, model.properties["", "widthDp"].defaultValue)
    assertEquals(null, model.properties["", "heightDp"].defaultValue)
    // We don't take the library's default value for color
    assertEquals(null, model.properties["", "backgroundColor"].defaultValue)
  }

  @RunsInEdt
  @Test
  fun fontScaleEditing() = runBlocking {
    @Language("kotlin")
    val fileContent =
      """
      import $composableAnnotationFqName
      import $previewToolingPackage.Preview

      @Composable
      @Preview
      fun PreviewNoParameters() {
      }
      """.trimIndent()

    val model = getFirstModel(fileContent)
    val preview =
      AnnotationFilePreviewElementFinder.findPreviewMethods(
          fixture.project,
          fixture.findFileInTempDir("Test.kt")
        )
        .first()

    fun checkFontScaleChange(newValue: String, expectedPropertyValue: String) {
      val expectedTextValue = expectedPropertyValue + 'f'

      model.properties["", "fontScale"].value = newValue
      assertEquals(expectedPropertyValue, model.properties["", "fontScale"].value)
      assertEquals("@Preview(fontScale = $expectedTextValue)", preview.annotationText())
    }

    checkFontScaleChange("1", "1.0")
    checkFontScaleChange("2.", "2.0")
    checkFontScaleChange("3.01", "3.01")
    checkFontScaleChange("4.0f", "4.0")
    checkFontScaleChange("5.0d", "5.0")
    checkFontScaleChange("6f", "6.0")
    checkFontScaleChange("7d", "7.0")
    checkFontScaleChange("8.f", "8.0")
  }

  @RunsInEdt
  @Test
  fun showBackgroundEditing() = runBlocking {
    @Language("kotlin")
    val fileContent =
      """
      import $composableAnnotationFqName
      import $previewToolingPackage.Preview

      @Composable
      @Preview
      fun PreviewNoParameters() {
      }
      """.trimIndent()

    val model = getFirstModel(fileContent)
    val preview =
      AnnotationFilePreviewElementFinder.findPreviewMethods(
          fixture.project,
          fixture.findFileInTempDir("Test.kt")
        )
        .first()

    fun checkShowBackgroundChange(newValue: String?, expectedPropertyValue: String?) {
      model.properties["", "showBackground"].value = newValue
      assertEquals(expectedPropertyValue, model.properties["", "showBackground"].value)
      assertEquals("@Preview(showBackground = $expectedPropertyValue)", preview.annotationText())
    }

    fun checkShowBackgroundEmptyChange(newValue: String?, expectedPropertyValue: String?) {
      model.properties["", "showBackground"].value = newValue
      assertEquals(expectedPropertyValue, model.properties["", "showBackground"].value)
      assertEquals("@Preview", preview.annotationText())
    }

    checkShowBackgroundChange("true", "true")
    checkShowBackgroundEmptyChange(null, null)
    checkShowBackgroundChange("false", "false")
    checkShowBackgroundEmptyChange("", null)
  }

  @Test
  fun `original order is preserved`() = runBlocking {
    @Language("kotlin")
    val fileContent =
      """
      import $composableAnnotationFqName
      import $previewToolingPackage.Preview

      @Composable
      @Preview(fontScale = 1.0f, name = "MyPreview", apiLevel = 1)
      fun PreviewWithParameters() {
      }
      """.trimIndent()

    val model = getFirstModel(fileContent)

    val properties = model.properties.values.iterator()
    assertEquals("name", properties.next().name)
    assertEquals("group", properties.next().name)
    assertEquals("apiLevel", properties.next().name)
    assertEquals("theme", properties.next().name)
    assertEquals("widthDp", properties.next().name)
    assertEquals("heightDp", properties.next().name)
    assertEquals("locale", properties.next().name)
    assertEquals("fontScale", properties.next().name)
  }

  @RunsInEdt
  @Test
  fun testDevicePropertiesTracked() {
    val (testTracker, model) = simpleTrackingTestSetup()

    model.properties["", "Device"].value = "hello world"

    model.properties["", "Orientation"].value = "portrait"
    model.properties["", "Orientation"].value = "landscape"
    model.properties["", "Orientation"].value = "bad input"

    model.properties["", "Density"].value = "480" // XXHIGH
    model.properties["", "Density"].value = "470" // Close to XXHIGH
    model.properties["", "Density"].value = "320" // XHIGH
    model.properties["", "Density"].value = "10000" // Extremely high (XXXHIGH is closest)
    model.properties["", "Density"].value = "bad input"

    model.properties["", "DimensionUnit"].value = "dp"
    model.properties["", "DimensionUnit"].value = "px"
    model.properties["", "DimensionUnit"].value = "bad input"

    model.properties["", "Width"].value = "100"
    model.properties["", "Height"].value = "200"

    assertEquals(14, testTracker.valuesRegistered.size)
    var index = 0
    // Device
    assertEquals(
      PreviewPickerValue.UNSUPPORTED_OR_OPEN_ENDED,
      testTracker.valuesRegistered[index++]
    )

    // Orientation
    assertEquals(PreviewPickerValue.ORIENTATION_PORTRAIT, testTracker.valuesRegistered[index++])
    assertEquals(PreviewPickerValue.ORIENTATION_LANDSCAPE, testTracker.valuesRegistered[index++])
    assertEquals(
      PreviewPickerValue.UNKNOWN_PREVIEW_PICKER_VALUE,
      testTracker.valuesRegistered[index++]
    )

    // Density
    assertEquals(PreviewPickerValue.DENSITY_XX_HIGH, testTracker.valuesRegistered[index++])
    assertEquals(PreviewPickerValue.DENSITY_XX_HIGH, testTracker.valuesRegistered[index++])
    assertEquals(PreviewPickerValue.DENSITY_X_HIGH, testTracker.valuesRegistered[index++])
    assertEquals(PreviewPickerValue.DENSITY_XXX_HIGH, testTracker.valuesRegistered[index++])
    assertEquals(
      PreviewPickerValue.UNKNOWN_PREVIEW_PICKER_VALUE,
      testTracker.valuesRegistered[index++]
    )

    // DimensionUnit
    assertEquals(PreviewPickerValue.UNIT_DP, testTracker.valuesRegistered[index++])
    assertEquals(PreviewPickerValue.UNIT_PIXELS, testTracker.valuesRegistered[index++])
    assertEquals(
      PreviewPickerValue.UNKNOWN_PREVIEW_PICKER_VALUE,
      testTracker.valuesRegistered[index++]
    )

    // Width/Height
    assertEquals(
      PreviewPickerValue.UNSUPPORTED_OR_OPEN_ENDED,
      testTracker.valuesRegistered[index++]
    )
    assertEquals(PreviewPickerValue.UNSUPPORTED_OR_OPEN_ENDED, testTracker.valuesRegistered[index])
  }

  @RunsInEdt
  @Test
  fun testTrackedValuesOfDeviceOptions() {
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.override(false)
    val (testTracker, model) = simpleTrackingTestSetup()

    val deviceProperty =
      model.properties[
        "",
        "Device"] // Which parameter doesn't matter in this context, but best to test with Device
    val deviceOptions =
      DeviceEnumValueBuilder()
        .addGenericById(
          "My Generic",
          "my_generic"
        ) // This is an example of an option generated from the Device Manager
        .includeDefaultsAndBuild()
        .associateBy { it.display } // Easier to test

    deviceOptions["Phone"]!!.select(deviceProperty)
    assertEquals("spec:shape=Normal,width=411,height=891,unit=dp,dpi=420", deviceProperty.value)

    deviceOptions["Foldable"]!!.select(deviceProperty)
    assertEquals("spec:shape=Normal,width=674,height=841,unit=dp,dpi=480", deviceProperty.value)

    deviceOptions["Tablet"]!!.select(deviceProperty)
    assertEquals("spec:shape=Normal,width=1280,height=800,unit=dp,dpi=480", deviceProperty.value)

    deviceOptions["Desktop"]!!.select(deviceProperty)
    assertEquals("spec:shape=Normal,width=1920,height=1080,unit=dp,dpi=480", deviceProperty.value)

    deviceOptions["Square"]!!.select(deviceProperty) // Wear device
    assertEquals("spec:shape=Normal,width=300,height=300,unit=px,dpi=240", deviceProperty.value)

    deviceOptions["55.0\" Tv 2160p"]!!.select(deviceProperty) // Tv device
    assertEquals("spec:shape=Normal,width=3840,height=2160,unit=px,dpi=320", deviceProperty.value)

    deviceOptions["8.4\" Auto 768p"]!!.select(deviceProperty) // Auto
    assertEquals("spec:shape=Normal,width=1024,height=768,unit=px,dpi=320", deviceProperty.value)

    deviceOptions["My Generic"]!!.select(deviceProperty) // Device Manager example
    assertEquals("id:my_generic", deviceProperty.value)

    assertEquals(8, testTracker.valuesRegistered.size)
    var index = 0
    assertEquals(PreviewPickerValue.DEVICE_REF_PHONE, testTracker.valuesRegistered[index++])
    assertEquals(PreviewPickerValue.DEVICE_REF_FOLDABLE, testTracker.valuesRegistered[index++])
    assertEquals(PreviewPickerValue.DEVICE_REF_TABLET, testTracker.valuesRegistered[index++])
    assertEquals(PreviewPickerValue.DEVICE_REF_DESKTOP, testTracker.valuesRegistered[index++])

    // Non-reference devices
    assertEquals(PreviewPickerValue.DEVICE_REF_NONE, testTracker.valuesRegistered[index++])
    assertEquals(PreviewPickerValue.DEVICE_REF_NONE, testTracker.valuesRegistered[index++])
    assertEquals(PreviewPickerValue.DEVICE_REF_NONE, testTracker.valuesRegistered[index++])
    assertEquals(PreviewPickerValue.DEVICE_REF_NONE, testTracker.valuesRegistered[index])
  }

  @RunsInEdt
  @Test
  fun testTrackedValuesOfUiModeOptions() {
    val (testTracker, model) = simpleTrackingTestSetup()

    val uiModeProperty = model.properties["", "uiMode"]

    val nightModeOption = UiModeWithNightMaskEnumValue.NormalNightEnumValue
    val notNightOption = UiModeWithNightMaskEnumValue.NormalNotNightEnumValue

    // The Night/NotNight is not explicitly set
    val nightModeUndefined = UiMode.NORMAL

    nightModeOption.select(uiModeProperty)
    notNightOption.select(uiModeProperty)
    nightModeUndefined.select(uiModeProperty)

    assertEquals(3, testTracker.valuesRegistered.size)
    assertEquals(PreviewPickerValue.UI_MODE_NIGHT, testTracker.valuesRegistered[0])
    assertEquals(PreviewPickerValue.UI_MODE_NOT_NIGHT, testTracker.valuesRegistered[1])
    assertEquals(PreviewPickerValue.UNSUPPORTED_OR_OPEN_ENDED, testTracker.valuesRegistered[2])
  }

  @RunsInEdt
  @Test
  fun testDeviceTrackedPerModification() {
    // We need the sdk to be able to figure out devices set by ID, including the initial/default
    // device
    Sdks.addLatestAndroidSdk(projectRule.fixture.projectDisposable, module)
    val (testTracker, model) = simpleTrackingTestSetup()

    // Modifications under default device
    model.properties["", "name"].value = "my name 1"
    model.properties["", "Device"].value = "id:pixel"

    // Modifications under pixel device
    model.properties["", "name"].value = "my name 2"
    model.properties["", "Device"].value = ReferencePhoneConfig.deviceSpec()

    // Modifications under Reference Phone device
    model.properties["", "name"].value = "my name 3"

    assertEquals(5, testTracker.devicesRegistered.size)
    assertEquals("pixel_5", testTracker.devicesRegistered[0]!!.id) // Default device
    assertEquals("pixel_5", testTracker.devicesRegistered[1]!!.id)
    assertEquals("pixel", testTracker.devicesRegistered[2]!!.id) // Pixel
    assertEquals("pixel", testTracker.devicesRegistered[3]!!.id)
    assertEquals("Custom", testTracker.devicesRegistered[4]!!.displayName) // Reference Phone Device
  }

  private suspend fun assertUpdatingModelUpdatesPsiCorrectly(fileContent: String) {
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.override(false)
    val file = fixture.configureByText("Test.kt", fileContent)
    val noParametersPreview =
      AnnotationFilePreviewElementFinder.findPreviewMethods(fixture.project, file.virtualFile)
        .first()
    val model =
      ReadAction.compute<PsiPropertiesModel, Throwable> {
        PreviewPickerPropertiesModel.fromPreviewElement(
          project,
          module,
          noParametersPreview.previewElementDefinitionPsi,
          NoOpTracker
        )
      }
    var expectedModificationsCountdown = 21
    model.addListener(
      object : PropertiesModelListener<PsiPropertyItem> {
        override fun propertyValuesChanged(model: PropertiesModel<PsiPropertyItem>) {
          expectedModificationsCountdown--
        }
      }
    )

    model.properties["", "name"].value = "NoHello"
    // Try to override our previous write. Only the last one should persist
    model.properties["", "name"].value = "Hello"
    assertEquals("@Preview(name = \"Hello\")", noParametersPreview.annotationText())

    // Add other properties
    model.properties["", "group"].value = "Group2"
    model.properties["", "widthDp"].value = "32"
    assertEquals("Hello", model.properties["", "name"].value)
    assertEquals("Group2", model.properties["", "group"].value)
    assertEquals("32", model.properties["", "widthDp"].value)
    assertEquals(
      "@Preview(name = \"Hello\", group = \"Group2\", widthDp = 32)",
      noParametersPreview.annotationText()
    )

    // Device parameters modifications
    model.properties["", "Width"].value =
      "720" // In pixels, this change should populate 'device' parameter in annotation
    assertEquals(
      """@Preview(name = "Hello", group = "Group2", widthDp = 32, device = "spec:shape=Normal,width=720,height=891,unit=dp,dpi=420")""",
      noParametersPreview.annotationText()
    )

    model.properties["", "DimensionUnit"].value =
      "px" // Should modify width and height in 'device' parameter
    assertEquals(
      """@Preview(name = "Hello", group = "Group2", widthDp = 32, device = "spec:shape=Normal,width=1890,height=2339,unit=px,dpi=420")""",
      noParametersPreview.annotationText()
    )

    model.properties["", "Density"].value =
      "240" // When changing back to pixels, the width and height should be different than
    // originally
    model.properties["", "DimensionUnit"].value = "dp"
    assertEquals(
      """@Preview(name = "Hello", group = "Group2", widthDp = 32, device = "spec:shape=Normal,width=1260,height=1559,unit=dp,dpi=240")""",
      noParametersPreview.annotationText()
    )

    model.properties["", "Orientation"].value =
      "landscape" // Changing orientation swaps width/height values
    assertEquals(
      """@Preview(name = "Hello", group = "Group2", widthDp = 32, device = "spec:shape=Normal,width=1559,height=1260,unit=dp,dpi=240")""",
      noParametersPreview.annotationText()
    )

    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.override(true)
    // Trigger a change while using the DeviceSpec Language
    model.properties["", "Width"].value = "1560"
    assertEquals(
      """@Preview(name = "Hello", group = "Group2", widthDp = 32, device = "spec:width=1560dp,height=1260dp,dpi=240")""",
      noParametersPreview.annotationText()
    )

    assertEquals("false", model.properties["", "IsRound"].value)
    // Changing ChinSize to non-zero value implies setting IsRound to true
    model.properties["", "ChinSize"].value = "30"
    assertEquals("true", model.properties["", "IsRound"].value)
    assertEquals(
      """@Preview(name = "Hello", group = "Group2", widthDp = 32, device = "spec:width=1560dp,height=1260dp,dpi=240,isRound=true,chinSize=30dp")""",
      noParametersPreview.annotationText()
    )

    // When using DeviceSpec Language, conversions should support floating point
    model.properties["", "DimensionUnit"].value = DimUnit.px.name
    assertEquals("2340", model.properties["", "Width"].value)
    assertEquals("1890", model.properties["", "Height"].value)
    assertEquals("45", model.properties["", "ChinSize"].value)
    assertEquals(
      """@Preview(name = "Hello", group = "Group2", widthDp = 32, device = "spec:width=2340px,height=1890px,dpi=240,isRound=true,chinSize=45px")""",
      noParametersPreview.annotationText()
    )

    // ChinSize is ignored in the device spec if IsRound is false
    model.properties["", "IsRound"].value = "false"
    assertEquals(
      """@Preview(name = "Hello", group = "Group2", widthDp = 32, device = "spec:width=2340px,height=1890px,dpi=240")""",
      noParametersPreview.annotationText()
    )

    // Since there's no orientation parameter, it's implied from the width/height values
    assertEquals("landscape", model.properties["", "Orientation"].value)
    // When changed, it has to be reflected explicitly in the spec, without affecting the
    // width/height
    model.properties["", "Orientation"].value = "portrait"
    assertEquals(
      """@Preview(name = "Hello", group = "Group2", widthDp = 32, device = "spec:width=2340px,height=1890px,dpi=240,orientation=portrait")""",
      noParametersPreview.annotationText()
    )

    model.properties["", "Device"].value = "id:pixel_3"
    assertEquals(
      """@Preview(name = "Hello", group = "Group2", widthDp = 32, device = "id:pixel_3")""",
      noParametersPreview.annotationText()
    )
    model.properties["", "Orientation"].value = "landscape"
    assertEquals(
      """@Preview(name = "Hello", group = "Group2", widthDp = 32, device = "spec:parent=pixel_3,orientation=landscape")""",
      noParametersPreview.annotationText()
    )
    assertEquals("1080", model.properties["", "Width"].value)
    assertEquals("2160", model.properties["", "Height"].value)
    model.properties["", "Width"].value = "2000"
    assertEquals(
      """@Preview(name = "Hello", group = "Group2", widthDp = 32, device = "spec:width=2000px,height=2160px,dpi=440,orientation=landscape")""",
      noParametersPreview.annotationText()
    )
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.clearOverride()

    // Clear values
    model.properties["", "group"].value = null
    model.properties["", "widthDp"].value = "    " // Blank value is the same as null value
    model.properties["", "Device"].value = null
    assertEquals("@Preview(name = \"Hello\")", noParametersPreview.annotationText())

    model.properties["", "name"].value = null
    try {
      model.properties["", "notexists"].value = "3"
      fail("Nonexistent property should throw NoSuchElementException")
    } catch (expected: NoSuchElementException) {}

    // Verify final values on model
    assertNull(model.properties["", "name"].value)
    assertNull(model.properties["", "group"].value)
    assertNull(model.properties["", "widthDp"].value)
    // Verify final state of file
    assertEquals("@Preview", noParametersPreview.annotationText())
    // Verify that every modification (setting, overwriting and deleting values) triggered the
    // listener
    assertEquals(0, expectedModificationsCountdown)
  }

  private fun simpleTrackingTestSetup(): Pair<TestTracker, PsiPropertiesModel> {
    @Language("kotlin")
    val fileContent =
      """
      import $composableAnnotationFqName
      import $previewToolingPackage.Preview

      @Composable
      @Preview(name = "Test")
      fun PreviewNoParameters() {
      }
    """.trimIndent()
    val testTracker = TestTracker()
    val model = runBlocking { getFirstModel(fileContent, testTracker) }
    return Pair(testTracker, model)
  }

  private suspend fun getFirstModel(
    fileContent: String,
    tracker: ComposePickerTracker = NoOpTracker
  ): PsiPropertiesModel {
    val file = fixture.configureByText("Test.kt", fileContent)
    val preview =
      AnnotationFilePreviewElementFinder.findPreviewMethods(fixture.project, file.virtualFile)
        .first()
    ConfigurationManager.getOrCreateInstance(module)
    return ReadAction.compute<PsiPropertiesModel, Throwable> {
      PreviewPickerPropertiesModel.fromPreviewElement(
        project,
        module,
        preview.previewElementDefinitionPsi,
        tracker
      )
    }
  }
}

private class TestTracker : ComposePickerTracker {
  val valuesRegistered = mutableListOf<PreviewPickerValue>()
  val devicesRegistered = mutableListOf<Device?>()

  override fun registerModification(name: String, value: PreviewPickerValue, device: Device?) {
    valuesRegistered.add(value)
    devicesRegistered.add(device)
  }

  override fun pickerShown() {} // Not tested
  override fun pickerClosed() {} // Not tested
  override fun logUsageData() {} // Not tested
}
