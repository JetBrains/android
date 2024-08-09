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

package com.android.tools.idea.backup

import com.intellij.openapi.project.Project

/** Provides functions needed by actions */
internal interface ActionHelper {
  /** Tries to get the application id from the active run configuration */
  fun getApplicationId(project: Project): String?

  /** Returns the number of devices selected in the deployment target selector */
  fun getDeployTargetCount(project: Project): Int

  /**
   * Returns the serial number of the deployment target
   *
   * @return null if there is more than one target or if the target is not running
   */
  suspend fun getDeployTargetSerial(project: Project): String?

  /** Display a warning popup */
  suspend fun showWarning(project: Project, title: String, message: String)
}
