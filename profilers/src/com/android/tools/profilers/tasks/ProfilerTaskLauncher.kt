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

import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.IdeProfilerServices
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.tasks.args.TaskArgs
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandler
import com.intellij.openapi.diagnostic.Logger
import java.util.function.BiConsumer

object ProfilerTaskLauncher {

  private fun getLogger(): Logger {
    return Logger.getInstance(ProfilerTaskLauncher::class.java)
  }

  /**
   * Launches the task tab with the correct task handler, performing all necessary operations. The criteria to launch a task is the
   * selection of a corresponding new/alive session or a previously-selected terminated session under the Task-Based UX, as session and
   * tasks are 1:1.
   */
  @JvmStatic
  fun launchProfilerTask(selectedTaskType: ProfilerTaskType,
                         isStartupTask: Boolean,
                         taskHandlers: Map<ProfilerTaskType, ProfilerTaskHandler>,
                         session: Common.Session,
                         sessionIdToSessionItems: Map<Long, SessionItem>,
                         openTaskTab: BiConsumer<ProfilerTaskType, TaskArgs>,
                         ideProfilerServices: IdeProfilerServices) {
    if (!taskHandlers.containsKey(selectedTaskType)) {
      getLogger().error("The task type, " + selectedTaskType.description + ", " + "does not have a corresponding task handler.")
      return
    }

    val taskHandler: ProfilerTaskHandler = taskHandlers[selectedTaskType]!!
    try {
      // Construct the args using the selected tasks task handler implementation of TaskArgs creation.
      val args = taskHandler.createArgs(isStartupTask, sessionIdToSessionItems, session)
      // Open the task tab with the selected task and constructed task arguments.
      openTaskTab.accept(selectedTaskType, args)
    }
    catch (e: Exception) {
      ideProfilerServices.openErrorDialog("There was an error launching the task.", "Task Launch Error")
    }
  }
}