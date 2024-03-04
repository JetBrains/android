/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.adddevicedialog

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.AndroidPluginDisposable

private class AddDeviceAction private constructor() : DumbAwareAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project

    val parent =
      if (project == null) AndroidPluginDisposable.getApplicationInstance()
      else AndroidPluginDisposable.getProjectInstance(project)

    AndroidCoroutineScope(parent, AndroidDispatchers.workerThread).launch {
      val sources =
        DeviceSourceProvider.deviceSourceProviders.mapNotNull { it.createDeviceSource(project) }

      withContext(AndroidDispatchers.uiThread) {
        AddDeviceWizard(sources, project).createDialog().show()
      }
    }
  }
}
