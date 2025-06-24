/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.compose.preview.util

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.State
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.updateScreenSize
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.intellij.lang.annotations.Language
import org.jetbrains.android.compose.ComposeProjectRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PreviewAnnotationRoundTripTest {

  @get:Rule val projectRule = ComposeProjectRule(AndroidProjectRule.withAndroidModel())

  private fun createConfiguration(
    width: Int,
    height: Int,
    density: Density = Density.MEDIUM,
    orientation: ScreenOrientation = ScreenOrientation.PORTRAIT,
  ): Configuration {
    val manager = ConfigurationManager.getOrCreateInstance(projectRule.fixture.module)
    val configuration = Configuration.create(manager, FolderConfiguration())
    val device = device(width, height, density, orientation)

    val state = device.defaultState.deepCopy()
    configuration.setEffectiveDevice(device, state)

    configuration.updateScreenSize(width, height)
    return configuration
  }

  // Helper function to create a Device (copied from PreviewAnnotationGeneratorTest)
  private fun device(
    width: Int,
    height: Int,
    density: Density,
    orientation: ScreenOrientation,
  ): Device =
    Device.Builder()
      .apply {
        setTagId("")
        setName("Custom")
        setId(Configuration.CUSTOM_DEVICE_ID)
        setManufacturer("")
        addSoftware(com.android.sdklib.devices.Software())
        addState(
          State().apply {
            name = "default"
            isDefaultState = true
            this.orientation = orientation
            hardware =
              Hardware().apply {
                screen =
                  Screen().apply {
                    yDimension = height
                    xDimension = width
                    pixelDensity = density
                  }
              }
          }
        )
      }
      .build()

  @Test
  fun `toPreviewAnnotationText round-trips a simple Preview annotation`() = runTest {
    @Language("kotlin")
    val composeFileContent =
      """
      import androidx.compose.ui.tooling.preview.Preview
      import androidx.compose.runtime.Composable

      @androidx.compose.ui.tooling.preview.Preview(name = "MySimplePreview", widthDp = 200, heightDp = 300)
      @Composable
      fun MyComposable() {
      }
    """
        .trimIndent()

    val composeTestFile = projectRule.fixture.addFileToProject("src/Test.kt", composeFileContent)

    val previewElements =
      AnnotationFilePreviewElementFinder.findPreviewElements(
          projectRule.project,
          composeTestFile.virtualFile,
        )
        .flatMap { it.resolve() }
    assertThat(previewElements).hasSize(1)

    val previewElement = previewElements.first()

    // Create a configuration that matches the dimensions of the preview element
    val configuration = createConfiguration(width = 200, height = 300)

    val generatedText = toPreviewAnnotationText(previewElement, configuration, "MySimplePreview")

    // The generated text should match the original, possibly with reordered parameters
    // and default values omitted. For this simple case, it should be identical.
    assertThat(generatedText)
      .isEqualTo(
        """
        @androidx.compose.ui.tooling.preview.Preview(
            name = "MySimplePreview",
            widthDp = 200,
            heightDp = 300
        )
      """
          .trimIndent()
      )
  }

  @Test
  fun `toPreviewAnnotationText round-trips a Preview with device spec`() = runTest {
    @Language("kotlin")
    val composeFileContent =
      """
      import androidx.compose.ui.tooling.preview.Preview
      import androidx.compose.runtime.Composable

      @androidx.compose.ui.tooling.preview.Preview(name = "MyDevicePreview", device = "spec:width=100dp,height=100dp,dpi=240,orientation=portrait")
      @Composable
      fun MyComposable() {
      }
    """
        .trimIndent()

    val composeTestFile = projectRule.fixture.addFileToProject("src/Test.kt", composeFileContent)

    val previewElements =
      AnnotationFilePreviewElementFinder.findPreviewElements(
          projectRule.project,
          composeTestFile.virtualFile,
        )
        .flatMap { it.resolve() }
    assertThat(previewElements).hasSize(1)

    val previewElement = previewElements.first()

    // Create a configuration that matches the new dimensions and density
    // 400dp * (240dpi / 160dpi) = 600px
    // 800dp * (240dpi / 160dpi) = 1200px
    val configuration =
      createConfiguration(
        width = 600,
        height = 1200,
        density = Density.HIGH, // 240 dpi
        orientation = ScreenOrientation.PORTRAIT,
      )

    val generatedText = toPreviewAnnotationText(previewElement, configuration, "MyDevicePreview")

    assertThat(generatedText)
      .isEqualTo(
        """
        @androidx.compose.ui.tooling.preview.Preview(
            name = "MyDevicePreview",
            device = "spec:width=400dp,height=800dp,dpi=240",
            widthDp = 400,
            heightDp = 800
        )
      """
          .trimIndent()
      )
  }

  @Test
  fun `toPreviewAnnotationText round-trips a MultiPreview instance`() = runTest {
    @Language("kotlin")
    val composeFileContent =
      """
      import androidx.compose.ui.tooling.preview.Preview
      import androidx.compose.runtime.Composable

      @androidx.compose.ui.tooling.preview.Preview(name = "small font", group = "font scales", fontScale = 0.5f)
      @androidx.compose.ui.tooling.preview.Preview(name = "large font", group = "font scales", fontScale = 1.5f)
      annotation class FontScalePreviews

      @FontScalePreviews
      @Composable
      fun MyComposable() {
      }
    """
        .trimIndent()

    val composeTestFile = projectRule.fixture.addFileToProject("src/Test.kt", composeFileContent)

    val previewElements =
      AnnotationFilePreviewElementFinder.findPreviewElements(
          projectRule.project,
          composeTestFile.virtualFile,
        )
        .flatMap { it.resolve() }
    // Find the specific preview element for "small font"
    val smallFontPreviewElement =
      previewElements.find { it.displaySettings.name == "MyComposable - small font" }!!

    assertThat(smallFontPreviewElement).isNotNull()

    // Create a configuration for the round-trip (dimensions don't matter as much here, but must be
    // valid)
    val configuration = createConfiguration(width = 100, height = 200)

    val generatedText =
      toPreviewAnnotationText(smallFontPreviewElement, configuration, "small font")

    // The generated text should represent the "small font" preview with default size parameters
    assertThat(generatedText)
      .isEqualTo(
        """
        @androidx.compose.ui.tooling.preview.Preview(
            name = "small font",
            group = "font scales",
            fontScale = 0.5f,
            widthDp = 100,
            heightDp = 200
        )
      """
          .trimIndent()
      )
  }

  @Test
  fun `toPreviewAnnotationText round-trips with all optional parameters`() = runTest {
    @Language("kotlin")
    val composeFileContent =
      """
      import androidx.compose.ui.tooling.preview.Preview
      import androidx.compose.runtime.Composable
      import android.content.res.Configuration.UI_MODE_NIGHT_NO

      @androidx.compose.ui.tooling.preview.Preview(
          name = "FullParams",
          group = "MyGroup",
          showBackground = true,
          backgroundColor = 0xFFCCCCCC,
          apiLevel = 28,
          locale = "en-rUS",
          fontScale = 1.2f,
          uiMode = UI_MODE_NIGHT_NO
      )
      @Composable
      fun MyComposable() {
      }
    """
        .trimIndent()

    val composeTestFile = projectRule.fixture.addFileToProject("src/Test.kt", composeFileContent)

    val previewElements =
      AnnotationFilePreviewElementFinder.findPreviewElements(
          projectRule.project,
          composeTestFile.virtualFile,
        )
        .flatMap { it.resolve() }
    assertThat(previewElements).hasSize(1)

    val previewElement = previewElements.first()

    // Create a configuration for the round-trip (dimensions don't matter as much here, but must be
    // valid)
    val configuration = createConfiguration(width = 500, height = 500)

    val generatedText = toPreviewAnnotationText(previewElement, configuration, "FullParams")

    assertThat(generatedText)
      .isEqualTo(
        """
        @androidx.compose.ui.tooling.preview.Preview(
            name = "FullParams",
            group = "MyGroup",
            showBackground = true,
            backgroundColor = 0x#ffcccccc,
            apiLevel = 28,
            locale = "en-rUS",
            fontScale = 1.2f,
            uiMode = UI_MODE_NIGHT_NO,
            widthDp = 500,
            heightDp = 500
        )
      """
          .trimIndent()
      )
  }

  @Test
  fun `toPreviewAnnotationText preserves device spec and adds widthDp heightDp when showDecoration is false`() =
    runTest {
      @Language("kotlin")
      val composeFileContent =
        """
      import androidx.compose.ui.tooling.preview.Preview
      import androidx.compose.runtime.Composable

      @androidx.compose.ui.tooling.preview.Preview(name = "DeviceWithCustomSize", device = "spec:width=400dp,height=800dp,dpi=240,cutout=double")
      @Composable
      fun MyComposable() {
      }
    """
          .trimIndent()

      val composeTestFile = projectRule.fixture.addFileToProject("src/Test.kt", composeFileContent)

      val previewElements =
        AnnotationFilePreviewElementFinder.findPreviewElements(
            projectRule.project,
            composeTestFile.virtualFile,
          )
          .flatMap { it.resolve() }
      assertThat(previewElements).hasSize(1)

      val previewElement = previewElements.first()

      // Simulate a resize where showDecoration is false (composable resizing)
      // The configuration should reflect the *new* desired composable size in pixels.
      // 600px x 1200px at 160dpi (MDPI) -> 600dp x 1200dp
      val newWidthPx = 600
      val newHeightPx = 1200
      val configuration =
        createConfiguration(
          width = newWidthPx,
          height = newHeightPx,
          density = Density.MEDIUM, // 160 dpi
          orientation = ScreenOrientation.PORTRAIT,
        )

      val generatedText =
        toPreviewAnnotationText(previewElement, configuration, "DeviceWithCustomSize")

      assertThat(generatedText)
        .isEqualTo(
          """
        @androidx.compose.ui.tooling.preview.Preview(
            name = "DeviceWithCustomSize",
            device = "spec:width=600dp,height=1200dp,dpi=160",
            widthDp = 600,
            heightDp = 1200
        )
      """
            .trimIndent()
        )
    }

  @Test
  fun `toPreviewAnnotationText with combined UiMode generates constant names`() = runTest {
    @Language("kotlin")
    val composeFileContent =
      """
      import androidx.compose.ui.tooling.preview.Preview
      import androidx.compose.runtime.Composable
      import android.content.res.Configuration.UI_MODE_NIGHT_YES
      import android.content.res.Configuration.UI_MODE_TYPE_DESK

      @Preview(name = "CombinedUiMode", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_DESK)
      @Composable
      fun MyComposable() {}
      """
        .trimIndent()

    val composeTestFile = projectRule.fixture.addFileToProject("src/Test.kt", composeFileContent)

    val previewElement =
      AnnotationFilePreviewElementFinder.findPreviewElements(
          projectRule.project,
          composeTestFile.virtualFile,
        )
        .flatMap { it.resolve() }
        .first()

    val configuration = createConfiguration(width = 200, height = 200)

    val generatedText = toPreviewAnnotationText(previewElement, configuration, "CombinedUiMode")

    assertThat(generatedText)
      .isEqualTo(
        """
        @androidx.compose.ui.tooling.preview.Preview(
            name = "CombinedUiMode",
            uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_DESK,
            widthDp = 200,
            heightDp = 200
        )
        """
          .trimIndent()
      )
  }
}
