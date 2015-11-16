/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.ddms.hprof;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.tools.chartlib.EventData;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.ddms.actions.AbstractClientAction;
import com.android.tools.idea.editors.hprof.HprofCaptureType;
import com.android.tools.idea.monitor.memory.MemoryMonitorView;
import com.android.tools.idea.profiling.capture.Capture;
import com.android.tools.idea.profiling.capture.CaptureHandle;
import com.android.tools.idea.profiling.capture.CaptureService;
import com.google.common.util.concurrent.FutureCallback;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DumpHprofAction extends AbstractClientAction {
  @NotNull private final Project myProject;
  @NotNull private EventData myEvents;
  private boolean isCollectingHprofDump;

  public DumpHprofAction(@NotNull Project project, @NotNull DeviceContext deviceContext, @NotNull EventData events) {
    super(deviceContext, AndroidBundle.message("android.ddms.actions.dump.hprof"),
          AndroidBundle.message("android.ddms.actions.dump.hprof.description"), AndroidIcons.Ddms.DumpHprof);
    myProject = project;
    myEvents = events;
  }

  @Override
  protected void performAction(@NotNull Client c) {
    isCollectingHprofDump = true;
    ApplicationManager.getApplication().executeOnPooledThread(new HprofRequest(c, myEvents));
  }

  @Override
  protected boolean canPerformAction() {
    return !isCollectingHprofDump;
  }

  private class HprofRequest implements Runnable, AndroidDebugBridge.IClientChangeListener {

    private final Client myClient;
    private CountDownLatch myResponse;
    private final EventData myEvents;
    private EventData.Event myEvent;

    public HprofRequest(Client client, EventData events) {
      myClient = client;
      myEvents = events;
      myResponse = new CountDownLatch(1);
    }

    @Override
    public void run() {
      AndroidDebugBridge.addClientChangeListener(this);

      try {
        myClient.dumpHprof();
        synchronized (myEvents) {
          myEvent = myEvents.start(System.currentTimeMillis(), MemoryMonitorView.EVENT_HPROF);
        }
        try {
          myResponse.await(1, TimeUnit.MINUTES);
          // TODO Handle cases where it fails or times out.
        }
        catch (InterruptedException e) {
          // Interrupted
        }
        // If the event had not finished, finish it now
        synchronized (myEvents) {
          if (myEvent != null) {
            myEvent.stop(System.currentTimeMillis());
          }
        }
      }
      finally {
        isCollectingHprofDump = false;
        AndroidDebugBridge.removeClientChangeListener(this);
      }
    }

    @Override
    public void clientChanged(final Client client, int changeMask) {
      if (changeMask == Client.CHANGE_HPROF && client == myClient) {
        assert !ApplicationManager.getApplication().isDispatchThread();

        final ClientData.HprofData data = client.getClientData().getHprofData();
        if (data != null) {
          switch (data.type) {
            case FILE:
              // TODO: older devices don't stream back the heap data. Instead they save results on the sdcard.
              // We don't support this yet.
              Messages.showErrorDialog(AndroidBundle.message("android.ddms.actions.dump.hprof.error.unsupported"),
                                       AndroidBundle.message("android.ddms.actions.dump.hprof"));
              break;
            case DATA:
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  try {
                    final CaptureService service = CaptureService.getInstance(myProject);
                    String name = service.getSuggestedName(client);
                    CaptureHandle handle = service.startCaptureFile(HprofCaptureType.class, name);
                    service.appendDataCopy(handle, data.data);
                    service.finalizeCaptureFileAsynchronous(handle, new FutureCallback<Capture>() {
                      @Override
                      public void onSuccess(Capture result) {
                        service.notifyCaptureReady(result);
                      }

                      @Override
                      public void onFailure(Throwable t) {
                        Messages.showErrorDialog("Error writing Hprof data", "Dump Java Heap");
                      }
                    }, EdtExecutor.INSTANCE);
                  }
                  catch (IOException e) {
                    Messages.showErrorDialog("Error create Hprof file", "Dump Java Heap");
                  }
                }
              });

              break;
          }
        }
        else {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              Messages.showErrorDialog("Error obtaining Hprof data", AndroidBundle.message("android.ddms.actions.dump.hprof"));
            }
          });
        }
        myResponse.countDown();
      }
    }
  }
}
