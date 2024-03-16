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

import com.android.tools.compile.fast.CompilationResult
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiFile
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.runBlocking

/** A CompilerDaemonClient that blocks until [completeOneRequest] is called. */
class BlockingDaemonClient : CompilerDaemonClient {
  override val isRunning: Boolean = true

  private val pendingRequests = Channel<CompletableDeferred<Unit>>(Channel.UNLIMITED)
  private val _requestReceived = AtomicLong(0)
  val firstRequestReceived = CompletableDeferred<Unit>()
  val requestReceived: Long
    get() = _requestReceived.get()

  override suspend fun compileRequest(
    files: Collection<PsiFile>,
    module: Module,
    outputDirectory: Path,
    indicator: ProgressIndicator
  ): CompilationResult {
    _requestReceived.incrementAndGet()
    firstRequestReceived.complete(Unit)
    return try {
      val request = CompletableDeferred<Unit>()
      pendingRequests.send(request)

      request.await()
      CompilationResult.Success
    } catch (_: CancellationException) {
      CompilationResult.CompilationAborted()
    }
  }

  /**
   * Completes one pending request. If there are no requests pending, the method will block until one arrives.
   */
  fun completeOneRequest() = runBlocking {
    pendingRequests.receive().complete(Unit)
  }

  override fun dispose() {}
}
