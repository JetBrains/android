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

import com.android.utils.TraceUtils.getStackTrace
import com.android.utils.TraceUtils.simpleId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.util.application
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Module service responsible for managing background actions taken by [ResourceFolderRepository].
 */
class ResourceFolderRepositoryBackgroundActions : Disposable.Default {

  private val updateChannel: Channel<Pair<String, Runnable>> = Channel(Channel.UNLIMITED)

  private val wolfChannel: Channel<Runnable> = Channel(Channel.UNLIMITED)

  init {
    coroutineScope
      .launch(Dispatchers.Default) {
        supervisorScope {
          updateChannel.consumeEach { (repositorySimpleId, action) ->
            launch { doRunInUpdateQueue(repositorySimpleId, action) }.join()
          }
        }
      }
      .cancelOnDispose(this)

    coroutineScope
      .launch(Dispatchers.Default) {
        supervisorScope {
          wolfChannel.consumeEach { launch { blockingContext { it.run() } }.join() }
        }
      }
      .cancelOnDispose(this)
  }

  /**
   * Runs the given update action on [updateExecutor] in a read action. All update actions are
   * executed in the same order they were scheduled.
   */
  fun runInUpdateQueue(repository: Any, action: Runnable) {
    val repositorySimpleId = repository.simpleId
    ResourceUpdateTracer.log { "$repositorySimpleId.scheduleUpdate scheduling $action" }

    // trySend always succeeds since this channel is created with UNLIMITED.
    updateChannel.trySend(Pair(repositorySimpleId, action))
  }

  fun runInWolfQueue(action: Runnable) {
    // trySend always succeeds since this channel is created with UNLIMITED.
    wolfChannel.trySend(action)
  }

  private suspend fun doRunInUpdateQueue(repositorySimpleId: String, action: Runnable) {
    ResourceUpdateTracer.log { "$repositorySimpleId: Update $action started" }
    // coroutineToIndicator is needed because ResourceFolderRepository.scheduleScan puts an action
    // on the queue to run here that internally uses a progress indicator. The logic it runs can be
    // interrupted without going back to an indicator context, which leads to logical errors (ie,
    // resource files don't get scanned).
    coroutineToIndicator {
      try {
        ReadAction.nonBlocking(action).expireWith(this).executeSynchronously()
        ResourceUpdateTracer.log { "$repositorySimpleId: Update $action finished" }
      } catch (e: Throwable) {
        if (e is ProcessCanceledException) {
          ResourceUpdateTracer.log { "$repositorySimpleId: Update $action was canceled" }
        } else {
          ResourceUpdateTracer.log {
            "$repositorySimpleId: Update $action finished with exception $e\n${getStackTrace(e)}"
          }
          thisLogger().error(e)
        }
      }
    }
  }

  companion object {
    private val coroutineScope
      get() = application.service<ScopeService>().coroutineScope

    @JvmStatic
    fun getInstance(module: Module) = module.service<ResourceFolderRepositoryBackgroundActions>()

    @JvmStatic
    fun runInBackground(action: Runnable) {
      coroutineScope.launch(Dispatchers.Default) { supervisorScope { launch { action.run() } } }
    }
  }

  /**
   * Service that provides a coroutine scope to [ResourceFolderRepositoryBackgroundActions].
   *
   * The platform does not provide coroutine scopes to module services, so one can't be sent
   * directly to [ResourceFolderRepositoryBackgroundActions]. This service can be used instead,
   * since each module service can share the same scope and just use separate [Channel]s.
   */
  @Service private class ScopeService(val coroutineScope: CoroutineScope)
}
