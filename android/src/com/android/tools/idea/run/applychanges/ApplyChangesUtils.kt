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
 * Otherwise the session needs to cold-swap. In the case of cold-swap and if a matching existing session exists, it will terminate
 * its [ProcessHandler] and detach its [com.intellij.ui.content.Content]
 */
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
 * Detaches the existing session so that we don't end up with having 2 run tabs for the same launch (the existing one and the new one).
 */
private fun detachExistingSessionForColdSwap(handler: ProcessHandler,
                                             descriptor: RunContentDescriptor?,
                                             manager: RunContentManager) {
  // Detach the process first so that removeRunContent doesn't try to prompt the user to disconnect.
  handler.detachProcess()
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
      else {
        // We ignore the return value because in most cases it should return true since we already detached the process above.
        // In all other cases the Content isn't valid, in which case we just ignore it since it's already in the intended state.
        manager.removeRunContent(previousExecutor, descriptor)
      }
    }
  }
}