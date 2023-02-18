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
package com.android.tools.idea.insights

import com.android.tools.idea.insights.LoadingState.Failure
import com.android.tools.idea.insights.LoadingState.Loading
import com.android.tools.idea.insights.LoadingState.Ready
import com.android.tools.idea.insights.LoadingState.Unauthorized
import com.android.tools.idea.insights.LoadingState.UnknownFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

/**
 * A generic container of values that may not yet be ready with a possibility of failure.
 *
 * It can be one of the following states:
 *
 * * [Loading]: value is currently being loaded.
 * * [Ready]: the value is ready to be used.
 * * [Failure]: value failed to load for any of the below reasons:
 * * [Unauthorized]: the user does not have sufficient access.
 * * [UnknownFailure]: any other failure, e.g. network failure.
 */
sealed class LoadingState<out T> {

  /** The value is being loaded and is not yet available. */
  object Loading : LoadingState<Nothing>() {
    override fun <U> map(fn: (Nothing) -> U): Loading {
      return this
    }

    override fun toString(): String = "LoadingState.Loading"
  }

  /** Loading has completed with either a [success][Ready] or a [Failure]. */
  sealed class Done<out T> : LoadingState<T>()

  /** The value is ready to be used. */
  data class Ready<out T>(val value: T) : Done<T>() {
    override fun <U> map(fn: (T) -> U): Ready<U> {
      return Ready(fn(value))
    }
  }

  /** Loading the value failed, see subclasses for possible failure reasons. */
  sealed class Failure : Done<Nothing>() {
    abstract val message: String?
    open val cause: Throwable? = null
  }

  /** Currently signed-in user does not have sufficient access. */
  data class Unauthorized(
    override val message: String?,
    override val cause: Throwable? = null,
  ) : Failure() {
    override fun <U> map(fn: (Nothing) -> U): Unauthorized {
      return this
    }
  }

  /** Encountered network failure while fetching issues. */
  data class NetworkFailure(override val message: String?, override val cause: Throwable? = null) :
    Failure() {
    override fun <U> map(fn: (Nothing) -> U): NetworkFailure {
      return this
    }
  }

  /** Insufficient permissions while fetching issues or updating issues. */
  data class PermissionDenied(
    override val message: String?,
    override val cause: Throwable? = null
  ) : Failure() {
    override fun <U> map(fn: (Nothing) -> U): PermissionDenied {
      return this
    }
  }

  /** Generic(catch all) failure. */
  data class UnknownFailure(
    override val message: String?,
    override val cause: Throwable? = null,
  ) : Failure() {
    override fun <U> map(fn: (Nothing) -> U): UnknownFailure {
      return this
    }
  }

  /**
   * If the value is [Ready], returns a new [Ready] with the value of type [U] obtained by
   * transforming the original [T] value with [fn].
   */
  abstract fun <U> map(fn: (T) -> U): LoadingState<U>
}

fun <T, U> Flow<LoadingState<T>>.mapReady(fn: (T) -> U): Flow<LoadingState<U>> = map { it.map(fn) }

fun <T> Flow<LoadingState<T>>.filterReady(): Flow<T> {
  return filterIsInstance<Ready<T>>().map { it.value }
}
