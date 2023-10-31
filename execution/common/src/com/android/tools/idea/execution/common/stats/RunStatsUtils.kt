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
package com.android.tools.idea.execution.common.stats

import com.google.wireless.android.sdk.stats.LaunchTaskDetail

/**
 * Executes a task and adds its execution statistics to Android Run Event.
 *
 * It tracks name, execution time, result (success or not) and execution thread.
 *
 * All "heavy" bit's of code in execution code path should be wrapped into this function.
 * The main goal of this is to monitor all pieces that we add to the run pipeline.
 *
 * For now, it also allows to fill other fields of LaunchTaskDetail.Builder as artifacts, but it's only for Deployer tasks, and it will be deprecated.
 */
inline fun <T> RunStats.track(taskId: String, task: LaunchTaskDetail.Builder.() -> T): T {
  val customTask = beginCustomTask(taskId)
  return try {
    task(customTask.builder)
  }
  catch (t: Throwable) {
    endCustomTask(customTask, t)
    throw t
  }.also {
    endCustomTask(customTask, null)
  }
}