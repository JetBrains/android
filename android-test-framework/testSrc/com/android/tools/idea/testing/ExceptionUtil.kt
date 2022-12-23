/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.testing

import com.intellij.util.containers.ConcurrentList
import com.intellij.util.containers.ContainerUtil
import org.junit.runners.model.MultipleFailureException

interface AggregateAndThrowIfAnyContext {
  fun recordResult(result: Result<*>)
}

inline fun <T> AggregateAndThrowIfAnyContext.runCatchingAndRecord(body: () -> T): Result<T> {
  return runCatching { body() }.also { recordResult(it) }
}

/*
Runs [body], captures & suppresses any exceptions thrown from nested `runCatchingAndRecord { }` blocks and, if any has been captured, throws
an aggregate exception in the end. Exceptions thrown directly from the [body] are not suppressed, however they are still aggregated with
any previously suppressed exceptions.
 */
inline fun <T> aggregateAndThrowIfAny(body: AggregateAndThrowIfAnyContext.() -> T) {
  val exceptions: ConcurrentList<Throwable> = ContainerUtil.createConcurrentList()
  val context = object : AggregateAndThrowIfAnyContext {
    override fun recordResult(result: Result<*>) {
      result.exceptionOrNull()?.let(exceptions::add)
    }
  }
  context.runCatchingAndRecord { body(context) }

  when {
    exceptions.isEmpty() -> Unit
    exceptions.size == 1 -> throw exceptions.single()
    else -> throw MultipleFailureException(exceptions)
  }
}
