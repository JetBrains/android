/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.run;

import static com.intellij.openapi.util.text.StringUtil.capitalize;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.ddms.DeviceNameProperties;
import com.android.tools.idea.ddms.DeviceNamePropertiesFetcher;
import com.android.tools.idea.ddms.DeviceRenderer;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.run.LaunchCompatibility.State;
import com.google.common.base.Predicate;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.JBTable;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import gnu.trove.TIntArrayList;
import java.awt.FontMetrics;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AvdManagerUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeviceChooser implements Disposable, AndroidDebugBridge.IDebugBridgeChangeListener, AndroidDebugBridge.IDeviceChangeListener {
  private final static int UPDATE_DELAY_MILLIS = 250;
  private static final String[] COLUMN_TITLES = new String[]{"Device", "State", "Compatible", "Serial Number"};
  private static final int DEVICE_NAME_COLUMN_INDEX = 0;
  private static final int DEVICE_STATE_COLUMN_INDEX = 1;
  private static final int COMPATIBILITY_COLUMN_INDEX = 2;
  private static final int SERIAL_COLUMN_INDEX = 3;

  public static final IDevice[] EMPTY_DEVICE_ARRAY = new IDevice[0];

  private final List<DeviceChooserListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final MergingUpdateQueue myUpdateQueue;

  private volatile boolean myProcessSelectionFlag = true;

  private final JComponent myPanel;
  private final JBTable myDeviceTable;

  private final Predicate<IDevice> myFilter;
  private final ListenableFuture<AndroidVersion> myMinSdkVersion;
  private final AndroidFacet myFacet;
  private final IAndroidTarget myProjectTarget;
  private final Set<Abi> mySupportedAbis;

  private int[] mySelectedRows;
  private final AtomicBoolean myDevicesDetected = new AtomicBoolean();

  public DeviceChooser(boolean multipleSelection,
                       @NotNull final Action okAction,
                       @NotNull AndroidFacet facet,
                       @NotNull IAndroidTarget projectTarget,
                       @Nullable Predicate<IDevice> filter) {
    myFacet = facet;
    myFilter = filter;
    myMinSdkVersion = AndroidModuleInfo.getInstance(facet).getRuntimeMinSdkVersion();
    myProjectTarget = projectTarget;
    AndroidModel androidModel = AndroidModel.get(facet);
    mySupportedAbis = androidModel != null ?
                      androidModel.getSupportedAbis() :
                      Collections.emptySet();

    myDeviceTable = new JBTable();
    myPanel = ScrollPaneFactory.createScrollPane(myDeviceTable);
    myPanel.setPreferredSize(JBUI.size(550, 220));

    MyDeviceTableModel tableModel = new MyDeviceTableModel(EMPTY_DEVICE_ARRAY);
    myDeviceTable.setModel(tableModel);
    myDeviceTable
      .setSelectionMode(multipleSelection ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
    myDeviceTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (myProcessSelectionFlag) {
          fireSelectedDevicesChanged();
        }
      }
    });
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        if (myDeviceTable.isEnabled() && okAction.isEnabled()) {
          okAction.actionPerformed(null);
          return true;
        }
        return false;
      }
    }.installOn(myDeviceTable);

    myDeviceTable.setDefaultRenderer(LaunchCompatibility.class, new LaunchCompatibilityRenderer());
    myDeviceTable.setDefaultRenderer(IDevice.class, new DeviceRenderer.DeviceNameRenderer(
      AvdManagerUtils.getAvdManager(facet),
      new DeviceNamePropertiesFetcher(this, new FutureCallback<DeviceNameProperties>() {
        @Override
        public void onSuccess(@Nullable DeviceNameProperties result) {
          updateTable();
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          Logger.getInstance(DeviceChooser.class).warn("Error retrieving device name properties", t);
        }
      })));
    myDeviceTable.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && okAction.isEnabled()) {
          okAction.actionPerformed(null);
        }
      }
    });
    myDeviceTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON3) {
          int i = myDeviceTable.rowAtPoint(e.getPoint());
          if (i >= 0) {
            Object serial = myDeviceTable.getValueAt(i, SERIAL_COLUMN_INDEX);
            final String serialString = serial.toString();
            // Add a menu to copy the serial key.
            JBPopupMenu popupMenu = new JBPopupMenu();
            Action action = new AbstractAction("Copy Serial Number") {
              @Override
              public void actionPerformed(ActionEvent e) {
                CopyPasteManager.getInstance().setContents(new StringSelection(serialString));
              }
            };
            popupMenu.add(action);
            popupMenu.show(e.getComponent(), e.getX(), e.getY());
          }
        }
        super.mouseReleased(e);
      }
    });
    setColumnWidth(myDeviceTable, DEVICE_NAME_COLUMN_INDEX, "Samsung Galaxy Nexus Android 4.1 (API 17)");
    setColumnWidth(myDeviceTable, DEVICE_STATE_COLUMN_INDEX, "offline");
    setColumnWidth(myDeviceTable, COMPATIBILITY_COLUMN_INDEX, "Compatible");
    setColumnWidth(myDeviceTable, SERIAL_COLUMN_INDEX, "123456");

    // Do not recreate columns on every model update - this should help maintain the column sizes set above
    myDeviceTable.setAutoCreateColumnsFromModel(false);

    // Allow sorting by columns (in lexicographic order)
    myDeviceTable.setAutoCreateRowSorter(true);

    // the device change notifications from adb can sometimes be noisy (esp. when a device is [dis]connected)
    // we use this merging queue to collapse multiple updates to one
    myUpdateQueue = new MergingUpdateQueue("android.device.chooser", UPDATE_DELAY_MILLIS, true, null, this, null,
                                           Alarm.ThreadToUse.POOLED_THREAD);

    AndroidDebugBridge.addDebugBridgeChangeListener(this);
    AndroidDebugBridge.addDeviceChangeListener(this);

    myMinSdkVersion.addListener(() -> tableModel.fireTableDataChanged(), ApplicationManager.getApplication()::invokeLater);
  }

  private static void setColumnWidth(JBTable deviceTable, int columnIndex, String sampleText) {
    int width = getWidth(deviceTable, sampleText);
    deviceTable.getColumnModel().getColumn(columnIndex).setPreferredWidth(width);
  }

  private static int getWidth(JBTable deviceTable, String sampleText) {
    FontMetrics metrics = deviceTable.getFontMetrics(deviceTable.getFont());
    return metrics.stringWidth(sampleText);
  }

  public void init(@Nullable String[] selectedSerials) {
    updateTable();
    if (selectedSerials != null) {
      resetSelection(selectedSerials);
    }
  }

  private void resetSelection(@NotNull String[] selectedSerials) {
    MyDeviceTableModel model = (MyDeviceTableModel)myDeviceTable.getModel();
    Set<String> selectedSerialsSet = new HashSet<>();
    Collections.addAll(selectedSerialsSet, selectedSerials);
    IDevice[] myDevices = model.myDevices;
    ListSelectionModel selectionModel = myDeviceTable.getSelectionModel();
    boolean cleared = false;

    for (int i = 0, n = myDevices.length; i < n; i++) {
      String serialNumber = myDevices[i].getSerialNumber();
      if (selectedSerialsSet.contains(serialNumber)) {
        if (!cleared) {
          selectionModel.clearSelection();
          cleared = true;
        }
        selectionModel.addSelectionInterval(i, i);
      }
    }
  }

  private void updateTable() {
    final IDevice[] devices = getFilteredDevices();
    if (devices.length > 1) {
      // sort by API level
      Arrays.sort(devices, new Comparator<IDevice>() {
        @Override
        public int compare(IDevice device1, IDevice device2) {
          int apiLevel1 = safeGetApiLevel(device1);
          int apiLevel2 = safeGetApiLevel(device2);
          return apiLevel2 - apiLevel1;
        }

        private int safeGetApiLevel(IDevice device) {
          try {
            String s = device.getProperty(IDevice.PROP_BUILD_API_LEVEL);
            return StringUtil.isNotEmpty(s) ? Integer.parseInt(s) : 0;
          }
          catch (Exception e) {
            return 0;
          }
        }
      });
    }

    UIUtil.invokeLaterIfNeeded(() -> {
      myDevicesDetected.set(devices.length > 0);
      refreshTable(devices);
    });
  }

  private void refreshTable(IDevice[] devices) {
    final IDevice[] selectedDevices = getSelectedDevices(false);
    final TIntArrayList selectedRows = new TIntArrayList();
    for (int i = 0; i < devices.length; i++) {
      if (ArrayUtil.indexOf(selectedDevices, devices[i]) >= 0) {
        selectedRows.add(i);
      }
    }

    myProcessSelectionFlag = false;
    myDeviceTable.setModel(new MyDeviceTableModel(devices));
    if (selectedRows.isEmpty() && devices.length > 0) {
      myDeviceTable.getSelectionModel().setSelectionInterval(0, 0);
    }
    for (int selectedRow : selectedRows.toNativeArray()) {
      if (selectedRow < devices.length) {
        myDeviceTable.getSelectionModel().addSelectionInterval(selectedRow, selectedRow);
      }
    }
    fireSelectedDevicesChanged();
    myProcessSelectionFlag = true;
  }

  public boolean hasDevices() {
    return myDevicesDetected.get();
  }

  public JComponent getPreferredFocusComponent() {
    return myDeviceTable;
  }

  @Nullable
  public JComponent getPanel() {
    return myPanel;
  }

  @Nullable
  public ValidationInfo doValidate() {
    if (!myDeviceTable.isEnabled()) {
      return null;
    }

    int[] rows = mySelectedRows != null ? mySelectedRows : myDeviceTable.getSelectedRows();
    boolean hasIncompatible = false;
    boolean hasCompatible = false;
    for (int row : rows) {
      if (!isRowCompatible(row)) {
        hasIncompatible = true;
      }
      else {
        hasCompatible = true;
      }
    }
    if (!hasIncompatible) {
      return null;
    }
    String message;
    if (hasCompatible) {
      message = "At least one of the selected devices is incompatible. Will only install on compatible devices.";
    }
    else {
      String devicesAre = rows.length > 1 ? "devices are" : "device is";
      message = "The selected " + devicesAre + " incompatible.";
    }
    return new ValidationInfo(message);
  }

  @NotNull
  public IDevice[] getSelectedDevices() {
    return getSelectedDevices(true);
  }

  @NotNull
  private IDevice[] getSelectedDevices(boolean onlyCompatible) {
    int[] rows = mySelectedRows != null ? mySelectedRows : myDeviceTable.getSelectedRows();
    List<IDevice> result = new ArrayList<>();
    for (int row : rows) {
      if (row >= 0) {
        if (onlyCompatible && !isRowCompatible(row)) {
          continue;
        }
        Object serial = myDeviceTable.getValueAt(row, SERIAL_COLUMN_INDEX);
        IDevice[] devices = getFilteredDevices();
        for (IDevice device : devices) {
          if (device.getSerialNumber().equals(serial.toString())) {
            result.add(device);
            break;
          }
        }
      }
    }
    return result.toArray(new IDevice[0]);
  }

  @NotNull
  private IDevice[] getFilteredDevices() {
    AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
    if (bridge == null || !bridge.isConnected()) {
      return EMPTY_DEVICE_ARRAY;
    }

    final List<IDevice> filteredDevices = new ArrayList<>();
    for (IDevice device : bridge.getDevices()) {
      if (myFilter == null || myFilter.apply(device)) {
        filteredDevices.add(device);
      }
    }

    return filteredDevices.toArray(new IDevice[0]);
  }

  private boolean isRowCompatible(int row) {
    // Use the value already computed in the table to avoid having to compute it again.
    Object compatibility = myDeviceTable.getValueAt(row, COMPATIBILITY_COLUMN_INDEX);
    return compatibility instanceof LaunchCompatibility && ((LaunchCompatibility)compatibility).getState() != State.ERROR;
  }

  public void finish() {
    mySelectedRows = myDeviceTable.getSelectedRows();
  }

  @Override
  public void dispose() {
    AndroidDebugBridge.removeDebugBridgeChangeListener(this);
    AndroidDebugBridge.removeDeviceChangeListener(this);
  }

  public void setEnabled(boolean enabled) {
    myDeviceTable.setEnabled(enabled);
  }

  @NotNull
  private static String getDeviceState(@NotNull IDevice device) {
    IDevice.DeviceState state = device.getState();
    return state != null ? capitalize(StringUtil.toLowerCase(state.name())) : "";
  }

  private void fireSelectedDevicesChanged() {
    for (DeviceChooserListener listener : myListeners) {
      listener.selectedDevicesChanged();
    }
  }

  public void addListener(@NotNull DeviceChooserListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void bridgeChanged(AndroidDebugBridge bridge) {
    postUpdate();
  }

  @Override
  public void deviceConnected(@NotNull IDevice device) {
    postUpdate();
  }

  @Override
  public void deviceDisconnected(@NotNull IDevice device) {
    postUpdate();
  }

  @Override
  public void deviceChanged(@NotNull IDevice device, int changeMask) {
    postUpdate();
  }

  private void postUpdate() {
    myUpdateQueue.queue(new Update("updateTable") {
      @Override
      public void run() {
        updateTable();
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
  }

  private class MyDeviceTableModel extends AbstractTableModel {
    private final IDevice[] myDevices;

    public MyDeviceTableModel(IDevice[] devices) {
      myDevices = devices;
    }

    @Override
    public String getColumnName(int column) {
      return COLUMN_TITLES[column];
    }

    @Override
    public int getRowCount() {
      return myDevices.length;
    }

    @Override
    public int getColumnCount() {
      return COLUMN_TITLES.length;
    }

    @Override
    @Nullable
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex >= myDevices.length) {
        return null;
      }
      IDevice device = myDevices[rowIndex];
      switch (columnIndex) {
        case DEVICE_NAME_COLUMN_INDEX:
          return device;
        case SERIAL_COLUMN_INDEX:
          return device.getSerialNumber();
        case DEVICE_STATE_COLUMN_INDEX:
          return getDeviceState(device);
        case COMPATIBILITY_COLUMN_INDEX:
          // This value is also used in the method isRowCompatible(). Update that if there's a change here.
          AndroidDevice connectedDevice = new ConnectedAndroidDevice(device);
          try {
            return myMinSdkVersion.isDone() ? connectedDevice
              .canRun(myMinSdkVersion.get(), myProjectTarget, myFacet, LaunchCompatibilityCheckerImpl::getRequiredHardwareFeatures,
                      mySupportedAbis) : false;
          }
          catch (InterruptedException | ExecutionException e) {
            return false;
          }
      }
      return null;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      if (columnIndex == COMPATIBILITY_COLUMN_INDEX) {
        return LaunchCompatibility.class;
      }
      else if (columnIndex == DEVICE_NAME_COLUMN_INDEX) {
        return IDevice.class;
      }
      else {
        return String.class;
      }
    }
  }

  private static class LaunchCompatibilityRenderer extends ColoredTableCellRenderer {
    @Override
    protected void customizeCellRenderer(@NotNull JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      if (!(value instanceof LaunchCompatibility)) {
        return;
      }

      LaunchCompatibility compatibility = (LaunchCompatibility)value;
      State state = compatibility.getState();
      if (state == State.OK) {
        append("Yes");
      }
      else {
        if (state == State.ERROR) {
          append("No", SimpleTextAttributes.ERROR_ATTRIBUTES);
        }
        else {
          append("Maybe");
        }
        String reason = compatibility.getReason();
        if (reason != null) {
          append(", ");
          append(reason);
        }
      }
    }
  }
}
