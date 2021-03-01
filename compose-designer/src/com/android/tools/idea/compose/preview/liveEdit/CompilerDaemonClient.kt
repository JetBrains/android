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
 * Interface to implement by specific implementations that can talk to compiler daemons.
 */
interface CompilerDaemonClient : Disposable {
  /**
   * Returns if this daemon is running. If not, no compileRequests will be handled.
   */
  val isRunning: Boolean

  /**
   * Sends the given compilation requests and returns if it was successful.
   */
  suspend fun compileRequest(args: List<String>): Boolean
}