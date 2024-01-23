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
package com.android.tools.profilers.tasks.taskhandlers

import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.sessions.SessionArtifact
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.TaskEventTrackerUtils.trackTaskEntered
import com.android.tools.profilers.tasks.args.TaskArgs
import com.intellij.openapi.diagnostic.Logger

/**
 * The ProfilerTaskHandler serves as the base class for all task handlers. It enforces implementation of what to do on task start, stop,
 * and how to load a task. Moreover, it also enforces implementation for a method to create the respective task arguments, when to know if
 * the task is defined as terminated, what to return for the task name, and whether the task supports a specific artifact.
 */
abstract class ProfilerTaskHandler(private val sessionsManager: SessionsManager) {
  private fun getLogger(): Logger {
    return Logger.getInstance(ProfilerTaskHandler::class.java)
  }

  /**
   * Because the lifecycle of tasks and sessions are equivalent, on enter of the task handler it can take two paths depending on if the
   * current session is alive or not: (1) if the session is alive (and thus the task is alive), we want to start the task (2) if the session
   * is dead (and thus the task is already complete/terminated), we want to load it up/display it. These two paths are built on the
   * following assumption: a task handler will not be entered until the new session tied to the task is successfully created and set as the
   * current/selected session. Therefore, the `mySelectedSession` of the SessionsManager (the session `isSessionAlive` is checking the
   * status of) will be correctly set before the task handler is entered.
   *
   * ProfilerTaskHandler#enter returns a boolean flag indicating whether the call to enter was successful (it was either able to start the
   * task or load the task with the supplied args). Note that the returned boolean only tells us if the ProfilerTaskHandlers' enter was
   * successful, it does not tell us if the startTask or loadTask functionality was successful.
   */
  open fun enter(args: TaskArgs) : Boolean {
    trackTaskEntered(sessionsManager.studioProfilers)
    if (sessionsManager.isSessionAlive) {
      startTask(args)
    }
    else {
      return loadTask(args)
    }

    return true
  }

  /**
   * Before entering a new task handler, we can call this exit method to perform any cleanup work the task necessitates. This is needed to
   * prevent memory leaks as task handlers are created at inception of the toolwindow and live throughout the duration of the program.
   */
  open fun exit() {}

  /**
   * Task behavior on start.
   *
   * Reads the task arguments (@param args) to determine if the task should be started on startup or not.
   */
  abstract fun startTask(args: TaskArgs)

  /**
   * Task behavior on stop.
   */
  abstract fun stopTask()

  /**
   * Reads the task arguments (@param args) for the backing task data, then uses it to load the task.
   *
   * Returns a boolean indicating whether it was able to cast to the correct TaskArgs subtype or not.
   */
  abstract fun loadTask(args: TaskArgs) : Boolean

  /**
   * Returns the name of the task.
   */
  abstract fun getTaskName(): String

  /**
   * Because every task has a respective TaskArgs (arguments construct), this method allows to each task to customize how their respective
   * arguments are constructed.
   *
   * To construct the arguments to load a previous or imported task, the session (sessionItems) are passed in for inspection.
   *
   * @param isStartupTask boolean flag indicating whether the task was initiated on startup of the process or not
   * @param sessionItems list of session items (sessions taken in the current profiler instance or from importing) that contain artifacts
   * @param selectedSession the current session (alive or not) that the current task corresponds to
   */
  fun createArgs(isStartupTask: Boolean,sessionItems: Map<Long, SessionItem>, selectedSession: Common.Session): TaskArgs {
    val isTaskOngoing = SessionsManager.isSessionAlive(selectedSession)
    // Finds the artifact that backs the task identified via its corresponding unique session (selectedSession).
    val artifact = TaskHandlerUtils.findTaskArtifact(selectedSession, sessionItems, ::supportsArtifact)

    return if (isTaskOngoing) {
      // If the session/task is not complete yet, then the TaskArgs only need to contain data on whether it is a startup task or not.
      createStartTaskArgs(isStartupTask)
    }
    else if (artifact != null && supportsArtifact(artifact)) {
      // If the task is complete and supports the found artifact, a TaskArgs is constructed using the artifact to load the completed task.
      createLoadingTaskArgs(artifact)
    }
    else {
      // There should never be a state in which a session is complete (not alive) and does not have a corresponding artifact present.
      throw IllegalStateException("No supported artifact was found to construct the TaskArgs with")
    }
  }

  /**
   * Returns whether the task supports a given session artifact (backing data construct).
   */
  abstract fun supportsArtifact(artifact: SessionArtifact<*>?): Boolean

  /**
   * Returns the TaskArgs (arguments construct) used when starting a task.
   */
  protected abstract fun createStartTaskArgs(isStartupTask: Boolean): TaskArgs

  /**
   * Returns the TaskArgs (arguments construct) used when loading an existing task.
   */
  protected abstract fun createLoadingTaskArgs(artifact: SessionArtifact<*>): TaskArgs

  /**
   * Returns whether the task supports a given device and process. Some tasks only require checking the device, some only the process, and
   * some require checking both.
   */
  fun supportsDeviceAndProcess(device: Common.Device, process: Common.Process) = device != Common.Device.getDefaultInstance() &&
                                                                                          process != Common.Process.getDefaultInstance() &&
                                                                                          checkDeviceAndProcess(device, process)

  protected abstract fun checkDeviceAndProcess(device: Common.Device, process: Common.Process): Boolean

  /**
   * Unified error handler for all task handlers.
   */
  fun handleError(errorMessage: String) {
    // TODO(b/298246786): Improve/refine the error handling
    getLogger().error("There was an error with the ${getTaskName()} task. Error message: $errorMessage.")
  }
}