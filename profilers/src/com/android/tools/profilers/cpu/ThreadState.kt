/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.cpu

/**
 * CPU profiler's abstraction of thread states w/ capture state info.
 */
enum class ThreadState(val displayName: String, val isCaptured: Boolean = false) {
  RUNNING("Running"),
  RUNNING_CAPTURED("Running", true),
  SLEEPING("Sleeping"),
  SLEEPING_CAPTURED("Sleeping", true),
  DEAD("Dead"),
  DEAD_CAPTURED("Dead", true),
  WAITING("Waiting"),
  WAITING_CAPTURED("Waiting", true),

  // These values are captured from Atrace as such we only have a captured state.
  NO_ACTIVITY("No thread activity", true),  // Perfetto's empty state.
  RUNNABLE_CAPTURED("Runnable", true),
  WAITING_IO_CAPTURED("Waiting on IO", true),

  UNKNOWN("Unknown");
}
