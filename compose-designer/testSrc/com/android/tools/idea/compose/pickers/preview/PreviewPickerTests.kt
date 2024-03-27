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
import com.android.tools.idea.compose.PsiComposePreviewElement
import com.android.tools.idea.compose.pickers.base.model.PsiPropertiesModel
import com.android.tools.idea.compose.pickers.base.property.PsiPropertyItem
import com.android.tools.idea.compose.pickers.base.tracking.ComposePickerTracker
import com.android.tools.idea.compose.pickers.common.tracking.NoOpTracker
import com.android.tools.idea.compose.pickers.preview.enumsupport.UiMode
import com.android.tools.idea.compose.pickers.preview.enumsupport.UiModeWithNightMaskEnumValue
import com.android.tools.idea.compose.pickers.preview.model.PreviewPickerPropertiesModel
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder
import com.android.tools.idea.compose.preview.COMPOSABLE_ANNOTATION_FQN
import com.android.tools.idea.compose.preview.PREVIEW_TOOLING_PACKAGE
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.testing.Sdks
import com.android.tools.preview.config.ReferencePhoneConfig
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

private fun PsiComposePreviewElement.annotationText(): String =
  ReadAction.compute<String, Throwable> { previewElementDefinition?.element?.text ?: "" }

class PreviewPickerTests {

  @get:Rule val projectRule = ComposeProjectRule()

  @get:Rule val edtRule = EdtRule()
  private val fixture
    get() = projectRule.fixture

  private val project
    get() = projectRule.project

  private val module
    get() = projectRule.fixture.module

  @RunsInEdt
  @Test
  fun `the psi model reads the preview annotation correctly`() = runBlocking {
    @Language("kotlin")
    val fileContent =
      """
      import $COMPOSABLE_ANNOTATION_FQN
      import $PREVIEW_TOOLING_PACKAGE.Preview

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
    """
        .trimIndent()

    val file = fixture.configureByText("Test.kt", fileContent)
    val previews =
      AnnotationFilePreviewElementFinder.findPreviewElements(fixture.project, file.virtualFile)
        .toList()
    ReadAction.run<Throwable> {
      previews[0].also { noParametersPreview ->
        val parsed =
          PreviewPickerPropertiesModel.fromPreviewElement(
            project,
            module,
            noParametersPreview.previewElementDefinition,
            NoOpTracker,
          )
        assertNotNull(parsed.properties["", "name"])
        assertNull(parsed.properties.getOrNull("", "name2"))
      }
      previews[1].also { namedPreview ->
        val parsed =
          PreviewPickerPropertiesModel.fromPreviewElement(
            project,
            module,
            namedPreview.previewElementDefinition,
            NoOpTracker,
          )
        assertEquals("named", parsed.properties["", "name"].value)
      }
      previews[3].also { namedPreviewFromConst ->
        val parsed =
          PreviewPickerPropertiesModel.fromPreviewElement(
            project,
            module,
            namedPreviewFromConst.previewElementDefinition,
            NoOpTracker,
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
      import $COMPOSABLE_ANNOTATION_FQN
      import $PREVIEW_TOOLING_PACKAGE.Preview

      @Composable
      @Preview(name = "Test")
      fun PreviewNoParameters() {
      }
      """
        .trimIndent()

    assertUpdatingModelUpdatesPsiCorrectly(annotationWithParameters)

    @Language("kotlin")
    val emptyAnnotation =
      """
      import $COMPOSABLE_ANNOTATION_FQN
      import $PREVIEW_TOOLING_PACKAGE.Preview

      @Composable
      @Preview
      fun PreviewNoParameters() {
      }
      """
        .trimIndent()

    assertUpdatingModelUpdatesPsiCorrectly(emptyAnnotation)
  }

  @RunsInEdt
  @Test
  fun `supported parameters displayed correctly`() = runBlocking {
    @Language("kotlin")
    val fileContent =
      """
      import $COMPOSABLE_ANNOTATION_FQN
      import $PREVIEW_TOOLING_PACKAGE.Preview

      @Composable
      @Preview(name = "Test", fontScale = 1.2f, backgroundColor = 4294901760)
      fun PreviewWithParemeters() {
      }
    """
        .trimIndent()

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
      import $COMPOSABLE_ANNOTATION_FQN
      import $PREVIEW_TOOLING_PACKAGE.Preview

      @Composable
      @Preview(name = "Test")
      fun PreviewNoParameters() {
      }
    """
        .trimIndent()

    Sdks.addLatestAndroidSdk(fixture.projectDisposable, module)
    val model = getFirstModel(fileContent)
    assertEquals(
      if (KotlinPluginModeProvider.isK2Mode()) "1.0" else "1",
      model.properties["", "fontScale"].defaultValue,
    )
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
      import $COMPOSABLE_ANNOTATION_FQN
      import $PREVIEW_TOOLING_PACKAGE.Preview

      @Composable
      @Preview
      fun PreviewNoParameters() {
      }
      """
        .trimIndent()

    val model = getFirstModel(fileContent)
    val preview =
      AnnotationFilePreviewElementFinder.findPreviewElements(
          fixture.project,
          fixture.findFileInTempDir("Test.kt"),
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
      import $COMPOSABLE_ANNOTATION_FQN
      import $PREVIEW_TOOLING_PACKAGE.Preview

      @Composable
      @Preview
      fun PreviewNoParameters() {
      }
      """
        .trimIndent()

    val model = getFirstModel(fileContent)
    val preview =
      AnnotationFilePreviewElementFinder.findPreviewElements(
          fixture.project,
          fixture.findFileInTempDir("Test.kt"),
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
      import $COMPOSABLE_ANNOTATION_FQN
      import $PREVIEW_TOOLING_PACKAGE.Preview

      @Composable
      @Preview(fontScale = 1.0f, name = "MyPreview", apiLevel = 1)
      fun PreviewWithParameters() {
      }
      """
        .trimIndent()

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
      testTracker.valuesRegistered[index++],
    )

    // Orientation
    assertEquals(PreviewPickerValue.ORIENTATION_PORTRAIT, testTracker.valuesRegistered[index++])
    assertEquals(PreviewPickerValue.ORIENTATION_LANDSCAPE, testTracker.valuesRegistered[index++])
    assertEquals(
      PreviewPickerValue.UNKNOWN_PREVIEW_PICKER_VALUE,
      testTracker.valuesRegistered[index++],
    )

    // Density
    assertEquals(PreviewPickerValue.DENSITY_XX_HIGH, testTracker.valuesRegistered[index++])
    assertEquals(PreviewPickerValue.DENSITY_XX_HIGH, testTracker.valuesRegistered[index++])
    assertEquals(PreviewPickerValue.DENSITY_X_HIGH, testTracker.valuesRegistered[index++])
    assertEquals(PreviewPickerValue.DENSITY_XXX_HIGH, testTracker.valuesRegistered[index++])
    assertEquals(
      PreviewPickerValue.UNKNOWN_PREVIEW_PICKER_VALUE,
      testTracker.valuesRegistered[index++],
    )

    // DimensionUnit
    assertEquals(PreviewPickerValue.UNIT_DP, testTracker.valuesRegistered[index++])
    assertEquals(PreviewPickerValue.UNIT_PIXELS, testTracker.valuesRegistered[index++])
    assertEquals(
      PreviewPickerValue.UNKNOWN_PREVIEW_PICKER_VALUE,
      testTracker.valuesRegistered[index++],
    )

    // Width/Height
    assertEquals(
      PreviewPickerValue.UNSUPPORTED_OR_OPEN_ENDED,
      testTracker.valuesRegistered[index++],
    )
    assertEquals(PreviewPickerValue.UNSUPPORTED_OR_OPEN_ENDED, testTracker.valuesRegistered[index])
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
    val file = fixture.configureByText("Test.kt", fileContent)
    val noParametersPreview =
      AnnotationFilePreviewElementFinder.findPreviewElements(fixture.project, file.virtualFile)
        .first()
    val model =
      ReadAction.compute<PsiPropertiesModel, Throwable> {
        PreviewPickerPropertiesModel.fromPreviewElement(
          project,
          module,
          noParametersPreview.previewElementDefinition,
          NoOpTracker,
        )
      }
    var expectedModificationsCountdown = 3
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
      import $COMPOSABLE_ANNOTATION_FQN
      import $PREVIEW_TOOLING_PACKAGE.Preview

      @Composable
      @Preview(name = "Test")
      fun PreviewNoParameters() {
      }
    """
        .trimIndent()
    val testTracker = TestTracker()
    val model = runBlocking { getFirstModel(fileContent, testTracker) }
    return Pair(testTracker, model)
  }

  private suspend fun getFirstModel(
    fileContent: String,
    tracker: ComposePickerTracker = NoOpTracker,
  ): PsiPropertiesModel {
    val file = fixture.configureByText("Test.kt", fileContent)
    val preview =
      AnnotationFilePreviewElementFinder.findPreviewElements(fixture.project, file.virtualFile)
        .first()
    ConfigurationManager.getOrCreateInstance(module)
    return ReadAction.compute<PsiPropertiesModel, Throwable> {
      PreviewPickerPropertiesModel.fromPreviewElement(
        project,
        module,
        preview.previewElementDefinition,
        tracker,
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
