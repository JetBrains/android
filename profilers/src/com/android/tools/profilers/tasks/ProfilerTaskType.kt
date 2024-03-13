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

enum class ProfilerTaskType(val description: String, val prefersProfileable: Boolean, val rank: Int) {
  UNSPECIFIED("Unspecified", false, Int.MAX_VALUE),
  CALLSTACK_SAMPLE("Callstack Sample", true, 2),
  SYSTEM_TRACE("System Trace", true, 0),
  JAVA_KOTLIN_METHOD_TRACE("Java/Kotlin Method Trace", true, 4),
  JAVA_KOTLIN_METHOD_SAMPLE("Java/Kotlin Method Sample (legacy)", true, 6),
  HEAP_DUMP("Heap Dump", false, 1),
  NATIVE_ALLOCATIONS("Native Allocations", true, 5),
  JAVA_KOTLIN_ALLOCATIONS("Java/Kotlin Allocations", false, 3),
  LIVE_VIEW("Live View", true, 7)
}