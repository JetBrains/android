/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.devicemanagerv2

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.tools.adtui.categorytable.IconButton
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.streaming.MirroringHandle
import com.android.tools.idea.streaming.MirroringManager
import com.android.tools.idea.streaming.MirroringState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.ui.EmptyIcon
import icons.StudioIcons
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A button for starting and stopping mirroring of a device. */
internal class StartStopMirroringButton(private val deviceHandle: DeviceHandle, project: Project) :
  IconButton(EmptyIcon.ICON_16) {

  private var mirroringHandle: MirroringHandle? = null

  init {
    isVisible = false

    addActionListener { mirroringHandle?.toggleMirroring() }

    deviceHandle.scope.launch {
      val mirroringHandles = project.service<MirroringManager>().mirroringHandles
      mirroringHandles.collect { handles ->
        withContext(uiThread) { updateMirroring(handles[deviceHandle]) }
      }
    }
  }

  private fun updateMirroring(mirroringHandle: MirroringHandle?) {
    this.mirroringHandle = mirroringHandle
    when {
      mirroringHandle == null -> {
        isVisible = false
        toolTipText = null
        baseIcon = EmptyIcon.ICON_16
      }
      mirroringHandle.mirroringState == MirroringState.INACTIVE -> {
        isVisible = true
        toolTipText = "Start Mirroring"
        baseIcon = StudioIcons.Avd.START_MIRROR
      }
      mirroringHandle.mirroringState == MirroringState.ACTIVE -> {
        isVisible = true
        toolTipText = "Stop Mirroring"
        baseIcon = StudioIcons.Avd.STOP_MIRROR
      }
    }
  }
}
