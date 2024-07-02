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

package com.android.tools.idea.backup.testing

import com.android.tools.idea.backup.ActionHelper
import com.intellij.openapi.project.Project

/** A fake implementation of [ActionHelper] */
internal class FakeActionHelper(
  private val applicationId: String?,
  private val targetCount: Int,
  private val serialNumber: String?,
) : ActionHelper {
  val warnings = mutableListOf<String>()

  override fun getApplicationId(project: Project) = applicationId

  override fun getDeployTargetCount(project: Project) = targetCount

  override suspend fun getDeployTargetSerial(project: Project) = serialNumber

  override suspend fun showWarning(project: Project, title: String, message: String) {
    warnings.add("$title: $message")
  }
}
