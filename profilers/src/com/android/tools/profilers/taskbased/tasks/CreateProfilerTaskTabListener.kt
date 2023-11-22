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
package com.android.tools.profilers.taskbased.tasks

import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.args.TaskArgs
import com.intellij.util.messages.Topic

/**
 * Listener of events requesting that a Profiler tab for a specific task be created.
 */
fun interface CreateProfilerTaskTabListener {

  /**
   * Creates and opens a Profiler tab for a specific task.
   *
   * @param taskType The [ProfilerTaskType] that should be opened.
   * @param args A serialized representation of the arguments needed to open the requested task.
   */
  fun createProfilerTaskTab(taskType: ProfilerTaskType, args: TaskArgs?)

  companion object {
    @JvmField
    val TOPIC = Topic("Command to create a Profiler tab for a specific task", CreateProfilerTaskTabListener::class.java)
  }
}