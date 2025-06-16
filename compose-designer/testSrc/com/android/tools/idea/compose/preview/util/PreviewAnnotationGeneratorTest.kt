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
package com.android.tools.idea.compose.preview.util // Test file package

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.State
import com.android.tools.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.preview.ComposePreviewElementInstance
import com.android.tools.preview.DisplayPositioning
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.android.tools.preview.UNDEFINED_API_LEVEL
import com.android.tools.preview.UNDEFINED_DIMENSION
import com.android.tools.preview.UNSET_UI_MODE_VALUE
import com.android.tools.preview.config.DEFAULT_DEVICE_ID
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import kotlinx.coroutines.test.runTest
import org.jetbrains.android.compose.ComposeProjectRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PreviewAnnotationGeneratorTest {

  @get:Rule val projectRule = ComposeProjectRule(AndroidProjectRule.withAndroidModel())

  private val UI_MODE_TYPE_CAR = 0x00000003
  private val UI_MODE_NIGHT_YES = 0x00000020

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

    return configuration
  }

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

  private fun createPreviewElement(
    name: String = "MyPreview",
    group: String? = null,
    showDecoration: Boolean = false,
    showBackground: Boolean = false,
    backgroundColor: String? = null,
    apiLevel: Int = UNDEFINED_API_LEVEL,
    locale: String? = null,
    fontScale: Float = 1.0f,
    uiMode: Int = UNSET_UI_MODE_VALUE,
    deviceSpec: String? = null,
    widthDp: Int = UNDEFINED_DIMENSION,
    heightDp: Int = UNDEFINED_DIMENSION,
    composableMethodFqn: String = "com.example.MyComposable",
    baseName: String = "MyComposable",
    parameterName: String? = name.ifBlank { null },
  ): ComposePreviewElementInstance<*> {
    val previewConfig =
      PreviewConfiguration.cleanAndGet(
        apiLevel = apiLevel,
        width = widthDp,
        height = heightDp,
        locale = locale,
        fontScale = fontScale,
        uiMode = uiMode,
        device = deviceSpec,
      )
    return SingleComposePreviewElementInstance.forTesting<Any>(
      composableMethodFqn = composableMethodFqn,
      baseName = baseName,
      parameterName = parameterName,
      groupName = group,
      showDecorations = showDecoration,
      showBackground = showBackground,
      backgroundColor = backgroundColor,
      displayPositioning = DisplayPositioning.NORMAL,
      configuration = previewConfig,
    )
  }

  @Test
  fun `toPreviewAnnotationText generates basic Preview with name and group`() = runTest {
    val previewElement = createPreviewElement(name = "BasicPreview", group = "MyGroup")
    val configuration = createConfiguration(width = 1080, height = 1920)

    val generatedText = toPreviewAnnotationText(previewElement, configuration, "BasicPreview")
    assertThat(generatedText)
      .isEqualTo(
        """
        @androidx.compose.ui.tooling.preview.Preview(
            name = "BasicPreview",
            group = "MyGroup",
            widthDp = 1080,
            heightDp = 1920
        )
      """
          .trimIndent()
      )
  }

  @Test
  fun `toPreviewAnnotationText generates with widthDp and heightDp`() = runTest {
    val previewElement =
      createPreviewElement(name = "CustomSizePreview", widthDp = 200, heightDp = 300)
    val configuration = createConfiguration(width = 200, height = 300)

    val generatedText = toPreviewAnnotationText(previewElement, configuration, "CustomSizePreview")
    assertThat(generatedText)
      .isEqualTo(
        """
        @androidx.compose.ui.tooling.preview.Preview(
            name = "CustomSizePreview",
            widthDp = 200,
            heightDp = 300
        )
      """
          .trimIndent()
      )
  }

  @Test
  fun `toPreviewAnnotationText generates with device spec when showDecoration is true`() = runTest {
    val previewElement = createPreviewElement(name = "DevicePreview", showDecoration = true)
    val configuration =
      createConfiguration(
        width = 1080,
        height = 1920,
        density = Density.XHIGH,
        orientation = ScreenOrientation.LANDSCAPE,
      )

    val generatedText = toPreviewAnnotationText(previewElement, configuration, "DevicePreview")
    // Expected DPI for XHIGH is 320.
    // Original pixel dimensions: 1080x1920 (portrait aspect)
    // When orientation is LANDSCAPE, deviceSize() will return (1920, 1080) pixels.
    // DP conversion:
    // widthDp = 1920px * (160dpi / 320dpi) = 960dp
    // heightDp = 1080px * (160dpi / 320dpi) = 540dp
    assertThat(generatedText)
      .isEqualTo(
        """
        @androidx.compose.ui.tooling.preview.Preview(
            name = "DevicePreview",
            showSystemUi = true,
            device = "spec:width=960dp,height=540dp,dpi=320,orientation=landscape"
        )
      """
          .trimIndent()
      )
  }

  @Test
  fun `toPreviewAnnotationText includes optional parameters when not default`() = runTest {
    val CAR_NIGHT_MODE_UI_MODE = UI_MODE_TYPE_CAR or UI_MODE_NIGHT_YES // 3 | 32 = 35

    val previewElement =
      createPreviewElement(
        name = "FullPreview",
        group = "AllFeatures",
        showBackground = true,
        backgroundColor = "0xFF000000",
        apiLevel = 30,
        locale = "fr",
        fontScale = 1.5f,
        uiMode = CAR_NIGHT_MODE_UI_MODE,
        showDecoration = false,
      )
    val configuration = createConfiguration(width = 400, height = 600)

    val generatedText = toPreviewAnnotationText(previewElement, configuration, "FullPreview")
    assertThat(generatedText)
      .isEqualTo(
        """
        @androidx.compose.ui.tooling.preview.Preview(
            name = "FullPreview",
            group = "AllFeatures",
            showBackground = true,
            backgroundColor = 0xFF000000,
            apiLevel = 30,
            locale = "fr",
            fontScale = 1.5f,
            uiMode = 35,
            widthDp = 400,
            heightDp = 600
        )
      """
          .trimIndent()
      )
  }

  @Test
  fun `toPreviewAnnotationText includes showSystemUi when showDecoration is true`() = runTest {
    val previewElement = createPreviewElement(name = "ShowSystemUiPreview", showDecoration = true)
    val configuration = createConfiguration(width = 400, height = 600)

    val generatedText =
      toPreviewAnnotationText(previewElement, configuration, "ShowSystemUiPreview")

    assertThat(generatedText)
      .isEqualTo(
        """
      @androidx.compose.ui.tooling.preview.Preview(
          name = "ShowSystemUiPreview",
          showSystemUi = true,
          device = "spec:width=400dp,height=600dp,dpi=160,orientation=portrait"
      )
      """
          .trimIndent()
      )
  }

  @Test
  fun `toPreviewAnnotationText omits default parameters`() = runTest {
    val previewElement =
      createPreviewElement(
        name = "DefaultParams",
        group = null,
        showBackground = false,
        backgroundColor = null,
        apiLevel = UNDEFINED_API_LEVEL,
        locale = "",
        fontScale = 1.0f,
        uiMode = UNSET_UI_MODE_VALUE,
        showDecoration = false,
      )
    val configuration = createConfiguration(width = 100, height = 100)

    val generatedText = toPreviewAnnotationText(previewElement, configuration, "DefaultParams")
    assertThat(generatedText)
      .isEqualTo(
        """
        @androidx.compose.ui.tooling.preview.Preview(
            name = "DefaultParams",
            widthDp = 100,
            heightDp = 100
        )
      """
          .trimIndent()
      )
  }

  @Test
  fun `toPreviewAnnotationText handles landscape orientation for widthDp heightDp`() = runTest {
    val previewElement = createPreviewElement(name = "LandscapeComposable", showDecoration = false)
    val configuration =
      createConfiguration(width = 1000, height = 500, orientation = ScreenOrientation.LANDSCAPE)

    val generatedText =
      toPreviewAnnotationText(previewElement, configuration, "LandscapeComposable")
    // For composable resize, if width < height but orientation is landscape,
    // they should be swapped to represent the actual dimensions.
    assertThat(generatedText)
      .isEqualTo(
        """
        @androidx.compose.ui.tooling.preview.Preview(
            name = "LandscapeComposable",
            widthDp = 1000,
            heightDp = 500
        )
      """
          .trimIndent()
      )
  }

  @Test
  fun `toPreviewAnnotationText handles portrait orientation for widthDp heightDp`() = runTest {
    val previewElement = createPreviewElement(name = "PortraitComposable", showDecoration = false)
    val configuration =
      createConfiguration(width = 500, height = 1000, orientation = ScreenOrientation.PORTRAIT)

    val generatedText = toPreviewAnnotationText(previewElement, configuration, "PortraitComposable")
    assertThat(generatedText)
      .isEqualTo(
        """
        @androidx.compose.ui.tooling.preview.Preview(
            name = "PortraitComposable",
            widthDp = 500,
            heightDp = 1000
        )
      """
          .trimIndent()
      )
  }

  @Test
  fun `toPreviewAnnotationText handles device spec with portrait orientation`() = runTest {
    val previewElement = createPreviewElement(name = "DevicePortrait", showDecoration = true)
    val configuration =
      createConfiguration(
        width = 500,
        height = 1000,
        density = Density.XXHIGH,
        orientation = ScreenOrientation.PORTRAIT,
      )

    val generatedText = toPreviewAnnotationText(previewElement, configuration, "DevicePortrait")
    // 500px at 480dpi -> 500 * (160/480) = 166.66 -> 167dp (int truncation)
    // 1000px at 480dpi -> 1000 * (160/480) = 333.33 -> 333dp (int truncation)
    assertThat(generatedText)
      .isEqualTo(
        """
        @androidx.compose.ui.tooling.preview.Preview(
            name = "DevicePortrait",
            showSystemUi = true,
            device = "spec:width=167dp,height=333dp,dpi=480,orientation=portrait"
        )
      """
          .trimIndent()
      )
  }

  @Test
  fun `toPreviewAnnotationText uses passed name for name attribute`() = runTest {
    val originalName = "MyOriginalName"
    val previewElement =
      createPreviewElement(
        name = originalName,
        baseName = "MyComposable",
        composableMethodFqn = "com.example.MyComposable",
      )
    val configuration = createConfiguration(width = 100, height = 100)

    val generatedText = toPreviewAnnotationText(previewElement, configuration, "NewName")
    assertThat(generatedText)
      .isEqualTo(
        """
        @androidx.compose.ui.tooling.preview.Preview(
            name = "NewName",
            widthDp = 100,
            heightDp = 100
        )
      """
          .trimIndent()
      )
  }

  @Test
  fun `toPreviewAnnotationText strips parameter provider suffix from name`() = runTest {
    val originalName = "phone"
    val parameterProviderSuffix = " - userNewsResources 0"
    val displayNameWithSuffix = "$originalName$parameterProviderSuffix"

    val previewElement =
      createPreviewElement(
        name = originalName,
        baseName = "ForYouScreenPopulatedFeed",
        composableMethodFqn = "com.example.ForYouScreenPopulatedFeed",
        parameterName = displayNameWithSuffix,
        showDecoration = false,
      )
    val configuration =
      createConfiguration(width = 845, height = 360, orientation = ScreenOrientation.LANDSCAPE)

    val generatedText = toPreviewAnnotationText(previewElement, configuration, "NewName")

    assertThat(generatedText)
      .isEqualTo(
        """
        @androidx.compose.ui.tooling.preview.Preview(
            name = "NewName",
            widthDp = 845,
            heightDp = 360
        )
      """
          .trimIndent()
      )
  }

  private fun createConfigurationForDevice(deviceId: String): Configuration {
    val manager = ConfigurationManager.getOrCreateInstance(projectRule.fixture.module)
    val device =
      manager.devices.find { it.id == deviceId }
        ?: error("Device '$deviceId' not found in available devices")
    val configuration = Configuration.create(manager, FolderConfiguration())
    configuration.setEffectiveDevice(device, device.defaultState)
    return configuration
  }

  @Test
  fun `toPreviewAnnotationText with known non-default device generates deviceId`() = runTest {
    val knownNonDefaultDevice = "pixel_3"

    assume().that(knownNonDefaultDevice).isNotEqualTo(DEFAULT_DEVICE_ID)

    val configuration = createConfigurationForDevice(knownNonDefaultDevice)
    val previewElement = createPreviewElement(name = "KnownDevicePreview")

    val generatedText = toPreviewAnnotationText(previewElement, configuration, "KnownDevicePreview")

    assertThat(generatedText)
      .isEqualTo(
        """
      @androidx.compose.ui.tooling.preview.Preview(
          name = "KnownDevicePreview",
          device = "id:pixel_3"
      )
      """
          .trimIndent()
      )
  }

  @Test
  fun `toPreviewAnnotationText with default device omits device parameter`() = runTest {
    val configuration = createConfigurationForDevice(DEFAULT_DEVICE_ID)
    val previewElement = createPreviewElement(name = "DefaultDevicePreview")

    val generatedText =
      toPreviewAnnotationText(previewElement, configuration, "DefaultDevicePreview")

    // For the default device, no device specifier is needed.
    assertThat(generatedText)
      .isEqualTo(
        """
      @androidx.compose.ui.tooling.preview.Preview(
          name = "DefaultDevicePreview"
      )
      """
          .trimIndent()
      )
  }
}
