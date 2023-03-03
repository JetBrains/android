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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test

class CompletableDeferredUtilsTest {
  @Test
  fun `completes for no elements`(): Unit = runBlocking {
    run {
      val completable = wrapCompletableDeferredCollection<Unit>(listOf())
      completable.complete(Unit)
    }

    run {
      val completable = wrapCompletableDeferredCollection<Unit>(listOf())
      completable.completeExceptionally(Throwable())
    }
  }

  @Test
  fun `completes normally for a single element`() = runBlocking {
    val completableCollection = listOf(CompletableDeferred<Unit>())

    val completable = wrapCompletableDeferredCollection(completableCollection)
    completable.complete(Unit)
    completableCollection.forEach {
      assertNotNull(it.getCompletedOrNull())
    }
  }

  @Test
  fun `completes exceptionally for a single element`() = runBlocking {
    val completableCollection = listOf(CompletableDeferred<Unit>())

    val completable = wrapCompletableDeferredCollection(completableCollection)
    completable.completeExceptionally(Throwable())
    completableCollection.forEach {
      assertNotNull(it.getCompletionExceptionOrNull())
    }
  }

  @Test
  fun `completes normally for a collection`() = runBlocking {
    val completableCollection = listOf<CompletableDeferred<Unit>>(
      CompletableDeferred(),
      CompletableDeferred(),
      CompletableDeferred()
    )

    val completable = wrapCompletableDeferredCollection(completableCollection)
    completable.complete(Unit)
    completableCollection.forEach {
      assertNotNull(it.getCompletedOrNull())
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `completes exceptionally for a collection`() = runBlocking {
    val completableCollection = listOf<CompletableDeferred<Unit>>(
      CompletableDeferred(),
      CompletableDeferred(),
      CompletableDeferred()
    )

    val completable = wrapCompletableDeferredCollection(completableCollection)
    completable.completeExceptionally(Throwable())
    completableCollection.forEach {
      assertNotNull(it.getCompletionExceptionOrNull())
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `completing one child completes all`() = runBlocking {
    val completableCollection = listOf<CompletableDeferred<Unit>>(
      CompletableDeferred(),
      CompletableDeferred(),
      CompletableDeferred()
    )

    val completable = wrapCompletableDeferredCollection(completableCollection)
    completableCollection.last().complete(Unit)
    completableCollection.forEach {
      assertNotNull(it.getCompletedOrNull())
    }
    assertNotNull(completable.getCompletedOrNull())
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `completing exceptionally one child completes exceptionally all`() = runBlocking {
    val completableCollection = listOf<CompletableDeferred<Unit>>(
      CompletableDeferred(),
      CompletableDeferred(),
      CompletableDeferred()
    )

    val completable = wrapCompletableDeferredCollection(completableCollection)
    completableCollection.last().completeExceptionally(Throwable())
    completableCollection.forEach {
      assertNotNull(it.getCompletionExceptionOrNull())
    }
    assertNotNull(completable.getCompletionExceptionOrNull())
  }
}