/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.layoutlib.concurrency

import com.android.tools.idea.rendering.RenderService
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.internal.MainDispatcherFactory
import kotlinx.coroutines.internal.tryCreateDispatcher
import java.util.ServiceLoader
import java.util.concurrent.ExecutorService
import kotlin.coroutines.CoroutineContext

/**
 * [MainCoroutineDispatcher] dispatcher that ensures the `"Main thread"` is always the Layoutlib thread for co-routines initiated
 * from that thread.
 */
class LayoutlibMainCoroutineDispatcher(
  private val alternativeMainCoroutineDispatcher: MainCoroutineDispatcher?) : MainCoroutineDispatcher() {
  override val immediate: MainCoroutineDispatcher
    get() = this

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    if (RenderService.isCurrentThreadARenderThread()) {
      RenderService.getRenderAsyncActionExecutor().runAsyncAction(block)
    }
    else {
      alternativeMainCoroutineDispatcher?.dispatch(context, block) ?: throw IllegalStateException(
        "Module with the Main dispatcher is missing. " +
        "Add dependency providing the Main dispatcher, e.g. 'kotlinx-coroutines-android' " +
        "and ensure it has the same version as 'kotlinx-coroutines-core'")
    }
  }
}

/**
 * A factory to intercept main coroutine dispatcher for user code executed by layoutlib.
 */
@InternalCoroutinesApi
internal class DelegatingDispatcherFactory : MainDispatcherFactory {
  /**
   * We are using the highest priority to override all other [MainDispatcherFactory] implementations. We will delegate
   * to the next high priority one, unless we are in the Layoutlib Thread.
   */
  override val loadPriority = Int.MAX_VALUE

  override fun createDispatcher(allFactories: List<MainDispatcherFactory>): MainCoroutineDispatcher =
    LayoutlibMainCoroutineDispatcher(allFactories.firstOrNull { it !is DelegatingDispatcherFactory }?.createDispatcher(allFactories))
}
