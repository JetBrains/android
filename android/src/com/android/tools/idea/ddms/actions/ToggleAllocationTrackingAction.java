/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.ddms.actions;

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.tools.idea.ddms.DeviceContext;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public class ToggleAllocationTrackingAction extends ToggleAction {
  private final DeviceContext myDeviceContext;

  public ToggleAllocationTrackingAction(@NotNull DeviceContext context) {
    super(AndroidBundle.message("android.ddms.actions.allocationtracker.start"),
          null,
          AndroidIcons.Ddms.AllocationTracker);
    myDeviceContext = context;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    Client c = myDeviceContext.getSelectedClient();
    if (c == null) {
      return false;
    }
    return c.getClientData().getAllocationStatus() == ClientData.AllocationTrackingStatus.ON;
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    Client c = myDeviceContext.getSelectedClient();
    if (c == null) {
      return;
    }
    if (c.getClientData().getAllocationStatus() == ClientData.AllocationTrackingStatus.ON) {
      c.requestAllocationDetails();
      c.enableAllocationTracker(false);
    } else {
      c.enableAllocationTracker(true);
    }
    c.requestAllocationStatus();
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    Presentation presentation = e.getPresentation();
    presentation.setVisible(Boolean.getBoolean("ALLOC_TRACK"));

    Client c = myDeviceContext.getSelectedClient();
    if (c == null) {
      presentation.setEnabled(false);
      return;
    }

    String text = c.getClientData().getAllocationStatus() == ClientData.AllocationTrackingStatus.ON ?
                  AndroidBundle.message("android.ddms.actions.allocationtracker.stop") :
                  AndroidBundle.message("android.ddms.actions.allocationtracker.start");
    presentation.setText(text);
    presentation.setEnabled(true);
  }
}
