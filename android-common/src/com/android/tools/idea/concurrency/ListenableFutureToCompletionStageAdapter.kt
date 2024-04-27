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
package com.android.tools.idea.concurrency

import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.CompletableFuture

internal class ListenableFutureToCompletionStageAdapter<T>(val wrapped: ListenableFuture<T>) : CompletableFuture<T>(), FutureCallback<T> {
  init {
    Futures.addCallback(wrapped, this, MoreExecutors.directExecutor());
  }

  override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
    // Note that we store these in local variables rather than just writing this as one "&&" expression since
    // we want to unconditionally call both cancel methods even if the first one returns false.
    val result1= wrapped.cancel(mayInterruptIfRunning)
    val result2= super.cancel(mayInterruptIfRunning)
    return result1 && result2
  }

  override fun onSuccess(result: T?) {
    complete(result)
  }

  override fun onFailure(t: Throwable) {
    completeExceptionally(t)
  }
}
