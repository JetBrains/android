/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.fast

import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.concurrency.UniqueTaskCoroutineLauncher
import com.android.tools.idea.concurrency.runWriteActionAndWait
import com.android.tools.idea.editors.fast.FastPreviewBundle.message
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import java.io.File
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.withContext
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays

/** Maximum amount of time to wait for a fast compilation to happen. */
private val FAST_PREVIEW_COMPILE_TIMEOUT =
  java.lang.Long.getLong("fast.preview.daemon.compile.seconds.timeout", 30)

private suspend fun PsiFile.saveIfNeeded() {
  val vFile = virtualFile ?: return
  val fileManager = FileDocumentManager.getInstance()
  val document = fileManager.getCachedDocument(vFile) ?: return
  if (!fileManager.isDocumentUnsaved(document)) return
  runWriteActionAndWait { fileManager.saveDocument(document) }
}

/**
 * Starts a new fast compilation for the current file in the Preview and returns the result of the
 * compilation. All given files must belong to the same project.
 */
internal suspend fun fastCompile(
  parentDisposable: Disposable,
  contextModule: Module,
  files: Set<PsiFile>,
  fastPreviewManager: FastPreviewManager = FastPreviewManager.getInstance(files.first().project),
  requestTracker: FastPreviewTrackerManager.Request =
    FastPreviewTrackerManager.getInstance(files.first().project).trackRequest()
): CompilationResult = coroutineScope {
  val project = files.first().project

  val compileProgressIndicator =
    BackgroundableProcessIndicator(project, message("notification.compiling"), "", "", false)
  compileProgressIndicator.isIndeterminate = true
  Disposer.register(parentDisposable, compileProgressIndicator)
  try {
    compileProgressIndicator.start()

    files.forEach { it.saveIfNeeded() }

    val (result, outputAbsolutePath) =
      withTimeout(Duration.ofSeconds(FAST_PREVIEW_COMPILE_TIMEOUT)) {
        fastPreviewManager.compileRequest(files, contextModule, tracker = requestTracker)
      }
    val isSuccess = result == CompilationResult.Success
    if (isSuccess) {
      ModuleClassLoaderOverlays.getInstance(contextModule)
        .pushOverlayPath(File(outputAbsolutePath).toPath())
    }

    return@coroutineScope result
  } catch (_: CancellationException) {
    return@coroutineScope CompilationResult.CompilationAborted()
  } catch (_: ProcessCanceledException) {
    return@coroutineScope CompilationResult.CompilationAborted()
  } finally {
    compileProgressIndicator.stop()
    compileProgressIndicator.processFinish()
  }
}

/**
 * Requests a "Fast Preview" compilation and invokes the [trackedForceRefresh] if successful. This
 * method tracks the time that the compilation and the execution of the [trackedForceRefresh] call
 * take in order to do statistics reporting.
 *
 * The given [contextModule] will be used as context for the compilation. This module or one of this
 * module dependencies must contain all the given [files].
 */
internal suspend fun requestFastPreviewRefreshAndTrack(
  parentDisposable: Disposable,
  contextModule: Module,
  files: Set<PsiFile>,
  currentStatus: ComposePreviewManager.Status,
  launcher: UniqueTaskCoroutineLauncher,
  trackedForceRefresh: () -> Job?
): CompilationResult = coroutineScope {
  // We delay the reporting of compilationSucceded until we have the amount of time the refresh
  // took. Either refreshSucceeded or
  // refreshFailed should be called.
  val delegateRequestTracker =
    FastPreviewTrackerManager.getInstance(files.first().project).trackRequest()
  val requestTracker =
    object : FastPreviewTrackerManager.Request by delegateRequestTracker {
      private var compilationDurationMs: Long = -1
      private var compiledFiles: Int = -1
      private var compilationSuccess: Boolean? = null
      override fun compilationSucceeded(
        compilationDurationMs: Long,
        compiledFiles: Int,
        refreshTimeMs: Long
      ) {
        compilationSuccess = true
        this.compilationDurationMs = compilationDurationMs
        this.compiledFiles = compiledFiles
      }

      override fun compilationFailed(compilationDurationMs: Long, compiledFiles: Int) {
        compilationSuccess = false
        this.compilationDurationMs = compilationDurationMs
        this.compiledFiles = compiledFiles
      }

      /**
       * Reports that the refresh has completed. If [refreshTimeMs] is -1, the refresh has failed.
       */
      /**
       * Reports that the refresh has completed. If [refreshTimeMs] is -1, the refresh has failed.
       */
      private fun reportRefresh(refreshTimeMs: Long = -1) {
        when (compilationSuccess) {
          true ->
            delegateRequestTracker.compilationSucceeded(
              compilationDurationMs,
              compiledFiles,
              refreshTimeMs
            )
          false -> delegateRequestTracker.compilationFailed(compilationDurationMs, compiledFiles)
          null -> Unit
        }
      }

      fun refreshSucceeded(refreshTimeMs: Long) {
        reportRefresh(refreshTimeMs)
      }

      fun refreshFailed() {
        reportRefresh()
      }
    }

  // We only want the first result sent through the channel
  val deferredCompilationResult =
    CompletableDeferred<CompilationResult>(CompilationResult.CompilationError())

  launcher.launch {
    var refreshJob: Job? = null
    try {
      if (!currentStatus.hasSyntaxErrors) {
        val result =
          fastCompile(parentDisposable, contextModule, files, requestTracker = requestTracker)
        deferredCompilationResult.complete(result)
        if (result is CompilationResult.Success) {
          val refreshStartMs = System.currentTimeMillis()
          refreshJob = trackedForceRefresh()
          refreshJob?.invokeOnCompletion { throwable ->
            when (throwable) {
              null -> requestTracker.refreshSucceeded(System.currentTimeMillis() - refreshStartMs)
              is CancellationException ->
                requestTracker.refreshCancelled(compilationCompleted = true)
              else -> requestTracker.refreshFailed()
            }
          }
          refreshJob?.join()
        } else {
          if (result is CompilationResult.CompilationAborted) {
            requestTracker.refreshCancelled(compilationCompleted = false)
          } else {
            // Compilation failed, report the refresh as failed too
            requestTracker.refreshFailed()
          }
        }
      } else {
        // At this point, the compilation result should have already been sent if any compilation
        // was done. So, send CompilationAborted result.
        deferredCompilationResult.complete(CompilationResult.CompilationAborted())
      }
    } catch (e: CancellationException) {
      // Any cancellations during the compilation step are handled by fastCompile, so at
      // this point, the compilation was completed or no compilation was done. Either way,
      // a compilation result was already sent through the channel. However, the refresh
      // may still need to be cancelled.
      // Use NonCancellable to make sure to wait until the cancellation is completed.
      withContext(NonCancellable) {
        deferredCompilationResult.complete(CompilationResult.CompilationAborted())
        refreshJob?.cancelAndJoin()
        throw e
      }
    }
  }
  // wait only for the compilation to finish, not for the whole refresh
  return@coroutineScope deferredCompilationResult.await()
}
