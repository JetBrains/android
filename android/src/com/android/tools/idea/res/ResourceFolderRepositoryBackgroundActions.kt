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
package com.android.tools.idea.res

import com.android.annotations.concurrency.GuardedBy
import com.android.utils.TraceUtils.getStackTrace
import com.android.utils.TraceUtils.simpleId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.util.application
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.ArrayDeque
import java.util.Deque
import java.util.concurrent.RejectedExecutionException

/** Class responsible for managing background actions taken by [ResourceFolderRepository]. */
class ResourceFolderRepositoryBackgroundActions(private val parentDisposable: Disposable) :
  Disposable {

  init {
    Disposer.register(parentDisposable, this)
  }

  @GuardedBy("updateQueue") private val updateQueue: Deque<Pair<Runnable, String>> = ArrayDeque()

  private val updateExecutor =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("ResourceFolderRepository", 1)

  private val wolfQueue =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("ResourceFolderRepositoryWolfQueue", 1)

  /**
   * Runs the given update action on [updateExecutor] in a read action. All update actions are
   * executed in the same order they were scheduled.
   */
  fun scheduleUpdate(repository: Any, updateAction: Runnable) {
    val repositorySimpleId = repository.simpleId
    ResourceUpdateTracer.log { "$repositorySimpleId.scheduleUpdate scheduling $updateAction" }

    synchronized(updateQueue) {
      val wasEmpty = updateQueue.isEmpty()
      updateQueue.add(Pair(updateAction, repositorySimpleId))
      if (!wasEmpty) return
    }

    try {
      updateExecutor.execute {
        while (true) {
          val (action, repositorySimpleId) =
            synchronized(updateQueue) { updateQueue.poll() } ?: return@execute

          ResourceUpdateTracer.log { "$repositorySimpleId: Update $action started" }
          try {
            ReadAction.nonBlocking(action).expireWith(parentDisposable).executeSynchronously()
            ResourceUpdateTracer.log { "$repositorySimpleId: Update $action finished" }
          } catch (e: ProcessCanceledException) {
            ResourceUpdateTracer.log { "$repositorySimpleId: Update $action was canceled" }
            // The current update action has been canceled. Proceed to the next one in the queue.
          } catch (e: Throwable) {
            ResourceUpdateTracer.log {
              "$repositorySimpleId: Update $action finished with exception $e\n${getStackTrace(e)}"
            }
            thisLogger().error(e)
          }
        }
      }
    } catch (ignore: RejectedExecutionException) {
      // The executor has been shut down.
    }
  }

  fun submitToWolfQueue(task: Runnable) {
    wolfQueue.submit(task)
  }

  override fun dispose() {
    updateExecutor.shutdownNow()
    wolfQueue.shutdownNow()
  }

  companion object {
    @JvmStatic
    fun executeOnPooledThread(action: Runnable) {
      application.executeOnPooledThread(action)
    }
  }
}
