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
package com.android.tools.idea.preview.representation

import com.android.testutils.delayUntilCondition
import com.android.testutils.retryUntilPassing
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.compile.fast.CompilationResult
import com.android.tools.compile.fast.isSuccess
import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.concurrency.awaitStatus
import com.android.tools.idea.editors.build.RenderingBuildStatus
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.fast.FastPreviewTrackerManager
import com.android.tools.idea.editors.fast.TestFastPreviewTrackerManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.PreviewElementModelAdapter
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.PreviewInvalidationManager
import com.android.tools.idea.preview.PreviewRefreshManager
import com.android.tools.idea.preview.PsiTestPreviewElement
import com.android.tools.idea.preview.TestPreviewRefreshRequest
import com.android.tools.idea.preview.analytics.PreviewRefreshEventBuilder
import com.android.tools.idea.preview.analytics.PreviewRefreshTracker
import com.android.tools.idea.preview.analytics.PreviewRefreshTrackerForTest
import com.android.tools.idea.preview.animation.AnimationManager
import com.android.tools.idea.preview.animation.AnimationPreview
import com.android.tools.idea.preview.fast.FastPreviewSurface
import com.android.tools.idea.preview.flow.PreviewFlowManager
import com.android.tools.idea.preview.groups.PreviewGroup
import com.android.tools.idea.preview.groups.PreviewGroupManager
import com.android.tools.idea.preview.modes.GALLERY_LAYOUT_OPTION
import com.android.tools.idea.preview.modes.LIST_LAYOUT_OPTION
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.mvvm.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.preview.mvvm.PreviewViewModelStatus
import com.android.tools.idea.preview.requestRefreshSync
import com.android.tools.idea.preview.viewmodels.CommonPreviewViewModel
import com.android.tools.idea.preview.views.CommonNlDesignSurfacePreviewView
import com.android.tools.idea.preview.waitUntilRefreshStarts
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.rendering.tokens.FakeBuildSystemFilePreviewServices
import com.android.tools.idea.run.deployment.liveedit.setUpComposeInProjectFixture
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.insertText
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.moveCaretToEnd
import com.android.tools.idea.ui.ApplicationUtils
import com.android.tools.preview.DisplayPositioning
import com.android.tools.preview.PreviewElement
import com.android.tools.rendering.RenderAsyncActionExecutor
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.PreviewRefreshEvent
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.DumbModeTestUtils.waitForSmartMode
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import java.util.concurrent.CountDownLatch
import javax.swing.JPanel
import kotlin.test.assertFails
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private lateinit var previewView: CommonNlDesignSurfacePreviewView
private lateinit var previewViewModelMock: CommonPreviewViewModel

private class TestPreviewElementProvider(
  private val previewElements: Sequence<PsiTestPreviewElement>
) : PreviewElementProvider<PsiTestPreviewElement> {
  override suspend fun previewElements(): Sequence<PsiTestPreviewElement> = previewElements
}

private val TEST_PREVIEW_ELEMENT_KEY =
  DataKey.create<PsiTestPreviewElement>("PsiTestPreviewElement")

private class TestPreviewElementModelAdapter :
  PreviewElementModelAdapter<PsiTestPreviewElement, NlModel> {
  override fun calcAffinity(el1: PsiTestPreviewElement, el2: PsiTestPreviewElement?): Int = 0

  override fun toXml(previewElement: PsiTestPreviewElement): String = ""

  override fun applyToConfiguration(
    previewElement: PsiTestPreviewElement,
    configuration: Configuration,
  ) {}

  override fun modelToElement(model: NlModel): PsiTestPreviewElement? =
    if (!model.isDisposed) {
      model.dataContext.getData(TEST_PREVIEW_ELEMENT_KEY)
    } else null

  override fun createDataContext(previewElement: PsiTestPreviewElement): DataContext =
    SimpleDataContext.builder().add(TEST_PREVIEW_ELEMENT_KEY, previewElement).build()

  override fun toLogString(previewElement: PsiTestPreviewElement): String = ""

  override fun createLightVirtualFile(
    content: String,
    backedFile: VirtualFile,
    id: Long,
  ): LightVirtualFile = InMemoryLayoutVirtualFile("test.xml", content, backedFile)
}

class CommonPreviewRepresentationTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory().withKotlin()
  private lateinit var myScope: CoroutineScope
  private lateinit var refreshManager: PreviewRefreshManager
  private lateinit var psiFile: PsiFile
  private lateinit var smartPointerManager: SmartPointerManager
  private val buildSystemServices = FakeBuildSystemFilePreviewServices()
  private val modelAdapter = TestPreviewElementModelAdapter()

  private val fixture
    get() = projectRule.fixture

  private val project
    get() = fixture.project

  @Before
  fun setup() {
    setUpComposeInProjectFixture(projectRule)
    runInEdtAndWait { TestProjectSystem(project).useInTests() }
    buildSystemServices.register(fixture.testRootDisposable)
    previewViewModelMock = mock(CommonPreviewViewModel::class.java)
    myScope = AndroidCoroutineScope(fixture.testRootDisposable)
    smartPointerManager = SmartPointerManager.getInstance(project)
    // use the "real" refresh manager and not a "for test" instance to actually test how the common
    // representation uses it
    refreshManager =
      PreviewRefreshManager.getInstance(RenderAsyncActionExecutor.RenderingTopic.NOT_SPECIFIED)

    psiFile =
      fixture.configureByText(
        "Test.kt",
        // language=kotlin
        """
      annotation class Preview

      @Preview
      fun MyFun() {
        println("Hello world!")
      }
    """
          .trimIndent(),
      )
  }

  @After
  fun tearDown() {
    StudioFlags.PREVIEW_RENDER_QUALITY.clearOverride()
  }

  @Test
  fun testFullRefreshIsTriggeredOnSuccessfulBuild() =
    runBlocking(workerThread) {
      // Turn off flag to make sure quality refreshes won't affect the asserts in this test
      StudioFlags.PREVIEW_RENDER_QUALITY.override(false)
      val previewRepresentation = createPreviewRepresentation()
      previewRepresentation.compileAndWaitForRefresh()

      // block the refresh manager with a high priority refresh that won't finish
      val blockingRefresh = blockRefreshManager()

      // building the project again should invalidate the preview representation
      assertFalse(previewRepresentation.isInvalidatedForTest())
      buildSystemServices.simulateArtifactBuild(ProjectSystemBuildManager.BuildStatus.SUCCESS)
      delayUntilCondition(delayPerIterationMs = 1000, 20.seconds) {
        previewRepresentation.isInvalidatedForTest()
      }
      assertTrue(previewRepresentation.isInvalidatedForTest())

      // unblock the refresh manager
      TestPreviewRefreshRequest.expectedLogPrintCount.await()
      TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(1)
      blockingRefresh.waitUntilRefreshStarts()
      blockingRefresh.runningRefreshJob!!.cancel()
      TestPreviewRefreshRequest.expectedLogPrintCount.await()
      assertEquals(
        """
      start testRequest
      user-cancel testRequest
    """
          .trimIndent(),
        TestPreviewRefreshRequest.log.toString().trimIndent(),
      )

      // As a consequence of the build a refresh should happen in the preview representation now
      // that the refresh manager was unblocked
      delayUntilCondition(delayPerIterationMs = 1000, 10.seconds) {
        !previewRepresentation.isInvalidatedForTest()
      }
      assertFalse(previewRepresentation.isInvalidatedForTest())
      previewRepresentation.onDeactivateImmediately()
    }

  @Test
  fun testFastPreviewIsRequested() = runBlocking {
    val requestCompleted = CompletableDeferred<Unit>()
    val testTracker =
      TestFastPreviewTrackerManager(showTimes = false) { requestCompleted.complete(Unit) }
    val fastPreviewManager = FastPreviewManager.getInstance(project)
    assertTrue("FastPreviewManager must be enabled", fastPreviewManager.isEnabled)

    project.replaceService(
      FastPreviewTrackerManager::class.java,
      testTracker,
      fixture.testRootDisposable,
    )

    val previewRepresentation = createPreviewRepresentation()
    previewRepresentation.compileAndWaitForRefresh()

    val compileDeferred = CompletableDeferred<CompilationResult>()
    val fastPreviewManagerListener =
      object : FastPreviewManager.Companion.FastPreviewManagerListener {
        override fun onCompilationStarted(files: Collection<PsiFile>) {}

        override fun onCompilationComplete(result: CompilationResult, files: Collection<PsiFile>) {
          // We expect a successful compilation, but some cancelled results can be received here
          // if for some reason a compilation is started while another one was already happening
          if (result.isSuccess) compileDeferred.complete(result)
        }
      }
    fastPreviewManager.addListener(fixture.testRootDisposable, fastPreviewManagerListener)
    runWriteActionAndWait {
      projectRule.fixture.openFileInEditor(psiFile.virtualFile)
      projectRule.fixture.moveCaret("println(\"Hello world!\")|")
      projectRule.fixture.editor.executeAndSave {
        insertText("\nprintln(\"added during test execution\")")
      }
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    val result = compileDeferred.await()
    assertTrue(result.isSuccess)

    withTimeout(10.seconds) {
      // Wait for the tracking request to be submitted
      requestCompleted.await()
    }

    assertEquals("compilationSucceeded (compiledFiles=1)", testTracker.logOutput())
    previewRepresentation.onDeactivateImmediately()
  }

  @Test
  fun testDataKeysShouldBeRegistered() {
    runBlocking(workerThread) {
      val preview = createPreviewRepresentation()
      val surface = preview.previewView.mainSurface
      val context =
        DataManager.getInstance().customizeDataContext(DataContext.EMPTY_CONTEXT, surface)

      assertTrue(PreviewModeManager.KEY.getData(context) is PreviewModeManager)
      assertTrue(PREVIEW_VIEW_MODEL_STATUS.getData(context) is PreviewViewModelStatus)
      assertTrue(PreviewGroupManager.KEY.getData(context) is PreviewGroupManager)
      assertTrue(PreviewFlowManager.KEY.getData(context) is PreviewFlowManager<*>)
      assertTrue(FastPreviewSurface.KEY.getData(context) is FastPreviewSurface)
      assertTrue(PreviewInvalidationManager.KEY.getData(context) is PreviewInvalidationManager)

      preview.onDeactivateImmediately()
    }
  }

  @Test
  fun testReactivationWithoutChangesDontFullRefresh(): Unit =
    runBlocking(workerThread) {
      // Turn off flag to make sure quality refreshes won't affect the asserts in this test
      StudioFlags.PREVIEW_RENDER_QUALITY.override(false)
      val previewRepresentation = createPreviewRepresentation()
      previewRepresentation.compileAndWaitForRefresh()

      assertFalse(previewRepresentation.isInvalidatedForTest())
      previewRepresentation.onDeactivateImmediately()

      val blockingRefresh = blockRefreshManager()

      // reactivating the representation shouldn't enqueue a new refresh
      previewRepresentation.onActivate()
      assertFalse(previewRepresentation.isInvalidatedForTest())
      assertFails {
        delayUntilCondition(delayPerIterationMs = 1000, 5.seconds) {
          refreshManager.getTotalRequestsInQueueForTest() == 1
        }
      }
      assertFalse(previewRepresentation.isInvalidatedForTest())
      blockingRefresh.runningRefreshJob!!.cancel()
    }

  @Test
  fun testReactivationWithoutChangesDoesQualityRefresh(): Unit =
    runBlocking(workerThread) {
      val previewRepresentation = createPreviewRepresentation()
      previewRepresentation.compileAndWaitForRefresh()

      assertFalse(previewRepresentation.isInvalidatedForTest())
      var blockingRefresh = blockRefreshManager()
      previewRepresentation.onDeactivateImmediately()
      // Quality refresh on deactivation to decrease qualities
      delayUntilCondition(delayPerIterationMs = 1000, 5.seconds) {
        refreshManager.getTotalRequestsInQueueForTest() == 1
      }
      assertFalse(previewRepresentation.isInvalidatedForTest())
      // unblock and wait for the quality refresh to be taken out of the queue
      blockingRefresh.runningRefreshJob!!.cancel()
      delayUntilCondition(delayPerIterationMs = 1000, 5.seconds) {
        refreshManager.getTotalRequestsInQueueForTest() == 0
      }

      blockingRefresh = blockRefreshManager()
      previewRepresentation.onActivate()
      // Another quality refresh on reactivation
      assertFalse(previewRepresentation.isInvalidatedForTest())
      delayUntilCondition(delayPerIterationMs = 1000, 5.seconds) {
        refreshManager.getTotalRequestsInQueueForTest() == 1
      }
      assertFalse(previewRepresentation.isInvalidatedForTest())
      blockingRefresh.runningRefreshJob!!.cancel()
    }

  @Test
  fun testPreviewRefreshMetricsAreTracked() {
    // Turn off flag to make sure quality refreshes won't affect the asserts in this test
    StudioFlags.PREVIEW_RENDER_QUALITY.override(false)

    var refreshTrackerFailed = false
    var successEventCount = 0
    val refreshTracker = PreviewRefreshTrackerForTest {
      if (
        it.result != PreviewRefreshEvent.RefreshResult.SUCCESS || it.previewRendersList.isEmpty()
      ) {
        return@PreviewRefreshTrackerForTest
      }
      try {
        assertTrue(it.hasInQueueTimeMillis())
        assertTrue(it.hasRefreshTimeMillis())
        assertTrue(it.hasType())
        assertTrue(it.hasResult())
        assertTrue(it.hasPreviewsCount())
        assertTrue(it.hasPreviewsToRefresh())
        assertTrue(it.previewRendersList.isNotEmpty())
        assertTrue(
          it.previewRendersList.all { render ->
            render.hasResult()
            render.hasRenderTimeMillis()
            render.hasRenderQuality()
            render.hasInflate()
          }
        )
        successEventCount++
      } catch (t: Throwable) {
        refreshTrackerFailed = true
      }
    }

    val previewRepresentation = createPreviewRepresentation()
    try {
      AnalyticsSettings.optedIn = true
      runBlocking(workerThread) {
        PreviewRefreshTracker.setInstanceForTest(
          previewRepresentation.previewView.mainSurface,
          refreshTracker,
        )
        previewRepresentation.compileAndWaitForRefresh()
        delayUntilCondition(delayPerIterationMs = 1000, 5.seconds) { successEventCount > 0 }
        assertFalse(refreshTrackerFailed)
      }
    } finally {
      PreviewRefreshTracker.cleanAfterTesting(previewRepresentation.previewView.mainSurface)
      AnalyticsSettings.optedIn = false
    }
  }

  @Test
  fun clickingOnThePreviewNavigatesToDefinition() {
    runBlocking(workerThread) {
      val preview = createPreviewRepresentation()
      preview.compileAndWaitForRefresh()

      assertEquals(0, runReadAction { projectRule.fixture.caretOffset })

      waitUntil { preview.previewView.mainSurface.models.size == 1 }
      val sceneView = preview.previewView.mainSurface.sceneManagers.first().sceneViews.first()

      withContext(uiThread) {
        preview.navigationHandler.handleNavigateWithCoordinates(
          sceneView,
          sceneView.x,
          sceneView.y,
          false,
        )
      }

      runReadAction {
        val expectedOffset =
          fixture.findElementByText("@Preview", KtAnnotationEntry::class.java).textOffset
        assertEquals(expectedOffset, projectRule.fixture.caretOffset)
      }

      preview.onDeactivateImmediately()
    }
  }

  // Regression test for b/353458840
  @Test
  fun previewsAreOrderedByPositioningThenOffsetThenName() {
    runBlocking(workerThread) {
      psiFile =
        fixture.configureByText(
          "Test.kt",
          // language=kotlin
          """
      annotation class Preview

      @Preview // second due to source offset
      fun second() {
      }

      @Preview // third, fourth and fifth - will have different names and groups
      fun thirdFourthFifth() {
      }

      @Preview // first - will have the TOP display positioning
      fun first() {
      }
    """
            .trimIndent(),
        )

      val first =
        PsiTestPreviewElement(
          displayPositioning = DisplayPositioning.TOP,
          previewElementDefinition = previewElementDefinitionForTextAtCaret("@|Preview // first"),
        )

      val second =
        PsiTestPreviewElement(
          previewElementDefinition = previewElementDefinitionForTextAtCaret("@|Preview // second")
        )

      val thirdFourthAndFifthPreviewElementDefinition =
        previewElementDefinitionForTextAtCaret("@|Preview // third, fourth and fifth")

      val third =
        PsiTestPreviewElement(
          displayName = "1",
          groupName = "3",
          previewElementDefinition = thirdFourthAndFifthPreviewElementDefinition,
        )
      val fourth =
        PsiTestPreviewElement(
          displayName = "2",
          groupName = "2",
          previewElementDefinition = thirdFourthAndFifthPreviewElementDefinition,
        )
      val fifth =
        PsiTestPreviewElement(
          displayName = "3",
          groupName = "1",
          previewElementDefinition = thirdFourthAndFifthPreviewElementDefinition,
        )

      val preview =
        createPreviewRepresentation(
          TestPreviewElementProvider(sequenceOf(first, second, third, fourth, fifth).shuffled())
        )
      preview.compileAndWaitForRefresh()

      val actualPreviewElements =
        preview.renderedPreviewElementsFlowForTest().value.asCollection().toList()
      assertThat(actualPreviewElements)
        .containsExactly(first, second, third, fourth, fifth)
        .inOrder()

      preview.onDeactivateImmediately()
    }
  }

  // Regression test for b/370595516
  @Test
  fun animationPreviewIsDisposedWhenExitingAnimationInspectorMode() {
    runBlocking(workerThread) {
      val animationPreview =
        mock<AnimationPreview<AnimationManager>>().also {
          whenever(it.component).thenReturn(TooltipLayeredPane(JPanel()))
        }
      val previewRepresentation = createPreviewRepresentation(animationPreview = animationPreview)
      previewRepresentation.compileAndWaitForRefresh()

      // start animation inspection
      previewRepresentation.setMode(PreviewMode.AnimationInspection(selected = mock()))
      previewRepresentation.mode.awaitStatus("Animation Inspection mode expected", 1.seconds) {
        it is PreviewMode.AnimationInspection
      }
      retryUntilPassing(1.seconds) {
        assertThat(previewRepresentation.currentAnimationPreview).isEqualTo(animationPreview)
      }

      // stop animation inspection
      previewRepresentation.setMode(PreviewMode.Default())
      previewRepresentation.mode.awaitStatus("Default mode expected", 1.seconds) {
        it is PreviewMode.Default
      }
      retryUntilPassing(1.seconds) {
        verify(animationPreview, times(1)).dispose()
        assertThat(previewRepresentation.currentAnimationPreview).isNull()
      }
      previewRepresentation.onDeactivateImmediately()
    }
  }

  // Regression test for: b/344639845
  @Test
  fun flowsAreCanceledOnDeactivate() {
    runBlocking(workerThread) {
      val previewElementProvider =
        mock<PreviewElementProvider<PsiTestPreviewElement>>().also {
          whenever(it.previewElements()).thenReturn(emptySequence())
        }
      val previewRepresentation = createPreviewRepresentation(previewElementProvider)
      previewRepresentation.compileAndWaitForRefresh()
      clearInvocations(previewElementProvider)

      // writing in the file should trigger element updates through the flows while
      // the lifecycle is active
      ApplicationUtils.invokeWriteActionAndWait(ModalityState.defaultModalityState()) {
        projectRule.fixture.editor.moveCaretToEnd()
        projectRule.fixture.editor.executeAndSave {
          insertText("\n\n// some change to the file\n\n")
        }
      }
      // wait for 2 seconds to give time for the flows to update themselves
      retryUntilPassing(2.seconds) {
        runBlocking { verify(previewElementProvider).previewElements() }
      }

      // once the lifecycle is deactivated, new preview elements should no longer be requested
      previewRepresentation.onDeactivateImmediately()
      clearInvocations(previewElementProvider)

      ApplicationUtils.invokeWriteActionAndWait(ModalityState.defaultModalityState()) {
        projectRule.fixture.editor.moveCaretToEnd()
        projectRule.fixture.editor.executeAndSave {
          insertText("\n\n// some change to the file\n\n")
        }
      }
      // wait for 2 seconds to give time for the flows to update themselves in case of a regression
      delay(2.seconds)
      verifyNoInteractions(previewElementProvider)
    }
  }

  // Regression test for b/373572532
  @Test
  fun layoutOptionIsPersisted(): Unit =
    runBlocking(workerThread) {
      val persistedPreviewRepresentation = createPreviewRepresentation()
      persistedPreviewRepresentation.compileAndWaitForRefresh()

      assertThat(persistedPreviewRepresentation.mode.value)
        .isNotEqualTo(PreviewMode.Default(LIST_LAYOUT_OPTION))
      persistedPreviewRepresentation.setMode(PreviewMode.Default(LIST_LAYOUT_OPTION))
      assertThat(persistedPreviewRepresentation.mode.value)
        .isEqualTo(PreviewMode.Default(LIST_LAYOUT_OPTION))
      retryUntilPassing(1.seconds) {
        assertThat(
            persistedPreviewRepresentation.previewView.mainSurface.layoutManagerSwitcher
              ?.currentLayout
              ?.value
          )
          .isEqualTo(LIST_LAYOUT_OPTION)
      }

      val state = persistedPreviewRepresentation.getState()

      val restoredPreviewRepresentation = createPreviewRepresentation()
      assertThat(restoredPreviewRepresentation.mode.value)
        .isNotEqualTo(PreviewMode.Default(LIST_LAYOUT_OPTION))
      restoredPreviewRepresentation.setState(state)
      restoredPreviewRepresentation.compileAndWaitForRefresh()
      assertThat(restoredPreviewRepresentation.mode.value)
        .isEqualTo(PreviewMode.Default(LIST_LAYOUT_OPTION))
      retryUntilPassing(1.seconds) {
        assertThat(
            restoredPreviewRepresentation.previewView.mainSurface.layoutManagerSwitcher
              ?.currentLayout
              ?.value
          )
          .isEqualTo(LIST_LAYOUT_OPTION)
      }

      persistedPreviewRepresentation.onDeactivateImmediately()
      restoredPreviewRepresentation.onDeactivateImmediately()
    }

  // Regression test for b/373572532
  @Test
  fun galleryLayoutOptionIsPersisted(): Unit =
    runBlocking(workerThread) {
      val previewElement = PsiTestPreviewElement("test element")
      val previewElementProvider = TestPreviewElementProvider(sequenceOf(previewElement))
      val persistedPreviewRepresentation = createPreviewRepresentation(previewElementProvider)
      persistedPreviewRepresentation.compileAndWaitForRefresh()

      assertThat(persistedPreviewRepresentation.mode.value.layoutOption)
        .isNotEqualTo(GALLERY_LAYOUT_OPTION)
      persistedPreviewRepresentation.setMode(PreviewMode.Gallery(previewElement))
      assertThat(persistedPreviewRepresentation.mode.value)
        .isEqualTo(PreviewMode.Gallery(previewElement))
      retryUntilPassing(1.seconds) {
        assertThat(
            persistedPreviewRepresentation.previewView.mainSurface.layoutManagerSwitcher
              ?.currentLayout
              ?.value
          )
          .isEqualTo(GALLERY_LAYOUT_OPTION)
      }

      val state = persistedPreviewRepresentation.getState()

      val restoredPreviewRepresentation = createPreviewRepresentation(previewElementProvider)
      restoredPreviewRepresentation.setState(state)
      restoredPreviewRepresentation.compileAndWaitForRefresh()
      assertThat(restoredPreviewRepresentation.mode.value)
        .isEqualTo(PreviewMode.Gallery(previewElement))
      retryUntilPassing(1.seconds) {
        assertThat(
            restoredPreviewRepresentation.previewView.mainSurface.layoutManagerSwitcher
              ?.currentLayout
              ?.value
          )
          .isEqualTo(GALLERY_LAYOUT_OPTION)
      }

      persistedPreviewRepresentation.onDeactivateImmediately()
      restoredPreviewRepresentation.onDeactivateImmediately()
    }

  // Regression test for b/373572532
  @Test
  fun groupSelectionIsPersisted(): Unit =
    runBlocking(workerThread) {
      val previewElement =
        PsiTestPreviewElement(displayName = "test element", groupName = "test group")
      val previewElementProvider = TestPreviewElementProvider(sequenceOf(previewElement))
      val persistedPreviewRepresentation = createPreviewRepresentation(previewElementProvider)
      persistedPreviewRepresentation.compileAndWaitForRefresh()

      assertThat(persistedPreviewRepresentation.groupManager.groupFilter)
        .isEqualTo(PreviewGroup.All)
      persistedPreviewRepresentation.groupManager.groupFilter =
        PreviewGroup.namedGroup("test group")

      val state = persistedPreviewRepresentation.getState()

      val restoredPreviewRepresentation = createPreviewRepresentation(previewElementProvider)
      restoredPreviewRepresentation.setState(state)
      restoredPreviewRepresentation.compileAndWaitForRefresh()
      assertThat(restoredPreviewRepresentation.groupManager.groupFilter)
        .isEqualTo(PreviewGroup.namedGroup("test group"))

      persistedPreviewRepresentation.onDeactivateImmediately()
      restoredPreviewRepresentation.onDeactivateImmediately()
    }

  private suspend fun blockRefreshManager(): TestPreviewRefreshRequest {
    // block the refresh manager with a high priority refresh that won't finish
    TestPreviewRefreshRequest.log = StringBuilder()
    TestPreviewRefreshRequest.expectedLogPrintCount = CountDownLatch(1)
    val blockingRefresh =
      TestPreviewRefreshRequest(
        myScope,
        clientId = "testClient",
        priority = 100,
        name = "testRequest",
        doInsideRefreshJob = {
          while (true) {
            delay(500)
          }
        },
      )
    refreshManager.requestRefreshSync(blockingRefresh)
    TestPreviewRefreshRequest.expectedLogPrintCount.await()
    assertEquals(0, refreshManager.getTotalRequestsInQueueForTest())
    return blockingRefresh
  }

  private fun createPreviewRepresentation(
    customPreviewElementProvider: PreviewElementProvider<PsiTestPreviewElement>? = null,
    animationPreview: AnimationPreview<*>? = null,
  ): CommonPreviewRepresentation<PsiTestPreviewElement> {
    val previewElementProvider =
      customPreviewElementProvider
        ?: TestPreviewElementProvider(
          sequenceOf(
            PsiTestPreviewElement(
              previewElementDefinition =
                runReadAction {
                  fixture.findElementByText("@Preview", KtAnnotationEntry::class.java)?.let {
                    smartPointerManager.createSmartPsiElementPointer(it)
                  }
                }
            )
          )
        )
    val previewRepresentation =
      object :
        CommonPreviewRepresentation<PsiTestPreviewElement>(
          adapterViewFqcn = "TestAdapterViewFqcn",
          psiFile,
          { previewElementProvider },
          modelAdapter,
          viewConstructor = { project, surfaceBuilder, parentDisposable ->
            CommonNlDesignSurfacePreviewView(project, surfaceBuilder, parentDisposable).also {
              previewView = it
            }
          },
          viewModelConstructor = { _, _, _, _, _, _ -> previewViewModelMock },
          configureDesignSurface = {},
          renderingTopic = RenderAsyncActionExecutor.RenderingTopic.NOT_SPECIFIED,
          createRefreshEventBuilder = { surface ->
            PreviewRefreshEventBuilder(
              PreviewRefreshEvent.PreviewType.UNKNOWN_TYPE,
              PreviewRefreshTracker.getInstance(surface),
            )
          },
        ) {

        override fun createAnimationInspector(element: PreviewElement<*>) = animationPreview
      }
    Disposer.register(fixture.testRootDisposable, previewRepresentation)
    return previewRepresentation
  }

  private suspend fun CommonPreviewRepresentation<PsiTestPreviewElement>
    .compileAndWaitForRefresh() {
    // wait for smart mode and status to be needs build
    waitForSmartMode(fixture.project)
    delayUntilCondition(delayPerIterationMs = 1000, 10.seconds) {
      getProjectBuildStatusForTest() == RenderingBuildStatus.NeedsBuild
    }

    // Activate and wait for build listener setup to finish
    assertFalse(hasBuildListenerSetupFinishedForTest())
    onActivate()
    delayUntilCondition(delayPerIterationMs = 1000, 10.seconds) {
      hasBuildListenerSetupFinishedForTest()
    }
    delayUntilCondition(delayPerIterationMs = 1000, 10.seconds) {
      hasFlowInitializationFinishedForTest()
    }
    assertTrue(isInvalidatedForTest())

    // Build the project and wait for a refresh to happen, setting the 'invalidated' to false
    buildSystemServices.simulateArtifactBuild(ProjectSystemBuildManager.BuildStatus.SUCCESS)
    delayUntilCondition(delayPerIterationMs = 1000, 40.seconds) {
      !isInvalidatedForTest() &&
        refreshManager.getTotalRequestsInQueueForTest() == 0 &&
        refreshManager.refreshingTypeFlow.value == null
    }
    assertFalse(isInvalidatedForTest())
  }

  private suspend fun previewElementDefinitionForTextAtCaret(
    textWithCaret: String
  ): SmartPsiElementPointer<PsiElement> {
    withContext(uiThread) { fixture.moveCaret(textWithCaret) }
    return runReadAction {
      fixture.elementAtCaret.let { smartPointerManager.createSmartPsiElementPointer(it) }
    }
  }

  private val CommonPreviewRepresentation<*>.groupManager: PreviewGroupManager
    get() {
      val context =
        DataManager.getInstance()
          .customizeDataContext(DataContext.EMPTY_CONTEXT, previewView.mainSurface)
      return PreviewGroupManager.KEY.getData(context)
        ?: fail("Expected a group manager to be present")
    }
}
