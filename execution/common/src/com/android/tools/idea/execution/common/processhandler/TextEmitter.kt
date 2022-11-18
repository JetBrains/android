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
package com.android.tools.idea.execution.common.processhandler

import com.intellij.openapi.util.Key

/**
 * An interface to emit a text message. An implementation class must be thread-safe.
 */
interface TextEmitter {
  /**
   * Emits [message] to a destination specified by [key].
   * Typically, [key] is [com.intellij.execution.process.ProcessOutputTypes.STDOUT] or
   * [com.intellij.execution.process.ProcessOutputTypes.STDERR].
   */
  fun emit(message: String, key: Key<*>)
}
