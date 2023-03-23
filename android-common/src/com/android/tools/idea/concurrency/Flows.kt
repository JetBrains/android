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
package com.android.tools.idea.concurrency

import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.ProblemListener
import kotlinx.coroutines.launch

sealed class SyntaxErrorUpdate(val file: VirtualFile) {
  /** The [file] has now errors. */
  class Appeared(file: VirtualFile) : SyntaxErrorUpdate(file)

  /** The errors in [file] have changed. */
  class Changed(file: VirtualFile) : SyntaxErrorUpdate(file)

  /** The [file] does not have errors anymore. */
  class Disappeared(file: VirtualFile) : SyntaxErrorUpdate(file)
}

/**
 * A [Flow] for the [ProblemListener] callback. It will emit a new value for every change in the
 * problem status with the file that changed an one of [SyntaxErrorUpdate].
 */
fun syntaxErrorFlow(
  project: Project,
  parentDisposable: Disposable,
  logger: Logger? = null,
  onConnected: (() -> Unit)? = null
) =
  disposableCallbackFlow<SyntaxErrorUpdate>("SyntaxErrorFlow", logger, parentDisposable) {
    project.messageBus
      .connect(disposable)
      .subscribe(
        ProblemListener.TOPIC,
        object : ProblemListener {
          override fun problemsAppeared(file: VirtualFile) {
            trySend(SyntaxErrorUpdate.Appeared(file))
          }

          override fun problemsChanged(file: VirtualFile) {
            trySend(SyntaxErrorUpdate.Changed(file))
          }

          override fun problemsDisappeared(file: VirtualFile) {
            trySend(SyntaxErrorUpdate.Disappeared(file))
          }
        }
      )

    onConnected?.let { launch(workerThread) { it() } }
  }
