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
package com.android.tools.idea.preview.fast

import com.android.tools.compile.fast.CompilationResult
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.UniqueTaskCoroutineLauncher
import com.android.tools.idea.editors.build.PsiCodeFileOutOfDateStatusReporter
import com.android.tools.idea.preview.lifecycle.PreviewLifecycleManager
import com.android.tools.idea.preview.mvvm.PreviewViewModelStatus
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtFile

/** Interface to be implemented by surfaces (like the Preview) that support FastPreview. */
interface FastPreviewSurface {
  /**
   * Request a fast preview refresh. The result [Deferred] will contain the result of the
   * compilation or the method will return [CompilationResult.CompilationAborted] if the compilation
   * request could not be scheduled (e.g. the code has syntax errors).
   */
  fun requestFastPreviewRefreshAsync(): Deferred<CompilationResult>

  companion object {
    val KEY = DataKey.create<FastPreviewSurface>("FastPreviewSurface")
  }
}

/**
 * Default implementation of [FastPreviewSurface], that triggers a fast preview compilation via
 * [requestFastPreviewRefreshAndTrack], and delegates the refresh after a successful compilation to
 * [delegateRefresh].
 */
class CommonFastPreviewSurface(
  parentDisposable: Disposable,
  private val lifecycleManager: PreviewLifecycleManager,
  private val psiFilePointer: SmartPsiElementPointer<PsiFile>,
  private val previewStatusProvider: () -> PreviewViewModelStatus,
  private val delegateRefresh: suspend () -> Unit,
) : Disposable, FastPreviewSurface {

  private val myPsiCodeFileOutOfDateStatusReporter =
    PsiCodeFileOutOfDateStatusReporter.getInstance(psiFilePointer.project)

  private val coroutineScope: CoroutineScope

  init {
    Disposer.register(parentDisposable, this)
    coroutineScope = AndroidCoroutineScope(this)
  }

  /**
   * [UniqueTaskCoroutineLauncher] for ensuring that only one fast preview request is launched at a
   * time.
   */
  private val fastPreviewCompilationLauncher: UniqueTaskCoroutineLauncher by
    lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
      UniqueTaskCoroutineLauncher(coroutineScope, "Compilation Launcher")
    }

  override fun requestFastPreviewRefreshAsync(): Deferred<CompilationResult> =
    lifecycleManager.executeIfActive { async { requestFastPreviewRefreshSync() } }
      ?: CompletableDeferred(CompilationResult.CompilationAborted())

  /**
   * Request a fast preview compilation, followed by preview refresh when the compilation is
   * successful. Note that this method waits for the compilation to complete, returning its result,
   * but it doesn't wait for the refresh to finish.
   */
  suspend fun requestFastPreviewRefreshSync(): CompilationResult {
    val previewFile =
      readAction { psiFilePointer.element }
        ?: return CompilationResult.RequestException(
          IllegalStateException("Preview File is not valid")
        )
    val previewFileModule =
      readAction { previewFile.module }
        ?: return CompilationResult.RequestException(
          IllegalStateException("Preview File does not have a valid module")
        )
    val outOfDateFiles =
      myPsiCodeFileOutOfDateStatusReporter.outOfDateFiles
        .filterIsInstance<KtFile>()
        .filter { modifiedFile ->
          if (modifiedFile.isEquivalentTo(previewFile)) return@filter true
          val modifiedFileModule = readAction { modifiedFile.module } ?: return@filter false

          // Keep the file if the file is from this module or from a module we depend on
          modifiedFileModule == previewFileModule ||
            ModuleManager.getInstance(psiFilePointer.project)
              .isModuleDependent(previewFileModule, modifiedFileModule)
        }
        .toSet()

    // Nothing to compile
    if (outOfDateFiles.isEmpty()) return CompilationResult.Success

    return requestFastPreviewRefreshAndTrack(
      parentDisposable = this,
      previewFileModule,
      outOfDateFiles,
      previewStatusProvider(),
      fastPreviewCompilationLauncher,
    ) { outputAbsolutePath ->
      ModuleClassLoaderOverlays.getInstance(previewFileModule)
        .pushOverlayPath(File(outputAbsolutePath).toPath())
      delegateRefresh()
    }
  }

  override fun dispose() {}
}
