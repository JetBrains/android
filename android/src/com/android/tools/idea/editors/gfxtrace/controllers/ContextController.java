/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.service.Capture;
import com.android.tools.idea.editors.gfxtrace.service.Device;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.path.CapturePath;
import com.android.tools.idea.editors.gfxtrace.service.path.DevicePath;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.service.path.PathListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ContextController implements PathListener {
  private static final String NO_DEVICE_AVAILABLE = "No Device Available";
  private static final String NO_DEVICE_SELECTED = "No Device Selected";
  private static final String NO_CAPTURE_AVAILABLE = "No Capture Available";
  private static final String NO_CAPTURE_SELECTED = "No Capture Selected";

  class DeviceEntry {
    public DevicePath myPath;
    public Device myDevice;

    public DeviceEntry(DevicePath path, Device device) {
      myPath = path;
      myDevice = device;
    }
  }

  class CaptureEntry {
    public CapturePath myPath;
    public Capture myCapture;

    public CaptureEntry(CapturePath path, Capture capture) {
      myPath = path;
      myCapture = capture;
    }
  }

  @NotNull private static final Logger LOG = Logger.getInstance(ContextController.class);
  @NotNull private final GfxTraceEditor myEditor;
  @NotNull private final ComboBox myDevicesView;
  @NotNull private final ComboBox myCapturesView;
  @NotNull private DeviceEntry[] myDevices;
  @NotNull private CaptureEntry[] myCaptures;
  @NotNull private AtomicBoolean myShouldStopContextSwitch = new AtomicBoolean(false);

  public ContextController(@NotNull GfxTraceEditor editor,
                           @NotNull ComboBox devicesView,
                           @NotNull ComboBox capturesView) {
    myEditor = editor;
    myEditor.addPathListener(this);
    myDevicesView = devicesView;
    myCapturesView = capturesView;

    myDevicesView.setRenderer(new ListCellRendererWrapper<Device>() {
      @Override
      public void customize(JList list, Device value, int index, boolean selected, boolean hasFocus) {
        if (list.getModel().getSize() == 0) {
          setText(NO_DEVICE_AVAILABLE);
        }
        else if (index == -1) {
          setText(NO_DEVICE_SELECTED);
        }
        else {
          setText(value.getName() + " (" + value.getModel() + ", " + value.getOS() + ")");
        }
      }
    });

    myCapturesView.setRenderer(new ListCellRendererWrapper<Capture>() {
      @Override
      public void customize(JList list, Capture value, int index, boolean selected, boolean hasFocus) {
        if (list.getModel().getSize() == 0) {
          setText(NO_CAPTURE_AVAILABLE);
        }
        else if (index == -1) {
          setText(NO_CAPTURE_SELECTED);
        }
        else {
          setText(((Capture)list.getModel().getElementAt(index)).getName());
        }
      }
    });
  }

  public void initialize() {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          ServiceClient client = myEditor.getClient();
          final CapturePath[] capturePaths = client.getCaptures().get();

          DevicePath[] tempDevicePaths;
          while (true) {
            // TODO: Fix this when proper signaling from the server is implemented.
            // The server can detect and update the number of devices it is connected to at any moment in time. Therefore, during startup,
            // the server will find all the devices connected to it. During this period of time, the server will return an empty list of
            // available devices. This API/RPC is yet to be finalized, hence the hack below to work around that issue.
            tempDevicePaths = client.getDevices().get();
            if (tempDevicePaths.length > 0) {
              break;
            }
            //noinspection BusyWait
            Thread.sleep(200l);
          }
          final DevicePath[] devicePaths = tempDevicePaths;

          final CaptureEntry[] captures = new CaptureEntry[capturePaths.length];
          for (int i = 0; i < capturePaths.length; i++) {
            captures[i] = new CaptureEntry(capturePaths[i], client.get(capturePaths[i]).get());
          }

          final DeviceEntry[] devices = new DeviceEntry[devicePaths.length];
          for (int i = 0; i < capturePaths.length; i++) {
            devices[i] = new DeviceEntry(devicePaths[i], client.get(devicePaths[i]).get());
          }

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              myCaptures = captures;
              myCapturesView.setModel(new DefaultComboBoxModel(myCaptures));
              myCapturesView.setSelectedIndex(-1);

              getCapturesView().addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent itemEvent) {
                  if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
                    assert (itemEvent.getItem() instanceof CaptureEntry);
                    CaptureEntry entry = (CaptureEntry)itemEvent.getItem();
                    myEditor.activatePath(entry.myPath);
                  }
                }
              });

              myDevices = devices;
              myDevicesView.setModel(new DefaultComboBoxModel(myDevices));
              myDevicesView.setSelectedIndex(-1);

              myDevicesView.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent itemEvent) {
                  if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
                    assert (itemEvent.getItem() instanceof DeviceEntry);
                    DeviceEntry entry = (DeviceEntry)itemEvent.getItem();
                    myEditor.activatePath(entry.myPath);
                  }
                }
              });
            }
          });
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
        catch (ExecutionException e) {
          LOG.error(e);
        }
      }
    });
  }

  @Override
  public void notifyPath(Path path) {
    // TODO: pick out the device and capture roots, and use them to update the selected item
  }

  @NotNull
  private ComboBox getDevicesView() {
    return myDevicesView;
  }

  @NotNull
  private ComboBox getCapturesView() {
    return myCapturesView;
  }
}
