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
package com.android.tools.compile.fast

/**
 * Class that represents the result of a compilation request.
 */
sealed class CompilationResult {
  /**
   * The compilation was successful.
   */
  object Success: CompilationResult()

  /**
   * Failure [CompilationResult] containing an optional [Throwable].
   */
  sealed interface WithThrowable {
    val e: Throwable?
  }

  /**
   * The daemon failed to start.
   */
  data class DaemonStartFailure(override val e: Throwable? = null): CompilationResult(), WithThrowable

  /**
   * The daemon returned an error. [code] contains the error code returned by the daemon.
   */
  data class DaemonError(val code: Int): CompilationResult()

  /**
   * An expected compilation error caused by normal user actions. For example, a syntax error would trigger
   * this. This is recoverable by the user updating the code.
   */
  data class CompilationError(override val e: Throwable? = null): CompilationResult(), WithThrowable

  /**
   * An exception happened while trying to execute the request. This could have happened before the request arrive to the daemon.
   * [e] will contain the exception if available.
   */
  data class RequestException(override val e: Throwable? = null): CompilationResult(), WithThrowable

  /**
   * The compilation request was aborted. This usually means it was cancelled by a condition that can be retried.
   */
  data class CompilationAborted(override val e: Throwable? = null): CompilationResult(), WithThrowable
}

/**
 * True if this is a [CompilationResult.Success].
 */
val CompilationResult.isSuccess: Boolean
  get() = this == CompilationResult.Success

/**
 * True if this is not a success result. [isSuccess] will be false.
 */
val CompilationResult.isError: Boolean
  get() = !isSuccess