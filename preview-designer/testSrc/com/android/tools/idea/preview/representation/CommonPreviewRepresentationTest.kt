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

import com.android.testutils.waitForCondition
import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.preview.PreviewElementModelAdapter
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.PreviewRefreshManager
import com.android.tools.idea.preview.PsiTestPreviewElement
import com.android.tools.idea.preview.TestPreviewRefreshRequest
import com.android.tools.idea.preview.requestRefreshSync
import com.android.tools.idea.preview.viewmodels.CommonPreviewViewModel
import com.android.tools.idea.preview.views.CommonNlDesignSurfacePreviewView
import com.android.tools.idea.preview.waitUntilRefreshStarts
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.rendering.RenderAsyncActionExecutor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.runInEdtAndWait
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

private lateinit var previewView: CommonNlDesignSurfacePreviewView
private lateinit var previewViewModelMock: CommonPreviewViewModel

private class TestPreviewElementProvider(private val previewElement: PsiTestPreviewElement) :
  PreviewElementProvider<PsiTestPreviewElement> {
  override suspend fun previewElements(): Sequence<PsiTestPreviewElement> =
    sequenceOf(previewElement)
}

private class TestPreviewElementModelAdapter(private val previewElement: PsiTestPreviewElement) :
  PreviewElementModelAdapter<PsiTestPreviewElement, NlModel> {
  override fun calcAffinity(el1: PsiTestPreviewElement, el2: PsiTestPreviewElement?): Int = 0

  override fun toXml(previewElement: PsiTestPreviewElement): String = ""

  override fun applyToConfiguration(
    previewElement: PsiTestPreviewElement,
    configuration: Configuration,
  ) {}

  override fun modelToElement(model: NlModel): PsiTestPreviewElement? = previewElement

  override fun createDataContext(previewElement: PsiTestPreviewElement): DataContext =
    mock(DataContext::class.java)

  override fun toLogString(previewElement: PsiTestPreviewElement): String = ""

  override fun createLightVirtualFile(
    content: String,
    backedFile: VirtualFile,
    id: Long,
  ): LightVirtualFile = mock(LightVirtualFile::class.java)
}

class CommonPreviewRepresentationTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  private lateinit var myScope: CoroutineScope
  private lateinit var refreshManager: PreviewRefreshManager

  private val fixture
    get() = projectRule.fixture

  private val project
    get() = fixture.project

  @Before
  fun setup() {
    runBlocking(uiThread) {
      val surfaceBuilder = NlDesignSurface.builder(project, fixture.testRootDisposable)
      previewView =
        CommonNlDesignSurfacePreviewView(project, surfaceBuilder, fixture.testRootDisposable)
    }
    runInEdtAndWait { TestProjectSystem(project).useInTests() }
    previewViewModelMock = mock(CommonPreviewViewModel::class.java)
    myScope = AndroidCoroutineScope(fixture.testRootDisposable)
    refreshManager =
      PreviewRefreshManager.getInstanceForTest(
        myScope,
        RenderAsyncActionExecutor.RenderingTopic.NOT_SPECIFIED,
      )
  }

  @Test
  fun testFullRefreshIsTriggeredOnSuccessfulBuild() =
    runBlocking(workerThread) {
      val previewRepresentation = createPreviewRepresentation()

      // wait for smart mode and status to be needs build
      DumbModeTestUtils.waitForSmartMode(fixture.project)
      waitForCondition(10.seconds) {
        previewRepresentation.getProjectBuildStatusForTest() == ProjectStatus.NeedsBuild
      }

      // Activate and wait for build listener setup to finish
      assertFalse(previewRepresentation.hasBuildListenerSetupFinishedForTest())
      previewRepresentation.onActivate()
      waitForCondition(10.seconds) { previewRepresentation.hasBuildListenerSetupFinishedForTest() }
      assertTrue(previewRepresentation.isInvalidatedForTest())

      // Build the project and wait for a refresh to happen, setting the 'invalidated' to false
      ProjectSystemService.getInstance(project).projectSystem.getBuildManager().compileProject()
      waitForCondition(10.seconds) { !previewRepresentation.isInvalidatedForTest() }
      assertFalse(previewRepresentation.isInvalidatedForTest())

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

      // building the project again should invalidate the preview representation
      assertFalse(previewRepresentation.isInvalidatedForTest())
      ProjectSystemService.getInstance(project).projectSystem.getBuildManager().compileProject()
      waitForCondition(10.seconds) { previewRepresentation.isInvalidatedForTest() }
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
      waitForCondition(10.seconds) { !previewRepresentation.isInvalidatedForTest() }
      assertFalse(previewRepresentation.isInvalidatedForTest())
    }

  private fun createPreviewRepresentation(): CommonPreviewRepresentation<PsiTestPreviewElement> {
    val psiFile = runWriteActionAndWait {
      fixture.addFileToProjectAndInvalidate(
        "Test.kt",
        // language=kotlin
        """
        fun MyFun() {
          println("Hello world!")
        }
      """
          .trimIndent(),
      )
    }
    val previewElement = PsiTestPreviewElement()
    val previewElementProvider = TestPreviewElementProvider(previewElement)
    val previewElementModelAdapter = TestPreviewElementModelAdapter(previewElement)
    val previewRepresentation =
      CommonPreviewRepresentation(
        adapterViewFqcn = "TestAdapterViewFqcn",
        psiFile,
        previewElementProvider,
        previewElementModelAdapter,
        viewConstructor = { _, _, _ -> previewView },
        viewModelConstructor = { _, _, _, _, _, _ -> previewViewModelMock },
        configureDesignSurface = {},
        renderingTopic = RenderAsyncActionExecutor.RenderingTopic.NOT_SPECIFIED,
        isEssentialsModeEnabled = { false },
      )
    Disposer.register(fixture.testRootDisposable, previewRepresentation)
    return previewRepresentation
  }
}
