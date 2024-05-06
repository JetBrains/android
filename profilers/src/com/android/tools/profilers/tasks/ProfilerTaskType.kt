/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers.tasks

enum class ProfilerTaskType(val description: String) {
  UNSPECIFIED("Unspecified"),
  CALLSTACK_SAMPLE("Callstack Sample"),
  SYSTEM_TRACE("System Trace"),
  JAVA_KOTLIN_METHOD_TRACE("Java/Kotlin Method Trace"),
  JAVA_KOTLIN_METHOD_SAMPLE("Java/Kotlin Method Sample (legacy)"),
  HEAP_DUMP("Heap Dump"),
  NATIVE_ALLOCATIONS("Native Allocations"),
  JAVA_KOTLIN_ALLOCATIONS("Java/Kotlin Allocations"),
  LIVE_VIEW("Live View")
}