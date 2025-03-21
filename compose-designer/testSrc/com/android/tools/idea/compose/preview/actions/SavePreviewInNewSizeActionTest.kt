/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.actions

import com.android.flags.junit.FlagRule
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.Density
import com.android.resources.Density.DEFAULT_DENSITY
import com.android.resources.ScreenOrientation
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.updateScreenSize
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.model.NlDataProvider
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder
import com.android.tools.idea.compose.preview.PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.compose.preview.analytics.ComposeResizeToolingUsageTracker
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.modes.CommonPreviewModeManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.ResizeComposePreviewEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import kotlinx.coroutines.test.runTest
import org.intellij.lang.annotations.Language
import org.jetbrains.android.compose.ComposeProjectRule
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock

@RunWith(JUnit4::class)
@RunsInEdt
class SavePreviewInNewSizeActionTest {

  @get:Rule val projectRule = ComposeProjectRule(AndroidProjectRule.withAndroidModel())

  @get:Rule val edtRule = EdtRule()

  @get:Rule val studioFlagRule = FlagRule(StudioFlags.COMPOSE_PREVIEW_RESIZING, true)
  @get:Rule val usageTrackerRule = UsageTrackerRule()

  private val sceneManager: LayoutlibSceneManager = Mockito.mock()
  private val designSurface: NlDesignSurface = Mockito.mock()
  private val model: NlModel = Mockito.mock()
  private lateinit var modeManager: PreviewModeManager

  @Before
  fun setup() {
    `when`(designSurface.sceneManagers).thenReturn(listOf(sceneManager))
    `when`(sceneManager.model).thenReturn(model)
    modeManager = CommonPreviewModeManager()
  }

  @After
  fun tearDown() {
    Disposer.dispose(sceneManager)
  }

  @Test
  fun `update isVisible and isEnabled when scene manager is resized`() {
    `when`(sceneManager.isResized).thenReturn(true)
    val model = mock<NlModel>()
    `when`(sceneManager.model).thenReturn(model)
    val configuration = createConfiguration(500, 500)
    `when`(model.configuration).thenReturn(configuration)
    modeManager.setMode(PreviewMode.Focus(mock()))
    val event = TestActionEvent.createTestEvent(getDataContext())

    val action = SavePreviewInNewSizeAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()
    assertThat(event.presentation.text).isEqualTo("Save Size (500dp x 500dp)")
    assertThat(event.presentation.description)
      .isEqualTo("Add the @Preview annotation with the current preview size (500dp x 500dp)")
  }

  private fun getDataContext(): DataContext =
    SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, projectRule.project)
      .add(DESIGN_SURFACE, designSurface)
      .add(PreviewModeManager.KEY, modeManager)
      .build()

  @Test
  fun `update isVisible and isEnabled when preview mode is not focus`() {
    `when`(sceneManager.isResized).thenReturn(true)
    modeManager.setMode(PreviewMode.AnimationInspection(mock()))

    val event = TestActionEvent.createTestEvent(getDataContext())

    val action = SavePreviewInNewSizeAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isFalse()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun `update isVisible and isEnabled when flag is disabled`() {
    `when`(sceneManager.isResized).thenReturn(true)
    val event = TestActionEvent.createTestEvent(getDataContext())

    val action = SavePreviewInNewSizeAction()

    StudioFlags.COMPOSE_PREVIEW_RESIZING.override(false)
    action.update(event)
    StudioFlags.COMPOSE_PREVIEW_RESIZING.clearOverride()

    assertThat(event.presentation.isVisible).isFalse()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun `update isVisible and isEnabled when scene manager is not resized`() {
    `when`(sceneManager.isResized).thenReturn(false)
    modeManager.setMode(PreviewMode.Focus(mock()))
    val event = TestActionEvent.createTestEvent(getDataContext())

    val action = SavePreviewInNewSizeAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isFalse()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun `add new annotation, showSystemUi = true`() = runTest {
    @Language("kotlin")
    val composeTest =
      projectRule.fixture.addFileToProject(
        "src/Test.kt",
        """
                import androidx.compose.ui.tooling.preview.Preview
                import androidx.compose.runtime.Composable

                @Preview(name = "MyPreview", group = "MyGroup", showSystemUi = true)
                @Composable
                fun MyComposable() {
                }
                """
          .trimIndent(),
      )

    val previewElement =
      AnnotationFilePreviewElementFinder.findPreviewElements(
          projectRule.project,
          composeTest.virtualFile,
        )
        .first()
    modeManager.setMode(PreviewMode.Focus(previewElement))
    val originalAnnotation = previewElement.previewElementDefinition!!.element as KtAnnotationEntry

    val configuration = createConfiguration(500, 600)

    val newWidth = 100
    val newHeight = 200

    configuration.updateScreenSize(newWidth, newHeight)
    configuration.deviceState!!.orientation = ScreenOrientation.LANDSCAPE

    `when`(sceneManager.isResized).thenReturn(true)
    `when`(model.dataProvider)
      .thenReturn(
        object : NlDataProvider(PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE) {
          override fun getData(dataId: String) =
            previewElement.takeIf { dataId == PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.name }
        }
      )
    `when`(model.configuration).thenReturn(configuration)

    val action = SavePreviewInNewSizeAction()
    val event = TestActionEvent.createTestEvent(getDataContext())

    action.actionPerformed(event)

    val previewElements =
      AnnotationFilePreviewElementFinder.findPreviewElements(
        projectRule.project,
        composeTest.virtualFile,
      )

    assertThat(previewElements.size).isEqualTo(2)
    val newAnnotation =
      previewElements
        .map { (it.previewElementDefinition!!.element!! as KtAnnotationEntry) }
        .find { it.text != originalAnnotation.text }!!

    // We expect a device spec
    val deviceSpec = newAnnotation.getValueForArgument("device")
    assertThat(deviceSpec)
      .isEqualTo(
        "\"spec:width=${newWidth}dp,height=${newHeight}dp,dpi=${DEFAULT_DENSITY},orientation=landscape\""
      )
    assertThat(newAnnotation.text)
      .isEqualTo(
        """
          @Preview(
              name = "MyPreview",
              group = "MyGroup",
              showSystemUi = true,
              device = "spec:width=100dp,height=200dp,dpi=160,orientation=landscape"
          )
        """
          .trimIndent()
      )
  }

  @Test
  fun `add new annotation, showSystemUi = false`() = runTest {
    @Language("kotlin")
    val composeTest =
      projectRule.fixture.addFileToProject(
        "src/Test.kt",
        """
            import androidx.compose.ui.tooling.preview.Preview
            import androidx.compose.runtime.Composable

            @Preview(name = "MyPreview", group = "MyGroup", showBackground = true)
            @Composable
            fun MyComposable() {
            }
            """
          .trimIndent(),
      )

    val previewElement =
      AnnotationFilePreviewElementFinder.findPreviewElements(
          projectRule.project,
          composeTest.virtualFile,
        )
        .first()
    modeManager.setMode(PreviewMode.Focus(previewElement))
    val originalAnnotation = previewElement.previewElementDefinition!!.element as KtAnnotationEntry

    val configuration = createConfiguration(500, 600)

    val newWidth = 100
    val newHeight = 200

    configuration.updateScreenSize(newWidth, newHeight)

    `when`(sceneManager.isResized).thenReturn(true)
    `when`(model.dataProvider)
      .thenReturn(
        object : NlDataProvider(PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE) {
          override fun getData(dataId: String) =
            previewElement.takeIf { dataId == PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.name }
        }
      )
    `when`(model.configuration).thenReturn(configuration)

    val action = SavePreviewInNewSizeAction()
    val event = TestActionEvent.createTestEvent(getDataContext())

    action.actionPerformed(event)

    val previewElements =
      AnnotationFilePreviewElementFinder.findPreviewElements(
        projectRule.project,
        composeTest.virtualFile,
      )

    assertThat(previewElements.size).isEqualTo(2)
    val newAnnotation =
      previewElements
        .map { (it.previewElementDefinition!!.element!! as KtAnnotationEntry) }
        .find { it.text != originalAnnotation.text }!!

    assertThat(newAnnotation.getValueForArgument("widthDp")).isEqualTo("$newWidth")
    assertThat(newAnnotation.getValueForArgument("heightDp")).isEqualTo("$newHeight")
    assertThat(newAnnotation.text)
      .isEqualTo(
        """
          @Preview(
              name = "MyPreview",
              group = "MyGroup",
              showBackground = true,
              widthDp = 100,
              heightDp = 200
          )
        """
          .trimIndent()
      )
  }

  @Test
  fun `send statistics on save`() = runTest {
    ComposeResizeToolingUsageTracker.forceEnableForUnitTests = true

    @Language("kotlin")
    val composeTest =
      projectRule.fixture.addFileToProject(
        "src/Test.kt",
        """
            import androidx.compose.ui.tooling.preview.Preview
            import androidx.compose.runtime.Composable

            @Preview(name = "MyPreview", group = "MyGroup", showSystemUi = true)
            @Composable
            fun MyComposable() {
            }
            """
          .trimIndent(),
      )

    val previewElement =
      AnnotationFilePreviewElementFinder.findPreviewElements(
          projectRule.project,
          composeTest.virtualFile,
        )
        .first()
    modeManager.setMode(PreviewMode.Focus(previewElement))

    val configuration = createConfiguration(500, 600)

    val newWidth = 100
    val newHeight = 200

    configuration.updateScreenSize(newWidth, newHeight)

    `when`(sceneManager.isResized).thenReturn(true)
    `when`(model.dataProvider)
      .thenReturn(
        object : NlDataProvider(PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE) {
          override fun getData(dataId: String) =
            previewElement.takeIf { dataId == PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.name }
        }
      )
    `when`(model.configuration).thenReturn(configuration)

    val action = SavePreviewInNewSizeAction()
    val event = TestActionEvent.createTestEvent(getDataContext())

    action.actionPerformed(event)

    val eventAnalytics =
      usageTrackerRule.usages
        .find {
          it.studioEvent.resizeComposePreviewEvent.eventType ==
            ResizeComposePreviewEvent.EventType.RESIZE_SAVED
        }!!
        .studioEvent
        .resizeComposePreviewEvent
    assertThat(eventAnalytics.deviceWidthDp).isEqualTo(newWidth)
    assertThat(eventAnalytics.deviceHeightDp).isEqualTo(newHeight)
    assertThat(eventAnalytics.dpi)
      .isEqualTo(configuration.deviceState!!.hardware.screen.pixelDensity.dpiValue)
    assertThat(eventAnalytics.resizeMode)
      .isEqualTo(ResizeComposePreviewEvent.ResizeMode.DEVICE_RESIZE)
  }

  fun KtAnnotationEntry.getValueForArgument(name: String): String? {
    val valueArgument =
      valueArgumentList!!.arguments.find { it.getArgumentName()?.asName?.identifier == name }
    val matchResult = Regex(".*?=\\s*(.*)").find(valueArgument!!.text)
    return matchResult?.groups?.get(1)?.value
  }

  private fun createConfiguration(width: Int, height: Int): Configuration {
    val manager = ConfigurationManager.getOrCreateInstance(projectRule.fixture.module)
    val configuration = Configuration.create(manager, FolderConfiguration())
    configuration.setDevice(device(width, height), true)

    return configuration
  }

  private fun device(width: Int, height: Int): Device =
    Device.Builder()
      .apply {
        setTagId("")
        setName("Custom")
        setId(Configuration.CUSTOM_DEVICE_ID)
        setManufacturer("")
        addSoftware(Software())
        addState(
          State().apply {
            name = "default"
            isDefaultState = true
            orientation = ScreenOrientation.PORTRAIT
            hardware =
              Hardware().apply {
                screen =
                  Screen().apply {
                    yDimension = height
                    xDimension = width
                    pixelDensity = Density.MEDIUM
                  }
              }
          }
        )
      }
      .build()
}
