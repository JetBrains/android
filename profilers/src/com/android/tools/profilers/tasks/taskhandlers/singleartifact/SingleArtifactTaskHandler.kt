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
package com.android.tools.profilers.tasks.taskhandlers.singleartifact

import com.android.tools.profilers.InterimStage
import com.android.tools.profilers.sessions.SessionArtifact
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.args.TaskArgs
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.TaskHandlerUtils
import com.google.common.annotations.VisibleForTesting

/**
 * Building on/extending the ProfilerTaskHandler, this abstract class adds and enforces additional functionality catered towards tasks
 * backed by a single artifact. It augments the behavior of the ProfilerTaskHandler by introducing and enforcing the use of an InterimStage
 * to facilitate the capture of the artifact.
 */
abstract class SingleArtifactTaskHandler<T : InterimStage>(sessionsManager: SessionsManager) : ProfilerTaskHandler(sessionsManager) {

  /**
   * To collect the single artifact, an interim stage instance is utilized.
   *
   * For example, to perform a heap dump capture (and thus receive a heap dump artifact), the MainMemoryProfilerStage's
   * startHeapDumpCapture method can be invoked.
   */
  var stage: T? = null
    protected set

  /**
   * This method sets up the respective stage which translates into (1) creating the instance of the stage and (2) setting the newly created
   * stage as the current one.
   *
   * To be called before invoking the super class' enter method as the stage being prepared is a pre-requisite to start and load a task.
   */
  @VisibleForTesting
  abstract fun setupStage()

  /**
   * Builds upon the ProfilerTaskHandler enter method by setting up the respective stage first to enable starting and loading a task.
   */
  override fun enter(args: TaskArgs?): Boolean {
    setupStage()
    return super.enter(args)
  }

  /**
   * SingleArtifactTaskHandlers create and store a stage to perform starting, stopping, and loading of the tasks. Yet, when this type of
   * task handler is not in use, it is unnecessary to hold onto the instance of the stage. Thus, on exit of the task handler, we null it
   * out to prevent a memory leak.
   */
  override fun exit() {
    stage = null
  }

  /**
   * For a single artifact task handler, starting the task functionally is equivalent to starting the capture of the artifact.
   */
  override fun startTask() {
    if (stage == null) {
      handleError("Cannot start the task as the InterimStage was null")
      return
    }
    TaskHandlerUtils.executeTaskAction(action = { startCapture(stage!!) }, errorHandler = ::handleError)
  }

  /**
   * For a single artifact task handler, stopping the task functionally is equivalent to stopping the capture of the artifact.
   */
  override fun stopTask() {
    if (stage == null) {
      handleError("Cannot stop the task as the InterimStage was null")
      return
    }
    TaskHandlerUtils.executeTaskAction(action = { stopCapture(stage!!) }, errorHandler = ::handleError)
  }

  /**
   * For a single artifact task handler, loading the task functionally is equivalent to invoke the doSelect method on an artifact.
   * This will effectively create and set the capture stage required to display the artifact. To prepare for this doSelect behavior, the
   * only pre-requisite is being in the correct InterimStage, which is why we invoke setupStage before entering the task for single
   * artifact task handlers.
   */
  protected fun loadCapture(artifact: SessionArtifact<*>) {
    TaskHandlerUtils.executeTaskAction(action = { artifact.doSelect() }, errorHandler = ::handleError)
  }

  /**
   * Utilizing the parametrized InterimStage, implementations invoke the start of a capture for their respective tasks.
   */
  protected abstract fun startCapture(stage: T)

  /**
   * Utilizing the parametrized InterimStage, implementations invoke the stop of a capture for their respective tasks.
   */
  protected abstract fun stopCapture(stage: T)
}