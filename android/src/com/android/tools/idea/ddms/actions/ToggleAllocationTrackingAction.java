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
import com.android.tools.chartlib.EventData;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.monitor.memory.MemoryMonitorView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public class ToggleAllocationTrackingAction extends ToggleAction {
  private final DeviceContext myDeviceContext;
  private final EventData myEvents;
  private EventData.Event myEvent;

  public ToggleAllocationTrackingAction(@NotNull DeviceContext context, EventData events) {
    super(AndroidBundle.message("android.ddms.actions.allocationtracker.start"),
          null,
          AndroidIcons.Ddms.AllocationTracker);
    myDeviceContext = context;
    myEvents = events;
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
    long now = System.currentTimeMillis();
    if (c.getClientData().getAllocationStatus() == ClientData.AllocationTrackingStatus.ON) {
      c.requestAllocationDetails();
      c.enableAllocationTracker(false);
      if (myEvent == null) {
        // Unexpected end of tracking, start now:
        myEvent = myEvents.start(now, MemoryMonitorView.EVENT_ALLOC);
      }
      myEvent.stop(now);
      myEvent = null;
    } else {
      c.enableAllocationTracker(true);
      if (myEvent != null) {
        // TODO add support for different end types (error, etc)
        myEvent.stop(now);
      }
      myEvent = myEvents.start(now, MemoryMonitorView.EVENT_ALLOC);
    }
    c.requestAllocationStatus();
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    Presentation presentation = e.getPresentation();

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
