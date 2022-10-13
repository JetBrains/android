/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files;

import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.intellij.openapi.application.ModalityState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Executes the block in a write-safe scope in the current modality state.
 *
 * If this is called from a non-EDT thread, the "current" modality state cannot be observed, and
 * thus directly using the uiThread context results in the NON_MODAL state being used, preventing
 * execution until modal dialogs complete (see [ModalityState.defaultModalityState]).
 *
 * Thus, this first uses [ModalityState.any()] to transfer control to the EDT, allowing us to access
 * the current modality state, and then invokes the block with that modality state.
 *
 * We cannot just stay in [ModalityState.any()], because accessing VFS, PSI, etc. is not allowed
 * (see [ModalityState.any]).
 */
suspend fun <T> withWriteSafeContextWithCurrentModality(block: suspend CoroutineScope.() -> T): T =
  withContext(uiThread(ModalityState.any())) {
    withContext(uiThread, block)
  }

/**
 * Cancels the current coroutine, then throws CancellationException to immediately start
 * unwinding execution.
 */
suspend fun cancelAndThrow(): Nothing = coroutineScope {
  cancel()
  throw CancellationException()
}