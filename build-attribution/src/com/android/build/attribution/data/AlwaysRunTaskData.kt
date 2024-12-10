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

data class AlwaysRunTaskData(val taskData: TaskData, val rerunReason: Reason) {

  enum class Reason() {
    NO_OUTPUTS_WITH_ACTIONS,
    UP_TO_DATE_WHEN_FALSE;

    companion object {
      /**
       * Finds corresponding reason by the message that is defined in Gradle [org.gradle.api.internal.changedetection.changes.DefaultTaskExecutionMode].
       */
      fun findMatchingReason(message: String): Reason? = when(message) {
        // Do not use DefaultTaskExecutionMode directly as it sometimes leads to class loading issues (see b/366173283).
        "Task has not declared any outputs despite executing actions." -> NO_OUTPUTS_WITH_ACTIONS
        "Task.upToDateWhen is false." -> UP_TO_DATE_WHEN_FALSE
        else -> null
      }
    }
  }
}
