/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.insights.model.stacktrace

/**
 * A container for the errors / exceptions / stacktraces which describe a fatal crash or a non-fatal
 * error or exception. Logged errors have only the errors field set; fatals have both the exceptions
 * & thread_stack fields set.
 */
data class StacktraceGroup(
  // Nested exceptions are broken up & represented as peers in this list. See 'nested' field.
  val exceptions: List<ExceptionStack> = listOf()
)
