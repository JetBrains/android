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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.AndroidDebugBridge.IClientChangeListener;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.tools.adtui.EventData;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.allocations.AllocationCaptureType;
import com.android.tools.idea.monitor.memory.MemoryMonitorView;
import com.android.tools.idea.profiling.capture.Capture;
import com.android.tools.idea.profiling.capture.CaptureHandle;
import com.android.tools.idea.profiling.capture.CaptureService;
import com.google.common.util.concurrent.FutureCallback;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class ToggleAllocationTrackingAction extends AbstractClientToggleAction {
  private final Project myProject;
  private final EventData myEvents;
  private EventData.Event myEvent;

  public ToggleAllocationTrackingAction(@NotNull Project project, @NotNull DeviceContext context, @NotNull EventData events) {
    super(context,
          AndroidBundle.message("android.ddms.actions.allocationtracker.start"),
          AndroidBundle.message("android.ddms.actions.allocationtracker.description"),
          AndroidIcons.Ddms.AllocationTracker);

    myProject = project;
    myEvents = events;
  }

  @Override
  protected boolean isSelected(@NotNull Client c) {
    return c.getClientData().getAllocationStatus() == ClientData.AllocationTrackingStatus.ON;
  }

  @Override
  protected void setSelected(@NotNull Client c) {
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
    }
    else {
      installListener(c, myProject);
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
  @NotNull
  protected String getActiveText(@NotNull Client c) {
    return c.getClientData().getAllocationStatus() == ClientData.AllocationTrackingStatus.ON ? AndroidBundle
      .message("android.ddms.actions.allocationtracker.stop") : AndroidBundle.message("android.ddms.actions.allocationtracker.start");
  }

  private void installListener(@NotNull final Client listeningClient, @NotNull final Project project) {
    AndroidDebugBridge.addClientChangeListener(new IClientChangeListener() {
      @Override
      public void clientChanged(Client client, int changeMask) {
        if (client == listeningClient && (changeMask & Client.CHANGE_HEAP_ALLOCATIONS) != 0) {
          final byte[] data = client.getClientData().getAllocationsData();

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              try {
                if (myProject.isDisposed()) {
                  return;
                }

                final CaptureService service = CaptureService.getInstance(myProject);
                String name = service.getSuggestedName(listeningClient);
                CaptureHandle handle = service.startCaptureFile(AllocationCaptureType.class, name, true);
                service.appendDataCopy(handle, data);
                service.finalizeCaptureFileAsynchronous(handle, new FutureCallback<Capture>() {
                  @Override
                  public void onSuccess(Capture result) {
                    service.notifyCaptureReady(result);
                  }

                  @Override
                  public void onFailure(Throwable t) {
                    throw new RuntimeException(t);
                  }
                }, EdtExecutor.INSTANCE);
              }
              catch (IOException e) {
                throw new RuntimeException(e);
              }
            }
          });

          // Remove self from listeners.
          AndroidDebugBridge.removeClientChangeListener(this);
        }
      }
    });
  }
}
