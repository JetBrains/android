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
import com.google.wireless.android.sdk.stats.DeviceManagerEvent
import com.intellij.icons.AllIcons
import kotlinx.coroutines.launch

internal class EditButton(private val handle: DeviceHandle) : IconButton(AllIcons.Actions.Edit) {
  init {
    toolTipText = DeviceManagerBundle.message("editButton.tooltip")

    addActionListener {
      DeviceManagerUsageTracker.logEvent(
        DeviceManagerEvent.newBuilder()
          .setKind(DeviceManagerEvent.EventKind.VIRTUAL_EDIT_ACTION)
          .build()
      )

      handle.scope.launch { handle.editAction?.edit() }
    }

    handle.scope.launch(uiThread) { trackActionEnabled(handle.editAction) }
  }
}
