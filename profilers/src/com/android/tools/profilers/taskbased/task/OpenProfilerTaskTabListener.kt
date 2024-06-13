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
package com.android.tools.profilers.taskbased.task

import com.intellij.util.messages.Topic

/**
 * Listener of events requesting that a Profiler tab for an existing task be opened.
 */
fun interface OpenProfilerTaskTabListener {

  /**
   * Opens an existing Profiler task tab. There is at most one existing task tab at any time that can be opened.
   */
  fun openProfilerTaskTab()

  companion object {
    @JvmField
    val TOPIC = Topic("Command to open an existing Profiler task tab", OpenProfilerTaskTabListener::class.java)
  }
}