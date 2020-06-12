/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.invoker

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import java.lang.Exception

class NoopExternalSystemTaskNotificationListener: ExternalSystemTaskNotificationListener {
  override fun onStart(id: ExternalSystemTaskId) = Unit
  override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) = Unit
  override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) = Unit
  override fun onEnd(id: ExternalSystemTaskId) = Unit
  override fun onSuccess(id: ExternalSystemTaskId) = Unit
  override fun onFailure(id: ExternalSystemTaskId, e: Exception) = Unit
  override fun beforeCancel(id: ExternalSystemTaskId) = Unit
  override fun onCancel(id: ExternalSystemTaskId) = Unit
}
