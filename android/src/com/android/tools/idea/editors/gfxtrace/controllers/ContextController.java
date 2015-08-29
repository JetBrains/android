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
import com.android.tools.idea.editors.gfxtrace.service.Device;
import com.android.tools.idea.editors.gfxtrace.service.path.DevicePath;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.service.path.PathStore;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ContextController extends Controller {
  @NotNull public static final String TOOLBAR_NAME = "GfxTraceViewPanelToolbar";
  @NotNull private static final String NO_DEVICE_AVAILABLE = "No Device Available";
  @NotNull private static final String NO_DEVICE_SELECTED = "No Device Selected";
  @NotNull private static final String RETRIEVING_DEVICES = "Retrieving Devices";

  @NotNull private static final Logger LOG = Logger.getInstance(ContextController.class);
  @Nullable private DeviceEntry[] myDevices;
  @NotNull private final JComponent myToolBar;
  private final PathStore<DevicePath> mySelectedDevice = new PathStore<DevicePath>();

  public static JComponent createUI(GfxTraceEditor editor) {
    return new ContextController(editor).myToolBar;
  }

  private ContextController(@NotNull GfxTraceEditor editor) {
    super(editor);
    DefaultActionGroup group = new DefaultActionGroup(getContextAction());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    myToolBar = toolbar.getComponent();
    myToolBar.setName(TOOLBAR_NAME);

    // Populate the available device list.
    Futures.addCallback(myEditor.getClient().getDevices(), new LoadingCallback<DevicePath[]>(LOG) {
      @Override
      public void onSuccess(@Nullable final DevicePath[] paths) {
        if (paths == null || paths.length == 0) {
          myDevices = new DeviceEntry[0];
          return;
        }

        final ArrayList<ListenableFuture<Device>> futures = new ArrayList<ListenableFuture<Device>>(paths.length);
        for (DevicePath path : paths) {
          futures.add(myEditor.getClient().get(path));
        }

        Futures.addCallback(Futures.allAsList(futures), new LoadingCallback<List<Device>>(LOG) {
          @Override
          public void onSuccess(@Nullable final List<Device> devices) {
            if (devices == null || devices.size() == 0) {
              return;
            }

            EdtExecutor.INSTANCE.execute(new Runnable() {
              @Override
              public void run() {
                // Back in the UI thread here
                myDevices = new DeviceEntry[paths.length];
                for (int i = 0; i < paths.length; i++) {
                  myDevices[i] = new DeviceEntry(paths[i], devices.get(i));
                }
                if (paths.length > 0 && mySelectedDevice.update(paths[0]) && mySelectedDevice.getPath() != null) {
                  myEditor.activatePath(mySelectedDevice.getPath());
                }
              }
            });
          }
        });
      }
    });
  }

  @NotNull
  private AnAction getContextAction() {
    return new ComboBoxAction() {
      @NotNull
      @Override
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        DefaultActionGroup group = new DefaultActionGroup();
        if (myDevices != null && myDevices.length > 0) {
          for (final DeviceEntry device : myDevices) {
            group.add(new AnAction(device.toString()) {
              @Override
              public void actionPerformed(AnActionEvent e) {
                if (mySelectedDevice.update(device.myPath) && mySelectedDevice.getPath() != null) {
                  myEditor.activatePath(mySelectedDevice.getPath());
                }
              }
            });
          }
        }
        return group;
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        if (mySelectedDevice.isValid() && myDevices != null) {
          for (DeviceEntry device : myDevices) {
            if (mySelectedDevice.is(device.myPath)) {
              getTemplatePresentation().setText(device.toString());
              e.getPresentation().setText(device.toString());
              break;
            }
          }
          getTemplatePresentation().setEnabled(true);
          e.getPresentation().setEnabled(true);
        }
        else if (myDevices == null) {
          getTemplatePresentation().setText(RETRIEVING_DEVICES);
          getTemplatePresentation().setEnabled(false);
          e.getPresentation().setText(RETRIEVING_DEVICES);
          e.getPresentation().setEnabled(false);
        }
        else if (myDevices.length == 0) {
          getTemplatePresentation().setText(NO_DEVICE_AVAILABLE);
          getTemplatePresentation().setEnabled(false);
          e.getPresentation().setText(NO_DEVICE_AVAILABLE);
          e.getPresentation().setEnabled(false);
        }
        else {
          getTemplatePresentation().setText(NO_DEVICE_SELECTED);
          getTemplatePresentation().setEnabled(true);
          e.getPresentation().setText(NO_DEVICE_SELECTED);
          e.getPresentation().setEnabled(true);
        }
      }
    };
  }

  @Override
  public void notifyPath(Path path) {
    if (path instanceof DevicePath) {
      if (mySelectedDevice.update((DevicePath)path)) {
        if (myDevices != null) {
          for (DeviceEntry myDevice : myDevices) {
            if (mySelectedDevice.is(myDevice.myPath)) {
              return;
            }
          }
          mySelectedDevice.update(null);
        }
      }
    }
  }

  private static class DeviceEntry {
    public DevicePath myPath;
    public Device myDevice;

    public DeviceEntry(DevicePath path, Device device) {
      myPath = path;
      myDevice = device;
    }

    @Override
    public String toString() {
      return myDevice.getName() + " (" + myDevice.getModel() + ", " + myDevice.getOS() + ")";
    }
  }
}
