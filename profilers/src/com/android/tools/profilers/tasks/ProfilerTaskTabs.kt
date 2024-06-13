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

import com.android.tools.profilers.taskbased.task.CreateProfilerTaskTabListener
import com.android.tools.profilers.taskbased.task.OpenProfilerTaskTabListener
import com.android.tools.profilers.tasks.args.TaskArgs
import com.intellij.openapi.project.Project

/**
 * Helper for opening Profiler tabs.
 */
object ProfilerTaskTabs {

  /**
   * Creates and opens a Profiler task tab for a specific task.
   *
   * @param project The project associated with the current Android Studio instance.
   * @param taskType The [ProfilerTaskType] that should be opened.
   * @param args A serialized representation of the arguments needed to open the requested task.
   */
  fun create(project: Project, taskType: ProfilerTaskType, args: TaskArgs) {
    project.messageBus.syncPublisher(CreateProfilerTaskTabListener.TOPIC).createProfilerTaskTab(taskType, args)
  }

  /**
   * Opens an existing Profiler task tab. There is at most one existing task tab at any time that can be opened.
   *
   * @param project The project associated with the current Android Studio instance.
   */
  fun open(project: Project) {
    project.messageBus.syncPublisher(OpenProfilerTaskTabListener.TOPIC).openProfilerTaskTab()
  }
}