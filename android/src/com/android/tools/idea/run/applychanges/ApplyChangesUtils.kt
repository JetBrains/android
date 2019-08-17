/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.applychanges

import com.android.tools.idea.run.AndroidRunState
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.util.SwapInfo
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.execution.ui.RunContentManagerImpl
import com.intellij.openapi.diagnostic.Logger

/**
 * Finds the existing session and returns them as [ExistingSession] if present.
 *
 * If the session is applicable for hot-swap, it returns an existing [ProcessHandler] and optionally [ExecutionConsole] to be used.
 * Otherwise the session needs to cold-swap. This method may show a popup-window and asks a user to continue. If a user decides to
 * continue, it detaches the existing session and this method returns empty [ExistingSession]. If a user choose to cancel, it throws
 * [ApplyChangesCancelledException].
 */
@Throws(ApplyChangesCancelledException::class)
fun findExistingSessionAndMaybeDetachForColdSwap(env: ExecutionEnvironment, devices: DeviceFutures): ExistingSession {
  val prevHandler = env.findExistingProcessHandler(devices) ?: return ExistingSession()
  val manager = RunContentManager.getInstance(env.project)
  val descriptor = manager.allDescriptors.asSequence()
    .find { descriptor -> descriptor.processHandler === prevHandler }
  val swapInfo = env.getUserData(SwapInfo.SWAP_INFO_KEY)
  if (swapInfo == null) {
    detachExistingSessionForColdSwap(prevHandler, descriptor, manager)
    return ExistingSession()
  }
  return ExistingSession(prevHandler, descriptor?.executionConsole)
}

/**
 * A data holder of the existing session.
 */
data class ExistingSession(
  val processHandler: ProcessHandler? = null,
  val executionConsole: ExecutionConsole? = null
)

/**
 * An exception thrown when a user decides to cancel apply-change execution.
 */
class ApplyChangesCancelledException : Exception()

/**
 * Detaches the existing session so that we don't end up with having 2 run tabs for the same launch (the existing one and the new one).
 */
@Throws(ApplyChangesCancelledException::class)
private fun detachExistingSessionForColdSwap(handler: ProcessHandler,
                                             descriptor: RunContentDescriptor?,
                                             manager: RunContentManager) {
  if (descriptor != null) {
    val previousContent = descriptor.attachedContent
    if (previousContent == null) {
      Logger.getInstance(AndroidRunState::class.java).warn("Descriptor without content.")
    }
    else {
      val previousExecutor = RunContentManagerImpl.getExecutorByContent(previousContent)
      if (previousExecutor == null) {
        Logger.getInstance(AndroidRunState::class.java).warn("No executor found for content")
      }
      else if (!manager.removeRunContent(previousExecutor, descriptor)) {
        // In case there's an existing handler, it could pop up a dialog prompting the user to confirm. If the user
        // cancels, removeRunContent will return false. In such case, stop the run.
        throw ApplyChangesCancelledException()
      }
    }
  }
  handler.detachProcess()
}