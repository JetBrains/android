/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.profilers.taskbased

import com.android.tools.profilers.Notification.Companion.createError

/**
 * CPU related notification constants.
 */
object TaskNotifications {
  private const val TRY_TASK_AGAIN_MSG = "Try starting the task again or "

  @JvmField
  val STARTUP_TASK_FAILURE = createError("Task could not be launched on startup", TRY_TASK_AGAIN_MSG)

  // May be used when the starting point is process start as well (if the error isn't specific to startup).
  val LAUNCH_TASK_FAILURE = createError("Task launch error", TRY_TASK_AGAIN_MSG)
}