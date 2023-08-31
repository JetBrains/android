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
package com.android.tools.profilers.tasks.taskhandlers.singleartifact.memory

import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.SingleArtifactTaskHandler

/**
 * This class defines the task handlers that produce single memory artifacts. The key augmentation of this class is the setting up of the
 * MainMemoryProfilerStage (its respective InterimStage) to facilitate the task handler's starting, stopping, and loading of the task.
 */
abstract class MemoryTaskHandler(private val sessionsManager: SessionsManager) : SingleArtifactTaskHandler<MainMemoryProfilerStage>(
  sessionsManager) {

  /**
   * To perform the memory task start, stop, and load, the MainMemoryProfilerStage (an implementation of an InterimStage is instantiated
   * and set to be the current stage both inside this task handler, and in the StudioProfiler stage manager.
   */
  override fun setupStage() {
    val studioProfilers = sessionsManager.studioProfilers
    val stage = MainMemoryProfilerStage(studioProfilers, this::stopTask)
    // Set the new stage to be the current stage in the Profiler.
    studioProfilers.stage = stage
    // Set the new stage to be this task handler's stage, which can now be used ot start and stop captures.
    super.stage = stage
  }
}