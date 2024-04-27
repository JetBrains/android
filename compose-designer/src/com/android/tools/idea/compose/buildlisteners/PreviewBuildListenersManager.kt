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
package com.android.tools.idea.compose.buildlisteners

import com.android.tools.idea.compose.preview.animation.ComposePreviewAnimationManager
import com.android.tools.idea.compose.preview.essentials.ComposePreviewEssentialsModeManager
import com.android.tools.idea.editors.fast.CompilationResult
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.projectsystem.BuildListener
import com.android.tools.idea.projectsystem.setupBuildListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.base.util.module

/**
 * Class responsible for setting up build (both Fast Preview and Project System builds) listeners
 * that will update the preview state according to the build events.
 */
class PreviewBuildListenersManager(
  private val psiFilePointerProvider: () -> SmartPsiElementPointer<PsiFile>,
  private val invalidate: () -> Unit,
  private val refresh: () -> Unit,
  private val requestVisibilityAndNotificationsUpdate: () -> Unit
) {

  private val log = Logger.getInstance(PreviewBuildListenersManager::class.java)

  @VisibleForTesting
  var buildListenerSetupFinished = false
    private set

  fun setupPreviewBuildListeners(
    disposable: Disposable,
    shouldRefreshAfterBuildFailed: () -> Boolean,
    onBuildStarted: () -> Unit,
  ) {
    val psiFile = psiFilePointerProvider().element
    requireNotNull(psiFile) { "PsiFile was disposed before the preview initialization completed." }
    val module = runReadAction { psiFile.module }
    val project =
      module?.project
        ?: run {
          return@setupPreviewBuildListeners
        }

    setupBuildListener(
      project,
      object : BuildListener {
        private var refreshAfterBuildFailed = false

        override fun startedListening() {
          buildListenerSetupFinished = true
        }

        override fun buildSucceeded() {
          log.debug("buildSucceeded")
          module.let {
            // When the build completes successfully, we do not need the overlay until a new
            // modification happens. But invalidation should not be done when this listener is
            // called during setup, as a consequence of an old build (see startedListening)
            if (buildListenerSetupFinished) {
              ModuleClassLoaderOverlays.getInstance(it).invalidateOverlayPaths()
            }
          }

          // If Fast Preview is enabled, prefetch the daemon for the current configuration.
          // This should not happen when essentials mode is enabled.
          if (
            !module.isDisposed &&
              FastPreviewManager.getInstance(project).isEnabled &&
              !ComposePreviewEssentialsModeManager.isEssentialsModeEnabled
          ) {
            FastPreviewManager.getInstance(project).preStartDaemon(module)
          }

          afterBuildComplete(isSuccessful = true)
        }

        override fun buildFailed() {
          log.debug("buildFailed")
          afterBuildComplete(isSuccessful = false, refreshAfterBuildFailed)
        }

        override fun buildCleaned() {
          log.debug("buildCleaned")
          buildFailed()
        }

        override fun buildStarted() {
          log.debug("buildStarted")
          onBuildStarted()
          refreshAfterBuildFailed = shouldRefreshAfterBuildFailed()
          afterBuildStarted()
        }
      },
      disposable
    )

    FastPreviewManager.getInstance(project)
      .addListener(
        disposable,
        object : FastPreviewManager.Companion.FastPreviewManagerListener {
          override fun onCompilationStarted(files: Collection<PsiFile>) {
            psiFile.let { editorFile ->
              if (files.any { it.isEquivalentTo(editorFile) }) afterBuildStarted()
            }
          }

          override fun onCompilationComplete(
            result: CompilationResult,
            files: Collection<PsiFile>
          ) {
            // Notify on any Fast Preview compilation to ensure we refresh all the previews
            // correctly.
            afterBuildComplete(result == CompilationResult.Success)
          }
        }
      )
  }

  /** Called after a project build has completed. */
  private fun afterBuildComplete(isSuccessful: Boolean, refreshOnFail: Boolean = false) {
    if (isSuccessful) {
      invalidate()
      refresh()
    } else {
      requestVisibilityAndNotificationsUpdate()
      if (refreshOnFail) {
        refresh()
      }
    }
  }

  private fun afterBuildStarted() {
    // When building, invalidate the Animation Preview, since the animations are now obsolete and
    // new ones will be subscribed once build is complete and refresh is triggered.
    ComposePreviewAnimationManager.invalidate(psiFilePointerProvider())
    requestVisibilityAndNotificationsUpdate()
  }
}
