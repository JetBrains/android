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
package com.android.tools.idea.preview

import com.android.tools.compile.fast.CompilationResult
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.preview.essentials.PreviewEssentialsModeManager
import com.android.tools.idea.preview.mvvm.PreviewViewModel
import com.android.tools.idea.rendering.BuildListener
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.rendering.setupBuildListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.SlowOperations
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.jetbrains.annotations.VisibleForTesting

/**
 * Class responsible for setting up a Project System build listener, and also a fast preview
 * compilation listener when [isFastPreviewSupported] is true, that will update the preview state
 * according to the build events by calling [invalidate], [refresh] and
 * [requestVisibilityAndNotificationsUpdate].
 *
 * TODO(b/328056861): remove [requestVisibilityAndNotificationsUpdate] once Compose Preview starts
 *   using [PreviewViewModel].
 */
class PreviewBuildListenersManager(
  private val isFastPreviewSupported: Boolean,
  private val invalidate: () -> Unit,
  private val refresh: () -> Unit,
  private val requestVisibilityAndNotificationsUpdate: () -> Unit = {},
) {

  private val log = Logger.getInstance(PreviewBuildListenersManager::class.java)

  @VisibleForTesting
  var buildListenerSetupFinished = false
    private set

  fun setupPreviewBuildListeners(
    disposable: Disposable,
    psiFilePointer: SmartPsiElementPointer<PsiFile>,
    shouldRefreshAfterBuildFailed: () -> Boolean = { false },
    onBuildStarted: () -> Unit = {},
  ) {
    val psiFile = runReadAction { psiFilePointer.element }
    requireNotNull(psiFile) { "PsiFile was disposed before the preview initialization completed." }
    val buildTargetReference =
      SlowOperations.allowSlowOperations(ThrowableComputable { BuildTargetReference.from(psiFile) })
        ?: return
    val module = buildTargetReference.module
    setupBuildListener(
      buildTargetReference,
      object : BuildListener {
        private var refreshAfterBuildFailed = false

        override fun startedListening() {
          buildListenerSetupFinished = true
        }

        override fun buildSucceeded() {
          log.debug("buildSucceeded")
          if (isFastPreviewSupported && !module.isDisposed) {
            // When the build completes successfully, we do not need the overlay until a new
            // modification happens. But invalidation should not be done when this listener is
            // called during setup, as a consequence of an old build (see startedListening)
            if (buildListenerSetupFinished) {
              ModuleClassLoaderOverlays.getInstance(module).invalidateOverlayPaths()
            }
            // If Fast Preview is enabled, prefetch the daemon for the current configuration.
            // This should not happen when essentials mode is enabled.
            FastPreviewManager.getInstance(module.project).let {
              if (it.isEnabled && !PreviewEssentialsModeManager.isEssentialsModeEnabled) {
                it.preStartDaemon(module)
              }
            }
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
          refreshAfterBuildFailed = shouldRefreshAfterBuildFailed()
          onBuildStarted()
        }
      },
      disposable,
    )

    if (isFastPreviewSupported) {
      FastPreviewManager.getInstance(module.project)
        .addListener(
          disposable,
          object : FastPreviewManager.Companion.FastPreviewManagerListener {
            override fun onCompilationStarted(files: Collection<PsiFile>) {
              psiFile.let { editorFile ->
                if (files.any { it.isEquivalentTo(editorFile) }) onBuildStarted()
              }
            }

            override fun onCompilationComplete(
              result: CompilationResult,
              files: Collection<PsiFile>,
            ) {
              // Notify on any Fast Preview compilation to ensure we refresh all the previews
              // correctly.
              afterBuildComplete(result == CompilationResult.Success)
            }
          },
        )
    }
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
}
