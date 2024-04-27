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

import com.android.tools.adtui.categorytable.IconButton
import com.intellij.ide.DataManager
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.ui.popup.JBPopupFactory
import icons.StudioIcons

class OverflowButton : IconButton(StudioIcons.Common.OVERFLOW) {

  companion object {
    private val reservationActions =
      DefaultActionGroup(
        CustomActionsSchema.getInstance().getCorrectedAction("android.device.reservation.end"),
        CustomActionsSchema.getInstance()
          .getCorrectedAction("android.device.reservation.extend.half.hour"),
        CustomActionsSchema.getInstance()
          .getCorrectedAction("android.device.reservation.extend.max"),
      )
    private val wearableActions =
      DefaultActionGroup(
        PairWearableDeviceAction(),
        ViewPairedDevicesAction(),
        UnpairWearableDeviceAction(),
      )
    val actions =
      DefaultActionGroup(
        reservationActions,
        Separator.create(),
        ColdBootAction(),
        wearableActions,
        Separator.create(),
        EditDeviceAction(),
        DuplicateDeviceAction(),
        WipeDataAction(),
        DeleteAction(),
        Separator.create(),
        OpenDeviceExplorerAction(),
        ViewDetailsAction(),
        ShowAction(),
      )
  }

  init {
    addActionListener {
      JBPopupFactory.getInstance()
        .createActionGroupPopup(
          null,
          actions,
          DataManager.getInstance().getDataContext(this@OverflowButton),
          true,
          null,
          15
        )
        .showUnderneathOf(this@OverflowButton)
    }
  }
}
