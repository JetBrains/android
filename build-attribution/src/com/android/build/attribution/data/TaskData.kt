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
package com.android.build.attribution.data

class TaskData(taskPath: String, val originPlugin: PluginData) {
  val taskName: String
  val projectPath: String

  init {
    val lastColonIndex = taskPath.lastIndexOf(':')
    projectPath = taskPath.substring(0, lastColonIndex)
    taskName = taskPath.substring(lastColonIndex + 1)
  }

  fun getTaskPath(): String {
    return "$projectPath:$taskName"
  }

  override fun equals(other: Any?): Boolean {
    if (other is TaskData) {
      return taskName == other.taskName && projectPath == other.projectPath && originPlugin == other.originPlugin
    }
    return super.equals(other)
  }

  override fun hashCode(): Int {
    var result = originPlugin.hashCode()
    result = 31 * result + taskName.hashCode()
    result = 31 * result + projectPath.hashCode()
    return result
  }
}
