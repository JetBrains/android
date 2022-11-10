/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.adblib

import com.android.adblib.AdbSession
import com.android.ddmlib.AndroidDebugBridge
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * [Project] service that provides access to the corresponding [AdbSession] for that project.
 *
 * Example: `AdbLibService.getInstance(project).hostServices`
 *
 * If a [Project] instance is not available, use [AdbLibApplicationService] instead, but it is then
 * the caller's responsibility to manage [AndroidDebugBridge] initialization.
 */
interface AdbLibService : Disposable {
  val session: AdbSession

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<AdbLibService>()

    @JvmStatic
    fun getSession(project: Project) = getInstance(project).session
  }
}
