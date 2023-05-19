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
@file:JvmName("FutureTestUtils")
package com.android.tools.idea.concurrency

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

fun <V> pumpEventsAndWaitForFutures(futures: List<ListenableFuture<V>>): List<V> {
  return pumpEventsAndWaitForFuture(Futures.allAsList(futures))
}

fun <V> pumpEventsAndWaitForFuture(future: ListenableFuture<V>): V {
  return try {
    pumpEventsAndWaitForFuture(future, TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
  }
  catch (e: Exception) {
    throw RuntimeException(e)
  }
}

fun <V> pumpEventsAndWaitForFutureException(future: ListenableFuture<V>): Throwable {
  return try {
    pumpEventsAndWaitForFuture(future, TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    throw RuntimeException("Expected ExecutionException from future, got value instead")
  }
  catch (e: ExecutionException) {
    e
  }
  catch (t: Throwable) {
    throw RuntimeException("Expected ExecutionException from future, got Throwable instead", t)
  }
}

fun <V> pumpEventsAndWaitForFutureCancellation(future: ListenableFuture<V>): CancellationException {
  return try {
    pumpEventsAndWaitForFuture(future, TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    throw RuntimeException("Expected CancellationException from future, got value instead")
  }
  catch (e: CancellationException) {
    e
  }
  catch (t: Throwable) {
    throw RuntimeException("Expected CancellationException from future, got Throwable instead", t)
  }
}

private const val TIMEOUT_MILLISECONDS: Long = 30_000
