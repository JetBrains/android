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
import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.compose.PsiComposePreviewElement
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.NopComposePreviewManager
import com.android.tools.idea.compose.preview.PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.compose.preview.analytics.ComposeResizeToolingUsageTracker
import com.android.tools.idea.compose.preview.resize.ResizePanel
import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.flow.PreviewFlowManager
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.intellij.lang.annotations.Language
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
  private val resizePanel: ResizePanel = Mockito.mock()
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
    ComposeResizeToolingUsageTracker.forceEnableForUnitTests = false
  }

  @Test
  fun `update isVisible and isEnabled when resizePanel is resized`() {
    `when`(resizePanel.hasBeenResized).thenReturn(true)
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
    assertThat(event.presentation.text).isEqualTo("Save New Preview (500dp x 500dp)")
    assertThat(event.presentation.text).isEqualTo("Save New Preview (500dp x 500dp)")
    assertThat(event.presentation.description)
      .isEqualTo("Add the @Preview annotation with the current preview size (500dp x 500dp)")
  }

  private fun getDataContext(): DataContext =
    SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, projectRule.project)
      .add(DESIGN_SURFACE, designSurface)
      .add(PreviewModeManager.KEY, modeManager)
      .add(RESIZE_PANEL_INSTANCE_KEY, resizePanel)
      .build()

  @Test
  fun `update isVisible and isEnabled when preview mode is not focus`() {
    `when`(resizePanel.hasBeenResized).thenReturn(true)
    modeManager.setMode(PreviewMode.AnimationInspection(mock()))

    val event = TestActionEvent.createTestEvent(getDataContext())

    val action = SavePreviewInNewSizeAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isFalse()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun `update isVisible and isEnabled when flag is disabled`() {
    `when`(resizePanel.hasBeenResized).thenReturn(true)
    val event = TestActionEvent.createTestEvent(getDataContext())

    val action = SavePreviewInNewSizeAction()

    StudioFlags.COMPOSE_PREVIEW_RESIZING.override(false)
    action.update(event)
    StudioFlags.COMPOSE_PREVIEW_RESIZING.clearOverride()

    assertThat(event.presentation.isVisible).isFalse()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun `update isVisible and isEnabled when resize panel is not resized`() {
    `when`(resizePanel.hasBeenResized).thenReturn(false)
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

    val smallerSide = 100
    val biggerSide = 200

    configuration.updateScreenSize(smallerSide, biggerSide)
    configuration.deviceState!!.orientation = ScreenOrientation.LANDSCAPE

    `when`(resizePanel.hasBeenResized).thenReturn(true)
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
        "\"spec:width=${biggerSide}dp,height=${smallerSide}dp,dpi=${DEFAULT_DENSITY},orientation=landscape\""
      )
    assertThat(newAnnotation.text)
      .isEqualTo(
        """
          @Preview(
              name = "200dp x 100dp",
              group = "MyGroup",
              showSystemUi = true,
              device = "spec:width=200dp,height=100dp,dpi=160,orientation=landscape"
          )
        """
          .trimIndent()
      )
  }

  @Test
  fun `add new annotation, no custom device`() = runTest {
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

    val newDevice = device(100, 300, ScreenOrientation.LANDSCAPE, "Pixel_9")

    configuration.setDevice(newDevice, true)

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

    assertThat(newAnnotation.text)
      .isEqualTo(
        """
          @Preview(
              name = "Pixel 9",
              group = "MyGroup",
              showSystemUi = true,
              device = "id:Pixel_9"
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

    `when`(resizePanel.hasBeenResized).thenReturn(true)
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
              name = "100dp x 200dp",
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

    `when`(resizePanel.hasBeenResized).thenReturn(true)
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

  @Test
  fun `add new annotation for MultiPreview instance`() = runTest {
    @Language("kotlin")
    val composeTest =
      projectRule.fixture.addFileToProject(
        "src/Test.kt",
        """
          import androidx.compose.runtime.Composable
          import androidx.compose.ui.tooling.preview.Preview

          @Preview(name = "phone", device = "spec:width=360dp,height=640dp,dpi=480")
          @Preview(name = "landscape", device = "spec:width=640dp,height=360dp,dpi=480")
          annotation class DevicePreviews

          @DevicePreviews
          @Composable
          fun MyComposable(text: String) {
          }
          """
          .trimIndent(),
      )

    var previewElements =
      AnnotationFilePreviewElementFinder.findPreviewElements(
        projectRule.project,
        composeTest.virtualFile,
      )

    val phonePreviewElement: PsiComposePreviewElement =
      previewElements.find { it.displaySettings.name == "phone - MyComposable" }
        as PsiComposePreviewElement
    assertThat(phonePreviewElement).isNotNull()

    phonePreviewElement.previewElementDefinition!!.element as KtAnnotationEntry

    modeManager.setMode(PreviewMode.Focus(phonePreviewElement))

    // Simulate a resize
    val configuration =
      createConfiguration(width = 845, height = 360, orientation = ScreenOrientation.LANDSCAPE)
    configuration.updateScreenSize(845, 360) // Ensure internal state is updated

    `when`(resizePanel.hasBeenResized).thenReturn(true)
    `when`(model.dataProvider)
      .thenReturn(
        object : NlDataProvider(PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE) {
          override fun getData(dataId: String) =
            phonePreviewElement.takeIf { dataId == PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.name }
        }
      )
    `when`(model.configuration).thenReturn(configuration)

    val action = SavePreviewInNewSizeAction()
    val event = TestActionEvent.createTestEvent(getDataContext())

    action.actionPerformed(event)

    previewElements =
      AnnotationFilePreviewElementFinder.findPreviewElements(
        projectRule.project,
        composeTest.virtualFile,
      )

    assertThat(previewElements.size).isEqualTo(3)
    val newAnnotation =
      previewElements
        .find { it.configuration.width == 845 && it.configuration.height == 360 }
        ?.previewElementDefinition!!
        .element as KtAnnotationEntry

    assertThat(newAnnotation.text)
      .isEqualTo(
        """
        @Preview(
            name = "845dp x 360dp",
            device = "spec:width=845dp,height=360dp,dpi=160,orientation=landscape",
            widthDp = 845,
            heightDp = 360
        )
        """
          .trimIndent()
      )
    @Language("kotlin")
    val expectedContent =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview

      @Preview(name = "phone", device = "spec:width=360dp,height=640dp,dpi=480")
      @Preview(name = "landscape", device = "spec:width=640dp,height=360dp,dpi=480")
      annotation class DevicePreviews

      @Preview(
          name = "845dp x 360dp",
          device = "spec:width=845dp,height=360dp,dpi=160,orientation=landscape",
          widthDp = 845,
          heightDp = 360
      )
      @DevicePreviews
      @Composable
      fun MyComposable(text: String) {
      }
    """
        .trimIndent()

    assertThat(newAnnotation.containingFile.text).isEqualTo(expectedContent)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `actionPerformed switches focus to new preview after save`() = runTest {
    @Language("kotlin")
    val composeTest =
      projectRule.fixture.addFileToProject(
        "src/Test.kt",
        """
                import androidx.compose.ui.tooling.preview.Preview
                import androidx.compose.runtime.Composable

                @Preview(name = "MyPreview", group = "MyGroup")
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

    val mockFlowManager = mock<PreviewFlowManager<PsiComposePreviewElement>>()
    val previewElementFlow = MutableStateFlow(FlowableCollection.Present(listOf(previewElement)))
    `when`(mockFlowManager.allPreviewElementsFlow).thenReturn(previewElementFlow.asStateFlow())
    modeManager.setMode(PreviewMode.Focus(previewElement))

    val modeBeforeAction = modeManager.mode.value

    `when`(model.dataProvider)
      .thenReturn(
        object : NlDataProvider(PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE) {
          override fun getData(dataId: String) =
            previewElement.takeIf { dataId == PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.name }
        }
      )
    val configuration = createConfiguration(100, 200)
    `when`(model.configuration).thenReturn(configuration)

    val previewManager = NopComposePreviewManager()
    Disposer.register(projectRule.fixture.testRootDisposable, previewManager)
    val testDataContext =
      SimpleDataContext.builder()
        .setParent(getDataContext())
        .add(PreviewFlowManager.KEY, mockFlowManager)
        .add(COMPOSE_PREVIEW_MANAGER, previewManager)
        .build()
    val event = TestActionEvent.createTestEvent(testDataContext)
    val action = SavePreviewInNewSizeAction(StandardTestDispatcher(testScheduler))

    // Act
    // This will call setUpSwitchingToNewPreview, which launches a coroutine that waits
    // for the next flow emission.
    action.actionPerformed(event)

    // Assert (Part 1 - before the new preview appears in the flow)
    // The mode should not have changed yet.
    assertThat(modeManager.mode.value).isEqualTo(modeBeforeAction)

    // Act (Part 2 - simulate the file refresh that finds the new preview)
    val previewElements =
      AnnotationFilePreviewElementFinder.findPreviewElements(
        projectRule.project,
        composeTest.virtualFile,
      )
    assertThat(previewElements.size).isEqualTo(2)
    previewElementFlow.value = FlowableCollection.Present(previewElements)
    advanceUntilIdle()

    // Assert (Part 2 - after new preview appears)
    // Assert the final state of the real modeManager.
    val finalMode = modeManager.mode.value
    assertThat(finalMode).isInstanceOf(PreviewMode.Focus::class.java)
    val focusMode = finalMode as PreviewMode.Focus
    assertThat(focusMode.selected!!.displaySettings.parameterName).isEqualTo("100dp x 200dp")
    Disposer.dispose(previewManager)
  }

  @Test
  fun `add new annotation uses FQN when simple Preview name is ambiguous`() = runTest {
    // 1. Add the conflicting class file
    @Language("kotlin")
    projectRule.fixture.addFileToProject(
      "src/some/other/package/Preview.kt", // Path matching the package
      """
                package some.other.package

                class Preview // This is the conflicting class named 'Preview'
                """
        .trimIndent(),
    )

    @Language("kotlin")
    val composeTest =
      projectRule.fixture.addFileToProject(
        "src/Test.kt",
        """
        package com.example

        import androidx.compose.runtime.Composable
        import some.other.package.Preview // This import creates a name conflict for 'Preview'

        // Due to the conflict, @Preview must be fully qualified to compile
        @androidx.compose.ui.tooling.preview.Preview(name = "ExistingPreview")
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

    val configuration = createConfiguration(300, 400)
    configuration.updateScreenSize(300, 400)

    `when`(resizePanel.hasBeenResized).thenReturn(true)
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

    @Language("kotlin")
    val expectedContent =
      """
      package com.example

      import androidx.compose.runtime.Composable
      import some.other.package.Preview // This import creates a name conflict for 'Preview'

      // Due to the conflict, @Preview must be fully qualified to compile
      @androidx.compose.ui.tooling.preview.Preview(
          name = "300dp x 400dp",
          widthDp = 300,
          heightDp = 400
      )
      @androidx.compose.ui.tooling.preview.Preview(name = "ExistingPreview")
      @Composable
      fun MyComposable() {
      }
                """
        .trimIndent()

    assertThat(composeTest.text).isEqualTo(expectedContent)
  }

  @Test
  fun `add new annotation uses FQN with custom MultiPreview annotation from separate file and conflicting import`() =
    runTest {
      // 1. Add the conflicting class file
      @Language("kotlin")
      val conflictingPreviewFile =
        projectRule.fixture.addFileToProject(
          "src/some/other/package/Preview.kt", // Path matching the package
          """
                package some.other.package

                class Preview // This is the conflicting class named 'Preview'
                """
            .trimIndent(),
        )

      // 2. Add the custom MultiPreview annotation in a separate file
      @Language("kotlin")
      val fontScalePreviewsFile =
        projectRule.fixture.addFileToProject(
          "src/com/example/multipreview/FontScalePreviews.kt",
          """
                package com.example.multipreview

                import androidx.compose.ui.tooling.preview.Preview

                @Preview(name = "small font", group = "font scales", fontScale = 0.5f)
                @Preview(name = "large font", group = "font scales", fontScale = 1.5f)
                annotation class FontScalePreviews
                """
            .trimIndent(),
        )

      // 3. Main Compose Test file
      @Language("kotlin")
      val composeTest =
        projectRule.fixture.addFileToProject(
          "src/Test.kt",
          """
                package com.example

                import androidx.compose.runtime.Composable
                import com.example.multipreview.FontScalePreviews
                import some.other.package.Preview // This import creates a name conflict for 'Preview'

                @FontScalePreviews
                @Composable
                fun MyComposable() {
                }
                """
            .trimIndent(),
        )

      // Select one of the previews from the MultiPreview for resizing (e.g., "MyComposable - small
      // font")
      var previewElements =
        AnnotationFilePreviewElementFinder.findPreviewElements(
          projectRule.project,
          composeTest.virtualFile,
        )

      val selectedPreviewElement =
        previewElements.first { it.displaySettings.name == "small font - MyComposable" }
          as PsiComposePreviewElement
      assertThat(selectedPreviewElement).isNotNull()

      modeManager.setMode(PreviewMode.Focus(selectedPreviewElement))

      // Simulate a resize to a new configuration
      val newWidth = 250
      val newHeight = 400
      val configuration = createConfiguration(newWidth, newHeight)
      configuration.updateScreenSize(newWidth, newHeight)

      `when`(resizePanel.hasBeenResized).thenReturn(true)
      `when`(model.dataProvider)
        .thenReturn(
          object : NlDataProvider(PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE) {
            override fun getData(dataId: String) =
              selectedPreviewElement.takeIf { dataId == PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.name }
          }
        )
      `when`(model.configuration).thenReturn(configuration)

      val action = SavePreviewInNewSizeAction()
      val event = TestActionEvent.createTestEvent(getDataContext())

      action.actionPerformed(event)

      @Language("kotlin")
      val expectedContent =
        """
                package com.example

                import androidx.compose.runtime.Composable
                import com.example.multipreview.FontScalePreviews
                import some.other.package.Preview // This import creates a name conflict for 'Preview'

                @androidx.compose.ui.tooling.preview.Preview(
                    name = "250dp x 400dp",
                    group = "font scales",
                    fontScale = 0.5f,
                    widthDp = 250,
                    heightDp = 400
                )
                @FontScalePreviews
                @Composable
                fun MyComposable() {
                }
                """
          .trimIndent()

      assertThat(composeTest.text).isEqualTo(expectedContent)
    }

  @Test
  fun `add new annotation uses existing alias for Preview`() = runTest {
    @Language("kotlin")
    val composeTest =
      projectRule.fixture.addFileToProject(
        "src/Test.kt",
        """
        package com.example

        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview as MyCustomPreviewAlias

        @MyCustomPreviewAlias(name = "ExistingAliasedPreview")
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

    val configuration = createConfiguration(300, 400)
    configuration.updateScreenSize(300, 400)

    `when`(resizePanel.hasBeenResized).thenReturn(true)
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

    @Language("kotlin")
    val expectedContent =
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview as MyCustomPreviewAlias

      @MyCustomPreviewAlias(
          name = "300dp x 400dp",
          widthDp = 300,
          heightDp = 400
      )
      @MyCustomPreviewAlias(name = "ExistingAliasedPreview")
      @Composable
      fun MyComposable() {
      }
      """
        .trimIndent()

    assertThat(composeTest.text).isEqualTo(expectedContent)
  }

  @Test
  fun `add new annotation uses existing alias despite conflicting simple Preview name`() = runTest {
    // 1. Add the conflicting class file
    @Language("kotlin")
    val conflictingPreviewFile =
      projectRule.fixture.addFileToProject(
        "src/some/other/package/Preview.kt", // Path matching the package
        """
                package some.other.package

                class Preview
                """
          .trimIndent(),
      )

    // 2. Main Compose Test file with existing alias and a conflicting import
    @Language("kotlin")
    val composeTest =
      projectRule.fixture.addFileToProject(
        "src/Test.kt",
        """
                package com.example

                import androidx.compose.runtime.Composable
                import androidx.compose.ui.tooling.preview.Preview as MyCustomPreviewAlias
                import some.other.package.Preview

                @MyCustomPreviewAlias(name = "ExistingAliasedPreview")
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

    val configuration = createConfiguration(300, 400)
    configuration.updateScreenSize(300, 400)

    `when`(resizePanel.hasBeenResized).thenReturn(true)
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

    @Language("kotlin")
    val expectedContent =
      """
        package com.example

        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview as MyCustomPreviewAlias
        import some.other.package.Preview

        @MyCustomPreviewAlias(
            name = "300dp x 400dp",
            widthDp = 300,
            heightDp = 400
        )
        @MyCustomPreviewAlias(name = "ExistingAliasedPreview")
        @Composable
        fun MyComposable() {
        }
                """
        .trimIndent()

    assertThat(composeTest.text).isEqualTo(expectedContent)
  }

  @Test
  fun `add new annotation with backgroundColor defined as hex literal`() = runTest {
    @Language("kotlin")
    val composeTest =
      projectRule.fixture.addFileToProject(
        "src/Test.kt",
        """
            import androidx.compose.ui.tooling.preview.Preview
            import androidx.compose.runtime.Composable

            @Preview("Article screen", showBackground = true, backgroundColor = 0xFF000000)
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

    val configuration = createConfiguration(300, 400)

    `when`(resizePanel.hasBeenResized).thenReturn(true)
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

    assertThat(newAnnotation.text)
      .isEqualTo(
        """
            @Preview(
                name = "300dp x 400dp",
                showBackground = true,
                backgroundColor = 0xFF000000,
                widthDp = 300,
                heightDp = 400
            )
            """
          .trimIndent()
      )
  }

  @Test
  fun `add new imports for UI modes and Wallpapers`() = runTest {
    // 1. Add the custom MultiPreview annotation in a separate file
    projectRule.fixture.addFileToProject(
      "src/com/example/multipreview/PreviewLightDark.kt",
      """
            package com.example.multipreview

            import android.content.res.Configuration.UI_MODE_NIGHT_YES
            import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
            import androidx.compose.ui.tooling.preview.Preview
            import androidx.compose.ui.tooling.preview.Wallpapers

            @Preview(name = "Light")
            @Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL, wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE)
            annotation class PreviewLightDark
      """
        .trimIndent(),
    )

    // 2. Add the main Compose Test file
    val composeTest =
      projectRule.fixture.addFileToProject(
        "src/com/example/MyPreviews.kt",
        """
              package com.example

              import androidx.compose.runtime.Composable
              import com.example.multipreview.PreviewLightDark

              @Composable fun XRAppTheme(content: @Composable () -> Unit) { content() }
              @Composable fun My2DContent(onRequestFullSpaceMode: () -> Unit) {}

              @PreviewLightDark
              @Composable
              fun My2dContentPreview() {
                  XRAppTheme {
                      My2DContent(onRequestFullSpaceMode = {})
                  }
              }
        """
          .trimIndent(),
      )

    // 3. Find the "Dark" preview element, which has uiMode set
    val previewElements =
      AnnotationFilePreviewElementFinder.findPreviewElements(
        projectRule.project,
        composeTest.virtualFile,
      )

    val darkPreviewElement =
      previewElements.first { it.displaySettings.name == "Dark - My2dContentPreview" }
        as PsiComposePreviewElement

    modeManager.setMode(PreviewMode.Focus(darkPreviewElement))

    // 4. Simulate a resize
    val newWidth = 400
    val newHeight = 250
    val configuration = createConfiguration(newWidth, newHeight)
    configuration.updateScreenSize(newWidth, newHeight)

    `when`(resizePanel.hasBeenResized).thenReturn(true)
    `when`(model.dataProvider)
      .thenReturn(
        object : NlDataProvider(PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE) {
          override fun getData(dataId: String) =
            darkPreviewElement.takeIf { dataId == PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.name }
        }
      )
    `when`(model.configuration).thenReturn(configuration)

    // 5. Perform action
    val action = SavePreviewInNewSizeAction()
    val event = TestActionEvent.createTestEvent(getDataContext())
    action.actionPerformed(event)

    // 6. Assertions
    @Language("kotlin")
    val expectedContent =
      """
      package com.example

      import android.content.res.Configuration.UI_MODE_NIGHT_YES
      import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview
      import androidx.compose.ui.tooling.preview.Wallpapers.RED_DOMINATED_EXAMPLE
      import com.example.multipreview.PreviewLightDark

      @Composable fun XRAppTheme(content: @Composable () -> Unit) { content() }
      @Composable fun My2DContent(onRequestFullSpaceMode: () -> Unit) {}

      @Preview(
          name = "400dp x 250dp",
          uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
          wallpaper = RED_DOMINATED_EXAMPLE,
          widthDp = 400,
          heightDp = 250
      )
      @PreviewLightDark
      @Composable
      fun My2dContentPreview() {
          XRAppTheme {
              My2DContent(onRequestFullSpaceMode = {})
          }
      }
      """
        .trimIndent()

    val updatedText = composeTest.text
    assertThat(updatedText).isEqualTo(expectedContent)
  }

  @Test
  fun `add new imports for UI modes and Wallpapers with container class imported`() = runTest {
    // 1. Add the custom MultiPreview annotation in a separate file
    projectRule.fixture.addFileToProject(
      "src/com/example/multipreview/PreviewLightDark.kt",
      """
            package com.example.multipreview

            import android.content.res.Configuration.UI_MODE_NIGHT_YES
            import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
            import androidx.compose.ui.tooling.preview.Preview
            import androidx.compose.ui.tooling.preview.Wallpapers

            @Preview(name = "Light")
            @Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL, wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE)
            annotation class PreviewLightDark
      """
        .trimIndent(),
    )

    // 2. Add the main Compose Test file
    val composeTest =
      projectRule.fixture.addFileToProject(
        "src/com/example/MyPreviews.kt",
        """
              package com.example

              import android.content.res.Configuration
              import androidx.compose.runtime.Composable
              import androidx.compose.ui.tooling.preview.Wallpapers
              import com.example.multipreview.PreviewLightDark

              @Composable fun XRAppTheme(content: @Composable () -> Unit) { content() }
              @Composable fun My2DContent(onRequestFullSpaceMode: () -> Unit) {}

              @PreviewLightDark
              @Composable
              fun My2dContentPreview() {
                  XRAppTheme {
                      My2DContent(onRequestFullSpaceMode = {})
                  }
              }
        """
          .trimIndent(),
      )

    // 3. Find the "Dark" preview element, which has uiMode set
    val previewElements =
      AnnotationFilePreviewElementFinder.findPreviewElements(
        projectRule.project,
        composeTest.virtualFile,
      )

    val darkPreviewElement =
      previewElements.first { it.displaySettings.name == "Dark - My2dContentPreview" }
        as PsiComposePreviewElement

    modeManager.setMode(PreviewMode.Focus(darkPreviewElement))

    // 4. Simulate a resize
    val newWidth = 400
    val newHeight = 250
    val configuration = createConfiguration(newWidth, newHeight)
    configuration.updateScreenSize(newWidth, newHeight)

    `when`(resizePanel.hasBeenResized).thenReturn(true)
    `when`(model.dataProvider)
      .thenReturn(
        object : NlDataProvider(PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE) {
          override fun getData(dataId: String) =
            darkPreviewElement.takeIf { dataId == PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.name }
        }
      )
    `when`(model.configuration).thenReturn(configuration)

    // 5. Perform action
    val action = SavePreviewInNewSizeAction()
    val event = TestActionEvent.createTestEvent(getDataContext())
    action.actionPerformed(event)

    // 6. Assertions
    @Language("kotlin")
    val expectedContent =
      """
      package com.example

      import android.content.res.Configuration
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview
      import androidx.compose.ui.tooling.preview.Wallpapers
      import com.example.multipreview.PreviewLightDark

      @Composable fun XRAppTheme(content: @Composable () -> Unit) { content() }
      @Composable fun My2DContent(onRequestFullSpaceMode: () -> Unit) {}

      @Preview(
          name = "400dp x 250dp",
          uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
          wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE,
          widthDp = 400,
          heightDp = 250
      )
      @PreviewLightDark
      @Composable
      fun My2dContentPreview() {
          XRAppTheme {
              My2DContent(onRequestFullSpaceMode = {})
          }
      }
      """
        .trimIndent()

    val updatedText = composeTest.text
    assertThat(updatedText).isEqualTo(expectedContent)
  }

  @Test
  fun `do not add duplicate ui mode imports`() = runTest {
    // 1. Add the main Compose Test file, which already includes a UI_MODE import
    val composeTest =
      projectRule.fixture.addFileToProject(
        "src/com/example/MyPreviews.kt",
        """
              package com.example

              import android.content.res.Configuration.UI_MODE_NIGHT_YES
              import androidx.compose.runtime.Composable
              import androidx.compose.ui.tooling.preview.Preview

              @Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES)
              @Composable
              fun MyComposable() {
              }
        """
          .trimIndent(),
      )

    // 2. Find the "Dark" preview element
    val previewElement =
      AnnotationFilePreviewElementFinder.findPreviewElements(
          projectRule.project,
          composeTest.virtualFile,
        )
        .first() as PsiComposePreviewElement

    modeManager.setMode(PreviewMode.Focus(previewElement))

    // 3. Simulate a resize
    val newWidth = 400
    val newHeight = 250
    val configuration = createConfiguration(newWidth, newHeight)
    configuration.updateScreenSize(newWidth, newHeight)
    // Ensure the uiMode is carried over to the new annotation
    `when`(model.configuration).thenReturn(configuration)
    `when`(resizePanel.hasBeenResized).thenReturn(true)
    `when`(model.dataProvider)
      .thenReturn(
        object : NlDataProvider(PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE) {
          override fun getData(dataId: String) =
            previewElement.takeIf { dataId == PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.name }
        }
      )

    // 4. Perform action
    val action = SavePreviewInNewSizeAction()
    val event = TestActionEvent.createTestEvent(getDataContext())
    action.actionPerformed(event)

    // 5. Assertions
    @Language("kotlin")
    val expectedContent =
      """
      package com.example

      import android.content.res.Configuration.UI_MODE_NIGHT_YES
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview

      @Preview(
          name = "400dp x 250dp",
          uiMode = UI_MODE_NIGHT_YES,
          widthDp = 400,
          heightDp = 250
      )
      @Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES)
      @Composable
      fun MyComposable() {
      }
      """
        .trimIndent()

    val updatedText = composeTest.text
    assertThat(updatedText).isEqualTo(expectedContent)

    // Check that there is only one import for UI_MODE_NIGHT_YES
    val importCount =
      updatedText.lines().count {
        it.contains("import android.content.res.Configuration.UI_MODE_NIGHT_YES")
      }
    assertThat(importCount).isEqualTo(1)
  }

  fun KtAnnotationEntry.getValueForArgument(name: String): String? {
    val valueArgument =
      valueArgumentList!!.arguments.find { it.getArgumentName()?.asName?.identifier == name }
    val matchResult = Regex(".*?=\\s*(.*)").find(valueArgument!!.text)
    return matchResult?.groups?.get(1)?.value
  }

  private fun createConfiguration(
    width: Int,
    height: Int,
    orientation: ScreenOrientation = ScreenOrientation.PORTRAIT,
  ): Configuration {
    val manager = ConfigurationManager.getOrCreateInstance(projectRule.fixture.module)
    val configuration = Configuration.create(manager, FolderConfiguration())
    configuration.setDevice(device(width, height, orientation), true)

    return configuration
  }

  private fun device(
    width: Int,
    height: Int,
    orientation: ScreenOrientation,
    id: String = Configuration.CUSTOM_DEVICE_ID,
  ): Device =
    Device.Builder()
      .apply {
        setTagId("")
        setName(id.replace("_", " "))
        setId(id)
        setManufacturer("")
        addSoftware(Software())
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
                    pixelDensity = Density.MEDIUM
                  }
              }
          }
        )
      }
      .build()
}
