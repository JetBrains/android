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
package com.android.tools.idea.editors.fast

import com.android.tools.compile.fast.CompilationResult
import com.android.tools.idea.concurrency.disposableCallbackFlow
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Returns a [Flow] that will notify when a change in the compilation status happens.
 *
 * [onReady] will be called once the flow is ready to start processing values. Useful when using the flow in tests to ensure no elements
 * are lost.
 */
fun fastPreviewCompileFlow(
  project: Project,
  parentDisposable: Disposable,
  fastPreviewManager: FastPreviewManager = FastPreviewManager.getInstance(project),
  onReady: () -> Unit = {},
): Flow<Boolean> =
  disposableCallbackFlow<Boolean>("FastPreviewCompileFlow", parentDisposable = parentDisposable) {
      val listener =
        object : FastPreviewManager.Companion.FastPreviewManagerListener {
          override fun onCompilationStarted(files: Collection<PsiFile>) {
            trySend(true)
          }

          override fun onCompilationComplete(
            result: CompilationResult,
            files: Collection<PsiFile>
          ) {
            trySend(false)
          }
        }
      fastPreviewManager.addListener(disposable, listener)

      trySend(fastPreviewManager.isCompiling)
      onReady()
    }
    .distinctUntilChanged()
