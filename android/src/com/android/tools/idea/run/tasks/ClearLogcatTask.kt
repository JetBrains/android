/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.tasks

import com.android.tools.idea.run.ClearLogcatListener
import com.intellij.openapi.project.Project

private const val ID = "CLEAR_LOGCAT"

/**
 * A [LaunchTask] that clears Logcat components.
 */
class ClearLogcatTask(private val project: Project) : LaunchTask {
  override fun getDescription(): String = "Clearing Logcat"

  override fun getDuration(): Int = LaunchTaskDurations.ASYNC_TASK

  override fun getId(): String = ID

  override fun run(launchContext: LaunchContext) {
    project.messageBus.syncPublisher(ClearLogcatListener.TOPIC).clearLogcat(launchContext.device.serialNumber)
  }
}
