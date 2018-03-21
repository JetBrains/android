/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.google.common.base.Function
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.JdkFutureAdapters
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import java.util.concurrent.Executor
import java.util.concurrent.Future

/**
 * Wrapper function to apply transform function to ListenableFuture after it get done
 */
fun <I, O> ListenableFuture<I>.transform(executor: Executor = directExecutor(), func: (I) -> O): ListenableFuture<O> {
  return Futures.transform(this, Function<I, O> { i -> func(i!!) }, executor)
}

/**
 * Wrapper function to convert Future to ListenableFuture
 */
fun <I> Future<I>.listenInPoolThread(executor: Executor = directExecutor()): ListenableFuture<I> {
  return JdkFutureAdapters.listenInPoolThread(this, executor)
}

fun <I> List<Future<I>>.listenInPoolThread(executor: Executor = directExecutor()): List<ListenableFuture<I>> {
  return this.map { future: Future<I> -> future.listenInPoolThread(executor) }
}

/**
 * Wrapper function to convert convert a list of ListenableFuture<I> to a ListenableFuture whose value is a list containing the
 * values of all its successful input futures. The fail/ cancelled task would be filled as null
 */
fun <I> List<ListenableFuture<I>>.successfulAsList(): ListenableFuture<List<I?>> {
  return Futures.successfulAsList(this)
}

fun <I> List<ListenableFuture<I>>.whenAllComplete(): Futures.FutureCombiner<I?> {
  return Futures.whenAllComplete(this)
}

/**
 * Wrapper function to add callback for a ListenableFuture
 */
fun <I> ListenableFuture<I>.addCallback(executor: Executor = directExecutor(), success: (I?) -> Unit, failure: (Throwable?) -> Unit) {
  addCallback(executor, object : FutureCallback<I> {
    override fun onFailure(t: Throwable?) {
      failure(t)
    }

    override fun onSuccess(result: I?) {
      success(result)
    }
  })
}

/**
 * Wrapper function to add callback for a ListenableFuture
 */
fun <I> ListenableFuture<I>.addCallback(executor: Executor = directExecutor(), futureCalback: FutureCallback<I>) {
  Futures.addCallback(this, futureCalback, executor)
}
