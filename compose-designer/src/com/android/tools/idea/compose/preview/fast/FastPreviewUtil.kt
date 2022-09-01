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

import com.android.tools.idea.concurrency.runWriteActionAndWait
import com.android.tools.idea.editors.fast.FastPreviewBundle.message
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.time.withTimeout
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.jetbrains.kotlin.idea.util.module
import java.io.File
import java.time.Duration

/**
 * Maximum amount of time to wait for a fast compilation to happen.
 */
private val FAST_PREVIEW_COMPILE_TIMEOUT = java.lang.Long.getLong("fast.preview.daemon.compile.seconds.timeout", 30)

private suspend fun PsiFile.saveIfNeeded() {
  val vFile = virtualFile ?: return
  val fileManager = FileDocumentManager.getInstance()
  val document = fileManager.getCachedDocument(vFile) ?: return
  if (!fileManager.isDocumentUnsaved(document)) return
  runWriteActionAndWait {
    fileManager.saveDocument(document)
  }
}

/**
 * Starts a new fast compilation for the current file in the Preview and returns the result of the compilation.
 */
suspend fun fastCompile(parentDisposable: Disposable,
                        file: PsiFile,
                        fastPreviewManager: FastPreviewManager = FastPreviewManager.getInstance(file.project),
                        requestTracker: FastPreviewTrackerManager.Request = FastPreviewTrackerManager.getInstance(file.project).trackRequest()): CompilationResult = coroutineScope {
  val contextModule = file.module ?: throw Throwable("No module")
  val project = file.project

  val compileProgressIndicator = BackgroundableProcessIndicator(
    project,
    message("notification.compiling"),
    "",
    "",
    false
  )
  compileProgressIndicator.isIndeterminate = true
  Disposer.register(parentDisposable, compileProgressIndicator)
  try {
    compileProgressIndicator.start()

    file.saveIfNeeded()

    val (result, outputAbsolutePath) = withTimeout(Duration.ofSeconds(FAST_PREVIEW_COMPILE_TIMEOUT)) {
      fastPreviewManager.compileRequest(listOf(file), contextModule, tracker = requestTracker)
    }
    val isSuccess = result == CompilationResult.Success
    if (isSuccess) {
      ModuleClassLoaderOverlays.getInstance(contextModule).overlayPath = File(outputAbsolutePath).toPath()
    }

    return@coroutineScope result
  }
  catch (_: CancellationException) {
    return@coroutineScope CompilationResult.CompilationAborted()
  }
  catch (_: ProcessCanceledException) {
    return@coroutineScope CompilationResult.CompilationAborted()
  }
  finally {
    compileProgressIndicator.stop()
    compileProgressIndicator.processFinish()
  }
}