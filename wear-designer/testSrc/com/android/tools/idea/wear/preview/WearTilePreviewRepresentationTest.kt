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
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceListener
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.mvvm.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.util.TestToolWindowManager
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    Logger.getInstance(ProjectStatus::class.java).setLevel(LogLevel.ALL)
    logger.info("setup")
    runInEdtAndWait { TestProjectSystem(project).useInTests() }
    logger.info("setup complete")

    project.replaceService(
      ToolWindowManager::class.java,
      TestToolWindowManager(project),
      fixture.testRootDisposable,
    )
    ToolWindowManager.getInstance(project)
      .registerToolWindow(RegisterToolWindowTask(ProblemsView.ID))
  }

  @Test
  fun testPreviewInitialization() =
    runBlocking(workerThread) {
      val preview = createWearTilePreviewRepresentation()

      preview.previewView.surface.models.forEach {
        assertTrue(preview.navigationHandler.defaultNavigationMap.contains(it))
      }

      val status = preview.previewViewModel
      assertFalse(status.isOutOfDate)
      val renderResults = preview.previewView.surface.sceneManagers.mapNotNull { it.renderResult }
      // Ensure the only warning message is the missing Android SDK message
      assertTrue(
        renderResults
          .flatMap { it.logger.messages }
          .none { !it.html.contains("No Android SDK found.") }
      )
      preview.onDeactivate()
    }

  @Test
  fun testDataKeysShouldBeRegistered() =
    runBlocking(workerThread) {
      val preview = createWearTilePreviewRepresentation()

      assertNotNull(preview.previewView.surface.getData(PreviewModeManager.KEY.name))
      assertNotNull(preview.previewView.surface.getData(PREVIEW_VIEW_MODEL_STATUS.name))

      preview.onDeactivate()
    }

  private suspend fun createWearTilePreviewRepresentation(): WearTilePreviewRepresentation {
    val wearTileTestFile = createWearTilePreviewTestFile()
    val modelRenderedLatch = CountDownLatch(2)
    val previewRepresentation =
      WearTilePreviewRepresentationProvider().createRepresentation(wearTileTestFile)
        as WearTilePreviewRepresentation

    previewRepresentation.previewView.surface.addListener(
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
    project.runWhenSmartAndSyncedOnEdt(callback = {
      runBlocking(Dispatchers.IO) {
        logger.info("compile")
        ProjectSystemService.getInstance(project).projectSystem.getBuildManager().compileProject()
        logger.info("activate")
        previewRepresentation.onActivate()
      }
    })

    withContext(Dispatchers.IO) {
      modelRenderedLatch.await()
      delayWhileRefreshingOrDumb(previewRepresentation)
    }

    return previewRepresentation
  }

  private fun createWearTilePreviewTestFile() = runWriteActionAndWait {
    fixture.addFileToProjectAndInvalidate(
      "Test.kt", // language=kotlin
      """
          package com.android.test

        import android.content.Context
        import androidx.wear.tiles.TileService
        import androidx.wear.tiles.tooling.preview.Preview
        import androidx.wear.tiles.tooling.preview.TilePreviewData
        import androidx.wear.tiles.tooling.preview.WearDevices

        @Preview
        private fun tilePreview(): TilePreviewData {
          return TilePreviewData()
        }

        @Preview(name = "preview2", group = "groupA")
        private fun tilePreview2(): TilePreviewData {
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
