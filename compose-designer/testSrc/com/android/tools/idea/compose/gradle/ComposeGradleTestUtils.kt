/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.compose.gradle

import com.android.tools.adtui.swing.FakeMouse
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.SyncNlModel.*
import com.android.tools.idea.common.layout.positionable.scaledContentSize
import com.android.tools.idea.common.model.AccessibilityModelUpdater
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.compose.preview.waitForAllRefreshesToFinish
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.rendering.RenderService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.runInEdtAndWait
import java.awt.event.KeyEvent.VK_SHIFT
import java.util.concurrent.TimeoutException
import javax.swing.JLabel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.android.facet.AndroidFacet

/**
 * Find the [PsiFile] corresponding to a file that is part of the given [project], whose location is
 * defined by the given [relativePath] from the [project]'s root directory.
 */
internal fun getPsiFile(project: Project, relativePath: String): PsiFile {
  val vFile = project.guessProjectDir()!!.findFileByRelativePath(relativePath)!!
  return runReadAction { PsiManager.getInstance(project).findFile(vFile)!! }
}

/** Activates the [ComposePreviewRepresentation] and waits for scenes to complete rendering. */
suspend fun ComposePreviewRepresentation.activateAndWaitForRender(
  fakeUi: FakeUi,
  timeout: Duration = 90.seconds,
) =
  try {
    withTimeout(timeout = timeout) {
      Logger.getInstance(ComposePreviewRepresentation::class.java)
        .debug("Activating ComposePreviewRepresentation for tests")
      onActivate()

      var retryCounter = 0

      val sceneViewPeerPanels = mutableSetOf<SceneViewPeerPanel>()
      while (isActive && sceneViewPeerPanels.isEmpty()) {
        withContext(Dispatchers.Main) {
          delay(250)
          withContext(uiThread) { fakeUi.root.validate() }
          sceneViewPeerPanels.addAll(fakeUi.findAllComponents())

          if (retryCounter++ % 4 == 0) {
            Logger.getInstance(ComposePreviewRepresentation::class.java).debug {
              val resultsString =
                sceneViewPeerPanels
                  .mapNotNull { it.sceneView.sceneManager as? LayoutlibSceneManager }
                  .joinToString { it.renderResult.toString() }
                  .ifBlank { "<empty>" }
              "Retry $retryCounter $resultsString"
            }
          }
        }
      }
      Logger.getInstance(ComposePreviewRepresentation::class.java)
        .debug("ComposePreviewRepresentation active")

      // Now wait for them to be rendered
      waitForRender(sceneViewPeerPanels, timeout)
    }
  } catch (e: TimeoutCancellationException) {
    throw AssertionError(
      "Timeout while waiting for render to complete",
      TimeoutException().also { it.stackTrace = RenderService.getCurrentExecutionStackTrace() },
    )
  }

suspend fun waitForRender(
  sceneViewPeerPanels: Set<SceneViewPeerPanel>,
  timeout: Duration = 60.seconds,
) =
  withTimeout(timeout) {
    Logger.getInstance(ComposePreviewRepresentation::class.java).debug("Waiting for render")
    waitForAllRefreshesToFinish(timeout)
    var retryCounter = 0
    while (
      isActive &&
        sceneViewPeerPanels.any {
          (it.sceneView.sceneManager as? LayoutlibSceneManager)?.renderResult == null
        }
    ) {
      if (retryCounter++ % 4 == 0) {
        Logger.getInstance(ComposePreviewRepresentation::class.java).debug {
          val resultsString =
            sceneViewPeerPanels
              .mapNotNull { it.sceneView.sceneManager as? LayoutlibSceneManager }
              .joinToString { it.renderResult.toString() }
          "Retry $retryCounter $resultsString"
        }
      }
      delay(250)
    }
  }

internal fun FakeUi.clickPreviewName(sceneViewPanel: SceneViewPeerPanel) {
  val nameLabel = sceneViewPanel.sceneViewTopPanel.components.single { it is JLabel }
  runInEdtAndWait { clickRelativeTo(nameLabel, 1, 1) }
}

internal fun FakeUi.clickPreviewImage(
  sceneViewPanel: SceneViewPeerPanel,
  rightClick: Boolean = false,
  pressingShift: Boolean = false,
) {
  sceneViewPanel.positionableAdapter.let {
    runInEdtAndWait {
      if (pressingShift) keyboard.press(VK_SHIFT)
      mouse.click(
        it.x + it.scaledContentSize.width / 2,
        it.y + it.scaledContentSize.height / 2,
        if (rightClick) FakeMouse.Button.RIGHT else FakeMouse.Button.LEFT,
      )
      if (pressingShift) keyboard.release(VK_SHIFT)
    }
  }
}

internal fun createNlModelForCompose(
  parent: Disposable,
  facet: AndroidFacet,
  file: VirtualFile,
): NlModel {
  val nlModel = create(parent, NlComponentRegistrar, BuildTargetReference.gradleOnly(facet), file)
  // Sets the correct model update for Compose
  nlModel.setModelUpdater(AccessibilityModelUpdater())
  return nlModel
}
