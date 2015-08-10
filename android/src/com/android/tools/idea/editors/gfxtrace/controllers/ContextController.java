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

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.LoadingCallback;
import com.android.tools.idea.editors.gfxtrace.service.Capture;
import com.android.tools.idea.editors.gfxtrace.service.Device;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.google.common.util.concurrent.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ContextController implements PathListener {
  private static final String NO_DEVICE_AVAILABLE = "No Device Available";
  private static final String NO_DEVICE_SELECTED = "No Device Selected";
  private static final String NO_CAPTURE_AVAILABLE = "No Capture Available";
  private static final String NO_CAPTURE_SELECTED = "No Capture Selected";

  private static class DeviceEntry {
    public DevicePath myPath;
    public Device myDevice;

    public DeviceEntry(DevicePath path, Device device) {
      myPath = path;
      myDevice = device;
    }
  }

  private static class CaptureEntry {
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
  @Nullable private DeviceEntry[] myDevices;
  @Nullable private CaptureEntry[] myCaptures;
  private final PathStore<CapturePath> mySelectedCapture = new PathStore<CapturePath>();
  private final PathStore<DevicePath> mySelectedDevice = new PathStore<DevicePath>();

  public ContextController(@NotNull GfxTraceEditor editor,
                           @NotNull ComboBox devicesView,
                           @NotNull ComboBox capturesView) {
    myEditor = editor;
    myEditor.addPathListener(this);
    myDevicesView = devicesView;
    myCapturesView = capturesView;

    myDevicesView.setRenderer(new ListCellRendererWrapper<DeviceEntry>() {
      @Override
      public void customize(JList list, DeviceEntry value, int index, boolean selected, boolean hasFocus) {
        if (list.getModel().getSize() == 0) {
          setText(NO_DEVICE_AVAILABLE);
        }
        else if (index == -1) {
          setText(NO_DEVICE_SELECTED);
        }
        else {
          setText(value.myDevice.getName() + " (" + value.myDevice.getModel() + ", " + value.myDevice.getOS() + ")");
        }
      }
    });

    myCapturesView.setRenderer(new ListCellRendererWrapper<CaptureEntry>() {
      @Override
      public void customize(JList list, CaptureEntry value, int index, boolean selected, boolean hasFocus) {
        if (list.getModel().getSize() == 0) {
          setText(NO_CAPTURE_AVAILABLE);
        }
        else if (index == -1) {
          setText(NO_CAPTURE_SELECTED);
        }
        else {
          setText(value.myCapture.getName());
        }
      }
    });
  }

  public void initialize() {
    myCapturesView.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent itemEvent) {
        if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
          assert (itemEvent.getItem() instanceof CaptureEntry);
          CaptureEntry entry = (CaptureEntry)itemEvent.getItem();
          myEditor.activatePath(entry.myPath);
        }
      }
    });

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

    Futures.addCallback(myEditor.getClient().getCaptures(), new LoadingCallback<CapturePath[]>(LOG) {
      @Override
      public void onSuccess(@Nullable final CapturePath[] paths) {
        final ListenableFuture<Capture>[] futures = new ListenableFuture[paths.length];
        for (int i = 0; i < paths.length; i++) {
          futures[i] = myEditor.getClient().get(paths[i]);
        }
        Futures.addCallback(Futures.allAsList(futures), new LoadingCallback<List<Capture>>(LOG) {
          @Override
          public void onSuccess(@Nullable final List<Capture> captures) {
            EdtExecutor.INSTANCE.execute(new Runnable() {
              @Override
              public void run() {
                // Back in the UI thread here
                myCaptures = new CaptureEntry[paths.length];
                for (int i = 0; i < paths.length; i++) {
                  myCaptures[i] = new CaptureEntry(paths[i], captures.get(i));
                }
                myCapturesView.setModel(new DefaultComboBoxModel(myCaptures));
                myCapturesView.setSelectedIndex(-1);
              }
            });
          }
        });
      }
    });

    Futures.addCallback(myEditor.getClient().getDevices(), new LoadingCallback<DevicePath[]>(LOG) {
      @Override
      public void onSuccess(@Nullable final DevicePath[] paths) {
        final ListenableFuture<Device>[] futures = new ListenableFuture[paths.length];
        for (int i = 0; i < paths.length; i++) {
          futures[i] = myEditor.getClient().get(paths[i]);
        }
        Futures.addCallback(Futures.allAsList(futures), new LoadingCallback<List<Device>>(LOG) {
          @Override
          public void onSuccess(@Nullable final List<Device> devices) {
            EdtExecutor.INSTANCE.execute(new Runnable() {
              @Override
              public void run() {
                // Back in the UI thread here
                myDevices = new DeviceEntry[paths.length];
                for (int i = 0; i < paths.length; i++) {
                  myDevices[i] = new DeviceEntry(paths[i], devices.get(i));
                }
                myDevicesView.setModel(new DefaultComboBoxModel(myDevices));
                myDevicesView.setSelectedIndex(-1);
              }
            });
          }
        });
      }
    });
  }

  @Override
  public void notifyPath(Path path) {
    if (path instanceof CapturePath) {
      if (mySelectedCapture.update((CapturePath)path)) {
        if (myCaptures != null) {
          for (int i = 0; i < myCaptures.length; i++) {
            if (mySelectedCapture.is(myCaptures[i].myPath)) {
              myCapturesView.setSelectedIndex(i);
              return;
            }
          }
          // capture not found
          myCapturesView.setSelectedIndex(-1);
        }
      }
    }

    if (path instanceof DevicePath) {
      if (mySelectedDevice.update((DevicePath)path)) {
        if (myDevices != null) {
          for (int i = 0; i < myDevices.length; i++) {
            if (mySelectedDevice.is(myDevices[i].myPath)) {
              myDevicesView.setSelectedIndex(i);
              return;
            }
          }
          // device not found
          myDevicesView.setSelectedIndex(-1);
        }
      }
    }
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
