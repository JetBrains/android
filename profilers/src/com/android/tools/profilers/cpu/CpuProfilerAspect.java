/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

public enum CpuProfilerAspect {
  // The capture state (e.g. capturing, parsing, set new capture) has changed.
  CAPTURE,
  // The threads selection has changed.
  SELECTED_THREADS,
  // The capture details has changed, e.g the user selected "Bottom Up" or "Top Down" tab.
  CAPTURE_DETAILS,
  // The profiling mode (i.e. sampled or instrumented) has changed
  PROFILING_MODE,
  // Clock type (i.e. wall or thread) has changed.
  CLOCK_TYPE,
}
