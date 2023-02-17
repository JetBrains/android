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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

private fun <T> CompletableDeferred<T>.completeFromOtherDeferred(other: Deferred<T>) =
  other.invokeOnCompletion { throwable ->
    if (!isCompleted) {
      if (throwable == null)
        complete(other.getCompleted())
      else
        completeExceptionally(throwable)
    }
  }

/**
 * Wraps all the given [collection] [CompletableDeferred]s into one. When the returned one completes, all the [CompletableDeferred]s
 * in the collection will also complete.
 * When the returned completable completes or any of the collection elements completes, all of them will be completed.
 *
 * This method might return the same [CompletableDeferred] if only one is passed.
 */
fun <T> wrapCompletableDeferredCollection(collection: Collection<CompletableDeferred<T>>): CompletableDeferred<T> =
  when {
    collection.isEmpty() -> CompletableDeferred()
    collection.size == 1 -> collection.single()
    else -> CompletableDeferred<T>().also { parentDeferred ->
      collection.forEach { singleCompletableDeferred ->
        singleCompletableDeferred.completeFromOtherDeferred(parentDeferred)
        parentDeferred.completeFromOtherDeferred(singleCompletableDeferred)
      }
    }
  }