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
import com.android.tools.idea.editors.gfxtrace.rpc.*;
import com.android.tools.rpclib.rpccore.RpcException;
import com.google.common.primitives.Ints;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.util.ArrayUtil.EMPTY_OBJECT_ARRAY;

public class ContextController {
  private static final String NO_DEVICE_AVAILABLE = "No Device Available";
  private static final String NO_DEVICE_SELECTED = "No Device Selected";
  private static final String NO_CAPTURE_AVAILABLE = "No Capture Available";
  private static final String NO_CAPTURE_SELECTED = "No Capture Selected";

  @NotNull private static final Logger LOG = Logger.getInstance(ContextController.class);
  @NotNull private final GfxTraceEditor myEditor;
  @NotNull private final ComboBox myDevicesView;
  @NotNull private final ComboBox myCapturesView;
  @NotNull private final ComboBox myGfxContextsView;
  @Nullable private Device myCurrentDevice;
  @Nullable private Capture myCurrentCapture;
  @Nullable private volatile Integer myCurrentContext;
  @NotNull private Map<Device, DeviceId> myDevices = new HashMap<Device, DeviceId>();
  @NotNull private Map<Capture, CaptureId> myCaptures = new HashMap<Capture, CaptureId>();
  @NotNull private AtomicBoolean myShouldStopContextSwitch = new AtomicBoolean(false);

  public ContextController(@NotNull GfxTraceEditor editor,
                           @NotNull ComboBox devicesView,
                           @NotNull ComboBox capturesView,
                           @NotNull ComboBox gfxContextsView) {
    myEditor = editor;
    myDevicesView = devicesView;
    myCapturesView = capturesView;
    myGfxContextsView = gfxContextsView;

    myDevicesView.setRenderer(new ListCellRendererWrapper<Device>() {
      @Override
      public void customize(JList list, Device value, int index, boolean selected, boolean hasFocus) {
        if (list.getModel().getSize() == 0) {
          setText(NO_DEVICE_AVAILABLE);
        }
        else if (index == -1 && myCurrentDevice == null) {
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
          if (myCurrentCapture == null) {
            setText(NO_CAPTURE_SELECTED);
          }
          else {
            setText(myCurrentCapture.getName());
          }
        }
        else {
          setText(((Capture)list.getModel().getElementAt(index)).getName());
        }
      }
    });
  }

  @Nullable
  public Device getCurrentDevice() {
    return myCurrentDevice;
  }

  @NotNull
  public DeviceId getCurrentDeviceId() {
    DeviceId deviceId = myDevices.get(myCurrentDevice);
    if (deviceId == null) {
      throw new RuntimeException("DeviceId not found!");
    }
    return deviceId;
  }

  @Nullable
  public Capture getCurrentCapture() {
    return myCurrentCapture;
  }

  @NotNull
  public CaptureId getCurrentCaptureId() {
    CaptureId captureId = myCaptures.get(myCurrentCapture);
    if (captureId == null) {
      throw new RuntimeException("CaptureId not found!");
    }
    return captureId;
  }

  @Nullable
  public Integer getCurrentContext() {
    return myCurrentContext;
  }

  public void initialize() {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          Client client = myEditor.getClient();

          Future<CaptureId[]> captureFuture = client.GetCaptures();
          final CaptureId[] captureIds = captureFuture.get();

          DeviceId[] tempDeviceIds;
          while (true) {
            // TODO: Fix this when proper signaling from the server is implemented.
            // The server can detect and update the number of devices it is connected to at any moment in time. Therefore, during startup,
            // the server will find all the devices connected to it. During this period of time, the server will return an empty list of
            // available devices. This API/RPC is yet to be finalized, hence the hack below to work around that issue.
            Future<DeviceId[]> deviceFuture = client.GetDevices();
            tempDeviceIds = deviceFuture.get();
            if (tempDeviceIds.length > 0) {
              break;
            }
            //noinspection BusyWait
            Thread.sleep(200l);
          }
          final DeviceId[] deviceIds = tempDeviceIds;

          final List<Capture> captures = new ArrayList<Capture>(captureIds.length);
          final List<Device> devices = new ArrayList<Device>(deviceIds.length);

          for (CaptureId captureId : captureIds) {
            Capture capture = client.ResolveCapture(captureId).get();
            captures.add(capture);
          }

          for (DeviceId deviceId : deviceIds) {
            Device device = client.ResolveDevice(deviceId).get();
            devices.add(device);
          }

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              updateCaptureList(captureIds, captures);
              myCurrentCapture = null;

              updateAvailableDevices(deviceIds, devices, myCurrentDevice);

              getDevicesView().addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent itemEvent) {
                  if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
                    if (itemEvent.getItem() instanceof Device) {
                      Device selectedDevice = (Device)itemEvent.getItem();
                      selectDevice(selectedDevice);
                      myEditor.notifyDeviceChanged(selectedDevice);
                    }
                    else {
                      myCurrentDevice = null;
                    }
                  }
                }
              });

              getCapturesView().addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent itemEvent) {
                  if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
                    assert (itemEvent.getItem() instanceof Capture);
                    Capture selectedCapture = (Capture)itemEvent.getItem();
                    selectCapture(selectedCapture);
                    myEditor.notifyCaptureChanged(selectedCapture);
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
        catch (IOException e) {
          LOG.error(e);
        }
        catch (RpcException e) {
          LOG.error(e);
        }
      }
    });
  }

  public void populateUi(@NotNull int[] contextIds) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    updateGfxContextView(contextIds);
    setGfxContext(contextIds[0]);

    getGfxContextsView().addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent itemEvent) {
        if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
          Integer newContext = (Integer)itemEvent.getItem();
          assert (!newContext.equals(myCurrentContext));
          myCurrentContext = newContext;

          assert (myCurrentContext != null);
          //noinspection ConstantConditions
          setGfxContext(myCurrentContext);
        }
      }
    });
  }

  private void updateAvailableDevices(@NotNull DeviceId[] deviceIds, @NotNull List<Device> devices, @Nullable Device previouslySelected) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    updateDeviceList(deviceIds, devices);

    if (devices.size() > 0) {
      Device deviceToSelect = devices.get(0);
      if (previouslySelected != null) {
        if (devices.contains(previouslySelected)) {
          deviceToSelect = previouslySelected;
        }
      }
      selectDevice(deviceToSelect);
      myEditor.notifyDeviceChanged(deviceToSelect);
    }
  }

  private void selectDevice(@NotNull Device selectedDevice) {
    assert (myCurrentDevice != selectedDevice);

    myCurrentDevice = selectedDevice;
  }

  private void selectCapture(@NotNull Capture selectedCapture) {
    assert (selectedCapture != myCurrentCapture);
    myCurrentCapture = selectedCapture;
    myCurrentContext = null;
    clearGfxContextView(); // Invalidate the context IDs in the ComboBox first.
  }

  private void setGfxContext(@NotNull final Integer contextId) {
    ApplicationManager.getApplication().assertIsDispatchThread(); // Must be in EDT to call this, since we're synchronized on it.

    if (!myShouldStopContextSwitch.get() && contextId.equals(myCurrentContext)) {
      return;
    }

    myShouldStopContextSwitch.set(true);
    myShouldStopContextSwitch = new AtomicBoolean(false);
    myCurrentContext = contextId;

    myEditor.resolveGfxContextChange(myShouldStopContextSwitch);
  }

  @NotNull
  private ComboBox getDevicesView() {
    return myDevicesView;
  }

  @NotNull
  private ComboBox getCapturesView() {
    return myCapturesView;
  }

  @NotNull
  private ComboBox getGfxContextsView() {
    return myGfxContextsView;
  }

  private void updateDeviceList(@NotNull DeviceId[] deviceIds, @NotNull List<Device> devices) {
    myDevices.clear();
    for (int i = 0; i < deviceIds.length; ++i) {
      myDevices.put(devices.get(i), deviceIds[i]);
    }

    getDevicesView().setModel(new DefaultComboBoxModel(devices.toArray()));
  }

  private void updateCaptureList(@NotNull CaptureId[] captureIds, @NotNull List<Capture> captures) {
    myCaptures.clear();
    for (int i = 0; i < captureIds.length; ++i) {
      myCaptures.put(captures.get(i), captureIds[i]);
    }

    myCapturesView.setModel(new DefaultComboBoxModel(captures.toArray()));
    myCapturesView.setSelectedIndex(-1);
  }

  private void updateGfxContextView(@NotNull int[] contextList) {
    assert (contextList.length > 0);
    Object[] boxedContextList = Ints.asList(contextList).toArray();
    DefaultComboBoxModel model = new DefaultComboBoxModel(boxedContextList);
    myGfxContextsView.setModel(model);
    myGfxContextsView.setSelectedIndex(0);
  }

  private void clearGfxContextView() {
    getGfxContextsView().setModel(new DefaultComboBoxModel(EMPTY_OBJECT_ARRAY));
  }
}
