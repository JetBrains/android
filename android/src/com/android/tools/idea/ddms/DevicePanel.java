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

package com.android.tools.idea.ddms;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.screenshot.ScreenshotTask;
import com.android.tools.idea.ddms.screenshot.ScreenshotViewer;
import com.android.utils.Pair;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DevicePanel implements Disposable,
                                    AndroidDebugBridge.IClientChangeListener,
                                    AndroidDebugBridge.IDeviceChangeListener {
  private static final String NO_DEVICES = "No Connected Devices";
  private JPanel myPanel;
  private JComboBox myDevicesComboBox;
  private JBList myClientsList;

  private final DefaultComboBoxModel myComboBoxModel = new DefaultComboBoxModel();
  private final CollectionListModel<Client> myClientsListModel = new CollectionListModel<Client>();

  private final DeviceContext myDeviceContext;
  private final Project myProject;
  private AndroidDebugBridge myBridge;

  public DevicePanel(@NotNull Project project, @NotNull DeviceContext context) {
    myProject = project;
    myDeviceContext = context;
    Disposer.register(myProject, this);

    if (!AndroidSdkUtils.activateDdmsIfNecessary(project, new Computable<AndroidDebugBridge>() {
      @Nullable
      @Override
      public AndroidDebugBridge compute() {
        return AndroidSdkUtils.getDebugBridge(myProject);
      }
    })) {
      myBridge = null;
      return;
    }

    myBridge = AndroidSdkUtils.getDebugBridge(myProject);
    if (myBridge == null) {
      return;
    }

    myBridge.addDeviceChangeListener(this);
    myBridge.addClientChangeListener(this);

    initializeDeviceCombo();
    initializeClientsList();
  }

  private void initializeDeviceCombo() {
    myDevicesComboBox.setModel(myComboBoxModel);
    myDevicesComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Object sel = myDevicesComboBox.getSelectedItem();
        IDevice device = (sel instanceof IDevice) ? (IDevice)sel : null;
        updateClientsForDevice(device);
        myDeviceContext.fireDeviceSelected(device);
        myDeviceContext.fireClientSelected(null);
      }
    });
    myDevicesComboBox.setRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list,
                                           Object value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        if (value instanceof String) {
          append((String)value, SimpleTextAttributes.ERROR_ATTRIBUTES);
        } else if (value instanceof IDevice) {
          IDevice d = (IDevice)value;
          setIcon(d.isEmulator() ? AndroidIcons.Ddms.Emulator2 : AndroidIcons.Ddms.RealDevice);
          List<Pair<String, SimpleTextAttributes>> components = renderDeviceName(d);
          for (Pair<String, SimpleTextAttributes> c : components) {
            append(c.getFirst(), c.getSecond());
          }
        }
      }
    });

    IDevice[] devices = myBridge.getDevices();
    if (devices.length == 0) {
      myComboBoxModel.addElement(NO_DEVICES);
    } else {
      for (IDevice device : devices) {
        myComboBoxModel.addElement(device);
      }
    }
    myDevicesComboBox.setSelectedIndex(0);
  }

  private void initializeClientsList() {
    myClientsList.setModel(myClientsListModel);
    myClientsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myClientsList.setEmptyText("No debuggable applications");
    myClientsList.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list,
                                           Object value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        if (!(value instanceof Client)) {
          return;
        }

        Client c = (Client)value;
        ClientData cd = c.getClientData();
        String name = cd.getClientDescription();
        if (name != null) {
          List<Pair<String, SimpleTextAttributes>> nameComponents = renderAppName(name);
          for (Pair<String, SimpleTextAttributes> component: nameComponents) {
            append(component.getFirst(), component.getSecond());
          }
        }

        append(String.format(" (%d)", cd.getPid()), SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    });
    new ListSpeedSearch(myClientsList) {
      @Override
      protected boolean isMatchingElement(Object element, String pattern) {
        if (element instanceof Client) {
          String pkg = ((Client)element).getClientData().getClientDescription();
          return pkg != null && pkg.contains(pattern);
        }
        return false;
      }
    };
    myClientsList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        Object sel = myClientsList.getSelectedValue();
        Client c = (sel instanceof Client) ? (Client)sel : null;
        myDeviceContext.fireClientSelected(c);
      }
    });
  }

  @VisibleForTesting
  static List<Pair<String, SimpleTextAttributes>> renderAppName(String name) {
    int index = name.lastIndexOf('.');
    if (index == -1) {
      return Collections.singletonList(Pair.of(name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));
    } else {
      List<Pair<String, SimpleTextAttributes>> components = new ArrayList<Pair<String, SimpleTextAttributes>>(2);
      components.add(Pair.of(name.substring(0, index + 1), SimpleTextAttributes.REGULAR_ATTRIBUTES));
      if (index < name.length() - 1) {
        components.add(Pair.of(name.substring(index + 1), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));
      }
      return components;
    }
  }

  public static List<Pair<String, SimpleTextAttributes>> renderDeviceName(IDevice d) {
    List<Pair<String, SimpleTextAttributes>> components = new ArrayList<Pair<String, SimpleTextAttributes>>(3);
    String name;
    if (d.isEmulator()) {
      name = String.format("%1$s %2$s ", AndroidBundle.message("android.emulator"), d.getAvdName());
    } else {
      name = String.format("%1$s %2$s ", DevicePropertyUtil.getManufacturer(d, ""), DevicePropertyUtil.getModel(d, ""));
    }

    components.add(Pair.of(name, SimpleTextAttributes.REGULAR_ATTRIBUTES));

    if (d.getState() != IDevice.DeviceState.ONLINE) {
      String state = String.format("%1$s [%2$s] ", d.getSerialNumber(), d.getState());
      components.add(Pair.of(state, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES));
    }

    components.add(Pair.of(DevicePropertyUtil.getBuild(d), SimpleTextAttributes.GRAY_ATTRIBUTES));

    return components;
  }

  @Override
  public void dispose() {
    if (myBridge != null) {
      AndroidDebugBridge.removeDeviceChangeListener(this);
      AndroidDebugBridge.removeClientChangeListener(this);

      myBridge = null;
    }
  }

  public JPanel getContentPanel() {
    return myPanel;
  }

  @Override
  public void clientChanged(Client client, int changeMask) {
  }

  @Override
  public void deviceConnected(final IDevice device) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myComboBoxModel.removeElement(NO_DEVICES);
        myComboBoxModel.addElement(device);
      }
    });
  }

  @Override
  public void deviceDisconnected(final IDevice device) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myComboBoxModel.removeElement(device);
        if (myComboBoxModel.getSize() == 0) {
          myComboBoxModel.addElement(NO_DEVICES);
        }
      }
    });
  }

  @Override
  public void deviceChanged(final IDevice device, final int changeMask) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            myDevicesComboBox.repaint();
          }
        });

        if (!myDevicesComboBox.getSelectedItem().equals(device)) {
          return;
        }

        if ((changeMask & IDevice.CHANGE_CLIENT_LIST) == IDevice.CHANGE_CLIENT_LIST) {
          updateClientsForDevice(device);
        }

        myDeviceContext.fireDeviceChanged(device, changeMask);
      }
    });
  }

  private void updateClientsForDevice(@Nullable IDevice device) {
    myClientsListModel.removeAll();

    if (device == null) {
      return;
    }

    for (Client c: device.getClients()) {
      myClientsListModel.add(c);
    }
  }

  @NotNull
  public ActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new MyScreenshotAction());
    //group.add(new MyFileExplorerAction());
    //group.add(new Separator());

    group.add(new MyTerminateVMAction());
    group.add(new MyGcAction());
    //group.add(new MyDumpHprofAction());
    //group.add(new MyAllocationTrackerAction());
    //group.add(new Separator());

    //group.add(new MyToggleMethodProfilingAction());
    //group.add(new MyThreadDumpAction()); // thread dump -> systrace
    return group;
  }

  private abstract class MyClientAction extends AnAction {
    public MyClientAction(@Nullable String text,
                          @Nullable String description,
                          @Nullable Icon icon) {
      super(text, description, icon);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myDeviceContext.getSelectedClient() != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Client c = myDeviceContext.getSelectedClient();
      if (c != null) {
        performAction(c);
      }
    }

    abstract void performAction(@NotNull Client c);
  }

  private class MyTerminateVMAction extends MyClientAction {
    public MyTerminateVMAction() {
      super(AndroidBundle.message("android.ddms.actions.terminate.vm"),
            AndroidBundle.message("android.ddms.actions.terminate.vm.description"),
            AllIcons.Process.Stop);
    }

    @Override
    void performAction(@NotNull Client c) {
      c.kill();
    }
  }

  private class MyGcAction extends MyClientAction {
    public MyGcAction() {
      super(AndroidBundle.message("android.ddms.actions.initiate.gc"),
            AndroidBundle.message("android.ddms.actions.initiate.gc.description"),
            AndroidIcons.Ddms.Gc); // Alternate: AllIcons.Actions.GC
    }

    @Override
    void performAction(@NotNull Client c) {
      c.executeGarbageCollector();
    }
  }

  private class MyScreenshotAction extends AnAction {
    public MyScreenshotAction() {
      super(AndroidBundle.message("android.ddms.actions.screenshot"),
            AndroidBundle.message("android.ddms.actions.screenshot.description"),
            AndroidIcons.Ddms.ScreenCapture); // Alternate: AllIcons.Actions.Dump looks like a camera
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myDeviceContext.getSelectedDevice() != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final IDevice d = myDeviceContext.getSelectedDevice();
      if (d == null) {
        return;
      }

      final Project project = myProject;

      new ScreenshotTask(project, d) {
        @Override
        public void onSuccess() {
          String msg = getError();
          if (msg != null) {
            Messages.showErrorDialog(project, msg, AndroidBundle.message("android.ddms.actions.screenshot"));
            return;
          }

          try {
            File backingFile = FileUtil.createTempFile("screenshot", SdkConstants.DOT_PNG, true);
            ImageIO.write(getScreenshot(), SdkConstants.EXT_PNG, backingFile);

            ScreenshotViewer viewer = new ScreenshotViewer(project, getScreenshot(), backingFile, d);
            if (viewer.showAndGet()) {
              File screenshot = viewer.getScreenshot();
              VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(screenshot);
              if (vf != null) {
                FileEditorManager.getInstance(project).openFile(vf, true);
              }
            }
          }
          catch (Exception e) {
            Messages.showErrorDialog(project,
                                     AndroidBundle.message("android.ddms.screenshot.generic.error", e),
                                     AndroidBundle.message("android.ddms.actions.screenshot"));
          }
        }
      }.queue();
    }
  }
}
