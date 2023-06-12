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
package com.android.tools.profilers;

public enum ProfilerAspect {
  // The current stage of the profiler tools has changed
  STAGE,
  // The set of devices and processes has changed.
  PROCESSES,
  // The profile's preferred process has changed.
  PREFERRED_PROCESS,
  // The agent attach state has changed
  AGENT,
  // The active tooltip has changed
  TOOLTIP,
}
