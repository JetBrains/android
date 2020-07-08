/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.internal

import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.api.AppInspectorLauncher
import com.android.tools.idea.appinspection.api.process.ProcessNotifier
import kotlinx.coroutines.CoroutineScope

/**
 * This serves as the entry point to all public AppInspection API services, specifically:
 * 1) discover when processes start and finish.
 * 2) launch inspectors on discovered processes.
 */
internal class DefaultAppInspectionApiServices internal constructor(
  private val targetManager: AppInspectionTargetManager,
  override val processNotifier: ProcessNotifier,
  override val launcher: AppInspectorLauncher,
  override val scope: CoroutineScope
) : AppInspectionApiServices {

  override fun disposeClients(project: String) {
    targetManager.disposeClients(project)
  }
}