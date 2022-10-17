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
package com.android.tools.idea.gradle.project.sync

/**
 * An instance of [ModelResult] represents a result of an operation together with any exceptions that have been suppressed.
 */
class ModelResult<out T : Any> private constructor (private val result: T?, val exceptions: List<Throwable> = emptyList()) {

  companion object {
    /**
     * Creates a new instance of [ModelResult] by running [block] and either recording the result or recording an exception if thrown.
     *
     * Additional exceptions can be recorded from [block] using methods of [Context].
     */
    fun <T : Any> create(block: Context.() -> T?): ModelResult<T> {
      val recorded = mutableSetOf<Throwable>()
      val context: Context = Context { recorded.addAll(it) }
      val mappedValue = kotlin.runCatching { context.block() }
      return ModelResult(mappedValue.getOrNull(), listOfNotNull(mappedValue.exceptionOrNull()) + recorded)
    }

    /**
     * If this [ModelResult] holds a value, transforms the value using [mapper] and returns a new [ModelResult] holding the result
     * and exceptions from both the original result and the transformation.
     *
     * Additional exceptions can be recorded from [mapper] using methods of [Context].
     */
    fun <T: Any, V : Any> ModelResult<T>.mapCatching(mapper: Context.(T) -> V): ModelResult<V> {
      return create {
        recordAndGet()?.let { mapper(it) }
      }
    }

    /**
     * If this [ModelResult] does not hold a value, transforms the value using [mapper] and returns a new [ModelResult] holding the result
     * and exceptions from both the original result and the transformation.
     *
     * Additional exceptions can be recorded from [mapper] using methods of [Context].
     */
    fun <T: Any> ModelResult<T>.mapNull(mapper: Context.() -> T?): ModelResult<T> {
      return create {
        recordAndGet() ?: mapper()
      }
    }

    /**
     * Returns the value of this [ModelResult].
     *
     * Note: [Context.recordAndGet] should be used in most cases.
     */
    fun <T : Any> ModelResult<T>.ignoreExceptionsAndGet(): T? = result
  }

  fun interface Context {
    /**
     * Records the [exceptions].
     */
    fun record(exceptions: List<Throwable>)

    /**
     * Records any exceptions that this [ModelResult] holds and returns its value.
     */
    fun <T : Any> ModelResult<T>.recordAndGet(): T? = also { record(exceptions) }.result

    /**
     * Executes [exception] and records the exception it throws.
     */
    fun recordException(exception: () -> Nothing) = kotlin.runCatching { exception() }.exceptionOrNull()?.let(::listOf)?.let(::record)
  }
}