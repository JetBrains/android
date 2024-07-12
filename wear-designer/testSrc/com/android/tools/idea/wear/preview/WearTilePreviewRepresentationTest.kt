/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wear.preview

import com.android.testutils.delayUntilCondition
import com.android.testutils.retryUntilPassing
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceListener
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.editors.build.RenderingBuildStatus
import com.android.tools.idea.preview.actions.GroupSwitchAction
import com.android.tools.idea.preview.flow.PreviewFlowManager
import com.android.tools.idea.preview.groups.PreviewGroupManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.representation.PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.rendering.tokens.FakeBuildSystemFilePreviewServices
import com.android.tools.idea.uibuilder.options.NlOptionsConfigurable
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.util.TestToolWindowManager
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.android.tools.preview.PreviewElement
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WearTilePreviewRepresentationTest {
  private val logger = Logger.getInstance(WearTilePreviewRepresentation::class.java)

  @get:Rule val projectRule = WearTileProjectRule()

  private val project
    get() = projectRule.project

  private val fixture
    get() = projectRule.fixture

  @Before
  fun setup() {
    logger.setLevel(LogLevel.ALL)
    Logger.getInstance(WearTilePreviewRepresentation::class.java).setLevel(LogLevel.ALL)
    Logger.getInstance(RenderingBuildStatus::class.java).setLevel(LogLevel.ALL)
    logger.info("setup")
    runInEdtAndWait { TestProjectSystem(project).useInTests() }
    FakeBuildSystemFilePreviewServices().register(fixture.testRootDisposable)
    logger.info("setup complete")

    project.replaceService(
      ToolWindowManager::class.java,
      TestToolWindowManager(project),
      fixture.testRootDisposable,
    )
  }

  @After
  fun tearDown() {
    wearTilePreviewEssentialsModeEnabled = false
  }

  @Test
  fun testPreviewInitialization() =
    runBlocking(workerThread) {
      val preview = createWearTilePreviewRepresentation()

      preview.previewView.mainSurface.models.forEach {
        assertTrue(preview.navigationHandler.defaultNavigationMap.contains(it))
      }

      val status = preview.previewViewModel
      assertFalse(status.isOutOfDate)
      val renderResults =
        preview.previewView.mainSurface.sceneManagers.mapNotNull { it.renderResult }
      // Ensure the only warning message is the missing Android SDK message
      assertTrue(
        renderResults
          .flatMap { it.logger.messages }
          .none { !it.html.contains("No Android SDK found.") }
      )
      preview.onDeactivate()
    }

  @Test
  fun testGroupFilteringIsSupported() =
    runBlocking(workerThread) {
      val preview = createWearTilePreviewRepresentation()
      val previewGroupManager =
        preview.previewView.mainSurface.getData(PreviewGroupManager.KEY.name) as PreviewGroupManager

      assertThat(previewGroupManager.availableGroupsFlow.value.map { it.displayName })
        .containsExactly("groupA")
      assertThat(preview.previewView.mainSurface.models).hasSize(4)

      val dataContext = DataContext { preview.previewView.mainSurface.getData(it) }

      // Select preview group "groupA"
      run {
        val groupSwitchAction = GroupSwitchAction()
        val actionEvent = TestActionEvent.createTestEvent(dataContext)

        groupSwitchAction.actionPerformed(actionEvent)
        groupSwitchAction.update(actionEvent)
        assertTrue(actionEvent.presentation.isEnabled)
        assertTrue(actionEvent.presentation.isVisible)

        val selectGroupAAction =
          groupSwitchAction.childActionsOrStubs.single { it.templateText == "groupA" }
        selectGroupAAction.actionPerformed(TestActionEvent.createTestEvent(dataContext))
      }

      // Ensure that the preview group was selected
      run {
        delayUntilCondition(250) { preview.previewView.mainSurface.models.size == 1 }

        val previewElements =
          preview.previewView.mainSurface.models.mapNotNull {
            it.dataContext.getData(PREVIEW_ELEMENT_INSTANCE) as? WearTilePreviewElement
          }
        assertThat(previewElements).hasSize(1)
        assertThat(previewElements.map { it.methodFqn })
          .containsExactly("com.android.test.TestKt.tilePreview2")
      }

      preview.onDeactivate()
    }

  @Test
  fun testGalleryMode() =
    runBlocking(workerThread) {
      val preview = createWearTilePreviewRepresentation()

      assertThat(preview.previewView.mainSurface.models).hasSize(4)
      assertThat(preview.previewView.galleryMode).isNull()

      // go into gallery mode
      run {
        val previewElement =
          preview.previewFlowManager.filteredPreviewElementsFlow.value.asCollection().elementAt(1)
        preview.previewModeManager.setMode(PreviewMode.Gallery(previewElement))

        expectGalleryModeIsSet(preview, previewElement)
      }

      preview.onDeactivate()
    }

  @Test
  fun testGalleryModeIsEnabledWhenEnablingWearTilePreviewEssentialsMode() =
    runBlocking(workerThread) {
      val preview = createWearTilePreviewRepresentation()

      assertThat(preview.previewView.mainSurface.models).hasSize(4)
      assertThat(preview.previewView.galleryMode).isNull()

      // enable tile preview essentials mode
      run {
        wearTilePreviewEssentialsModeEnabled = true

        val previewElement =
          preview.previewFlowManager.filteredPreviewElementsFlow.value.asCollection().first()

        expectGalleryModeIsSet(preview, previewElement)
      }

      preview.onDeactivate()
    }

  @Test
  fun testInteractivePreviewManagerFpsLimitIsInitializedWhenEssentialsModeIsDisabled() =
    runBlocking(workerThread) {
      val preview = createWearTilePreviewRepresentation()

      assertEquals(30, preview.interactiveManager.fpsLimit)

      preview.onDeactivate()
    }

  @Test
  fun testInteractivePreviewManagerFpsLimitIsInitializedWhenEssentialsModeIsEnabled() =
    runBlocking(workerThread) {
      wearTilePreviewEssentialsModeEnabled = true
      val preview = createWearTilePreviewRepresentation(expectedModelCount = 1)

      assertEquals(10, preview.interactiveManager.fpsLimit)

      preview.onDeactivate()
    }

  @Test
  fun testInteractivePreviewManagerFpsLimitIsUpdatedWhenEssentialsModeChanges() =
    runBlocking(workerThread) {
      val preview = createWearTilePreviewRepresentation()

      assertEquals(30, preview.interactiveManager.fpsLimit)

      wearTilePreviewEssentialsModeEnabled = true
      retryUntilPassing(5.seconds) { assertEquals(10, preview.interactiveManager.fpsLimit) }

      wearTilePreviewEssentialsModeEnabled = false
      retryUntilPassing(5.seconds) { assertEquals(30, preview.interactiveManager.fpsLimit) }

      preview.onDeactivate()
    }

  @Test
  fun clickingOnThePreviewNavigatesToDefinition() {
    runBlocking(workerThread) {
      val preview = createWearTilePreviewRepresentation()

      assertEquals(0, runReadAction { projectRule.fixture.caretOffset })

      // Preview from simple @Preview annotation
      run {
        val sceneViewWithNormalPreviewAnnotation =
          preview.previewView.mainSurface.sceneManagers
            .first {
              it.model.dataContext.getData(PREVIEW_ELEMENT_INSTANCE)?.displaySettings?.name ==
                "tilePreview3 - preview3"
            }
            .sceneViews
            .first()
        withContext(uiThread) {
          preview.navigationHandler.handleNavigateWithCoordinates(
            sceneViewWithNormalPreviewAnnotation,
            sceneViewWithNormalPreviewAnnotation.x,
            sceneViewWithNormalPreviewAnnotation.y,
            false,
          )
        }

        runReadAction {
          val expectedOffset =
            fixture
              .findElementByText("@Preview(name = \"preview3\")", KtAnnotationEntry::class.java)
              .textOffset
          assertEquals(expectedOffset, projectRule.fixture.caretOffset)
        }
      }

      // Preview from @MyMultiPreview annotation
      run {
        val sceneViewWithMultiPreviewAnnotation =
          preview.previewView.mainSurface.sceneManagers
            .first {
              it.model.dataContext.getData(PREVIEW_ELEMENT_INSTANCE)?.displaySettings?.name ==
                "tilePreview3 - multipreview preview"
            }
            .sceneViews
            .first()
        withContext(uiThread) {
          preview.navigationHandler.handleNavigateWithCoordinates(
            sceneViewWithMultiPreviewAnnotation,
            sceneViewWithMultiPreviewAnnotation.x,
            sceneViewWithMultiPreviewAnnotation.y,
            false,
          )
        }

        runReadAction {
          // We expect to navigate the user to where they use @MyMultiPreview and not the @Preview
          // declared within the multi preview
          val expectedOffset =
            fixture.findElementByText("@MyMultiPreview\n", KtAnnotationEntry::class.java).textOffset
          assertEquals(expectedOffset, projectRule.fixture.caretOffset)
        }
      }

      preview.onDeactivate()
    }
  }

  private suspend fun expectGalleryModeIsSet(
    preview: WearTilePreviewRepresentation,
    previewElement: PreviewElement<*>,
  ) {
    delayUntilCondition(250) {
      preview.previewView.mainSurface.models.size == 1 && preview.previewView.galleryMode != null
    }

    val previewElements =
      preview.previewView.mainSurface.models.mapNotNull {
        it.dataContext.getData(PREVIEW_ELEMENT_INSTANCE) as? PsiWearTilePreviewElement
      }
    assertThat(previewElements).containsExactly(previewElement)
    assertThat(preview.previewView.galleryMode).isNotNull()
  }

  private val WearTilePreviewRepresentation.previewModeManager
    get() = previewView.mainSurface.getData(PreviewModeManager.KEY.name) as PreviewModeManager

  private val WearTilePreviewRepresentation.previewFlowManager
    get() = previewView.mainSurface.getData(PreviewFlowManager.KEY.name) as PreviewFlowManager<*>

  private var wearTilePreviewEssentialsModeEnabled: Boolean = false
    set(value) {
      runWriteActionAndWait {
        AndroidEditorSettings.getInstance().globalState.isPreviewEssentialsModeEnabled = value
        ApplicationManager.getApplication()
          .messageBus
          .syncPublisher(NlOptionsConfigurable.Listener.TOPIC)
          .onOptionsChanged()
      }
      field = value
    }

  private suspend fun createWearTilePreviewRepresentation(
    expectedModelCount: Int = 2
  ): WearTilePreviewRepresentation {
    val wearTileTestFile = createWearTilePreviewTestFile()
    val modelRenderedLatch = CountDownLatch(expectedModelCount)
    val previewRepresentation =
      WearTilePreviewRepresentationProvider().createRepresentation(wearTileTestFile)
        as WearTilePreviewRepresentation

    previewRepresentation.previewView.mainSurface.addListener(
      object : DesignSurfaceListener {
        override fun modelChanged(surface: DesignSurface<*>, model: NlModel?) {
          val id = UUID.randomUUID().toString().substring(0, 5)
          logger.info("modelChanged ($id)")
          (surface.getSceneManager(model!!) as? LayoutlibSceneManager)?.addRenderListener {
            logger.info("renderListener ($id)")
            modelRenderedLatch.countDown()
          }
        }
      }
    )

    Disposer.register(fixture.testRootDisposable, previewRepresentation)
    project.runWhenSmartAndSyncedOnEdt(
      callback = {
        runBlocking(Dispatchers.IO) {
          logger.info("compile")
          ProjectSystemService.getInstance(project).projectSystem.getBuildManager().compileProject()
          logger.info("activate")
          previewRepresentation.onActivate()
        }
      }
    )

    withContext(Dispatchers.IO) {
      modelRenderedLatch.await()
      delayWhileRefreshingOrDumb(previewRepresentation)
    }

    return previewRepresentation
  }

  private fun createWearTilePreviewTestFile() = runWriteActionAndWait {
    fixture.configureByText(
      "Test.kt", // language=kotlin
      """
          package com.android.test

        import android.content.Context
        import androidx.wear.tiles.TileService
        import androidx.wear.tiles.tooling.preview.Preview
        import androidx.wear.tiles.tooling.preview.TilePreviewData
        import androidx.wear.tiles.tooling.preview.WearDevices

        @Preview(name = "multipreview preview")
        annotation class MyMultiPreview

        @Preview
        private fun tilePreview(): TilePreviewData {
          return TilePreviewData()
        }

        @Preview(name = "preview2", group = "groupA")
        private fun tilePreview2(): TilePreviewData {
          return TilePreviewData()
        }

        @MyMultiPreview
        @Preview(name = "preview3")
        private fun tilePreview3(): TilePreviewData {
          return TilePreviewData()
        }
        """
        .trimIndent(),
    )
  }

  private suspend fun delayWhileRefreshingOrDumb(preview: WearTilePreviewRepresentation) {
    delayUntilCondition(250) {
      !(preview.previewViewModel.isRefreshing || DumbService.getInstance(project).isDumb)
    }
  }
}
