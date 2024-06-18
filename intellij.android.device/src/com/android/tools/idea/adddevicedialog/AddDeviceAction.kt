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

import com.android.sdklib.deviceprovisioner.extensions
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class AddDeviceAction private constructor() : DumbAwareAction() {
  override fun actionPerformed(event: AnActionEvent) {
    // TODO(b/349112418): Support use without a project
    val project = event.project ?: return

    val provisioner = project.service<DeviceProvisionerService>().deviceProvisioner

    AndroidCoroutineScope(project, AndroidDispatchers.workerThread).launch {
      val sources = provisioner.extensions<DeviceSource>()
      withContext(AndroidDispatchers.uiThread) {
        AddDeviceWizard(sources, project).createDialog().show()
      }
    }
  }
}
