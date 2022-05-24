/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.liveEdit

import com.intellij.openapi.Disposable

/**
 * Class that represents the result of a compilation request.
 */
sealed class CompilationResult {
  /**
   * The compilation was successful.
   */
  object Success: CompilationResult()

  /**
   * The daemon returned an error. [code] contains the error code returned by the daemon.
   */
  data class DaemonError(val code: Int): CompilationResult()

  /**
   * An exception happened while trying to execute the request. This could have happened before the request arrive to the daemon.
   * [e] will contain the exception if available.
   */
  data class RequestException(val e: Throwable? = null): CompilationResult()
}
/**
 * Interface to implement by specific implementations that can talk to compiler daemons.
 */
interface CompilerDaemonClient : Disposable {
  /**
   * Returns if this daemon is running. If not, no compileRequests will be handled.
   */
  val isRunning: Boolean

  /**
   * Sends the given compilation requests and returns a [CompilationResult] indicating the result.
   */
  suspend fun compileRequest(args: List<String>): CompilationResult
}