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
package com.android.tools.profilers

import com.intellij.openapi.diagnostic.Logger

/**
 * This interface will be implemented by stages utilized for "Task" execution w.r.t. the Task-Based UX.
 */
interface TaskStage {
  private val LOGGER: Logger
    get() = Logger.getInstance(TaskStage::class.java)

  // To be passed in from the respective task handler, customizing the behavior on task stop.
  var stopTaskAction: Runnable

  /**
   * Method used to safely invoke the "stopTaskAction".
   *
   * This now allows the implementing Stage's bound view to invoke the stop action of a task handler.
   */
  fun stopTask() {
    try {
      stopTaskAction.run() ?: LOGGER.error("Stop task action is null")
    } catch (e: Exception) {
      LOGGER.error(e.message.toString())
    }
  }
}