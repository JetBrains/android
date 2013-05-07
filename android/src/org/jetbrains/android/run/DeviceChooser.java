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

package org.jetbrains.android.run;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DevicePanel;
import com.android.utils.Pair;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.JBTable;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.TIntArrayList;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.BooleanCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.capitalize;

/**
 * @author Eugene.Kudelevsky
 */
public class DeviceChooser implements Disposable {
  private static final String[] COLUMN_TITLES = new String[]{"Device", "Serial Number", "State", "Compatible"};
  private static final int DEVICE_NAME_COLUMN_INDEX = 0;
  private static final int SERIAL_COLUMN_INDEX = 1;
  private static final int DEVICE_STATE_COLUMN_INDEX = 2;
  private static final int COMPATIBILITY_COLUMN_INDEX = 3;

  public static final IDevice[] EMPTY_DEVICE_ARRAY = new IDevice[0];

  private final List<DeviceChooserListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Alarm myRefreshingAlarm = new Alarm(this);

  private volatile boolean myProcessSelectionFlag = true;
  private IDevice[] myOldDevices = EMPTY_DEVICE_ARRAY;

  private JComponent myPanel;
  private JBTable myDeviceTable;

  private final AndroidFacet myFacet;
  private final Condition<IDevice> myFilter;

  private int[] mySelectedRows;

  public DeviceChooser(boolean multipleSelection,
                       @NotNull final Action okAction,
                       @NotNull AndroidFacet facet,
                       @Nullable Condition<IDevice> filter) {
    myFacet = facet;
    myFilter = filter;
    
    myDeviceTable = new JBTable();
    myPanel = ScrollPaneFactory.createScrollPane(myDeviceTable);
    myPanel.setPreferredSize(new Dimension(450, 220));

    myDeviceTable.setModel(new MyDeviceTableModel(EMPTY_DEVICE_ARRAY));
    myDeviceTable.setSelectionMode(multipleSelection ?
                                   ListSelectionModel.MULTIPLE_INTERVAL_SELECTION :
                                   ListSelectionModel.SINGLE_SELECTION);
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

    myDeviceTable.setDefaultRenderer(Boolean.class, new BooleanCellRenderer());
    myDeviceTable.setDefaultRenderer(IDevice.class, new DeviceNameRenderer());
    myDeviceTable.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && okAction.isEnabled()) {
          okAction.actionPerformed(null);
        }
      }
    });

    setColumnWidth(myDeviceTable, DEVICE_NAME_COLUMN_INDEX, "Samsung Galaxy Nexus Android 4.1 (API 17)");
    setColumnWidth(myDeviceTable, SERIAL_COLUMN_INDEX, "0000-0000-00000");
    setColumnWidth(myDeviceTable, DEVICE_STATE_COLUMN_INDEX, "offline");
    setColumnWidth(myDeviceTable, COMPATIBILITY_COLUMN_INDEX, "yes");

    // Do not recreate columns on every model update - this should help maintain the column sizes set above
    myDeviceTable.setAutoCreateColumnsFromModel(false);
  }

  private void setColumnWidth(JBTable deviceTable, int columnIndex, String sampleText) {
    int width = getWidth(deviceTable, sampleText);
    deviceTable.getColumnModel().getColumn(columnIndex).setPreferredWidth(width);
  }

  private int getWidth(JBTable deviceTable, String sampleText) {
    FontMetrics metrics = deviceTable.getFontMetrics(deviceTable.getFont());
    return metrics.stringWidth(sampleText);
  }

  public void init(@Nullable String[] selectedSerials) {
    updateTable();
    if (selectedSerials != null) {
      resetSelection(selectedSerials);
    }
    addUpdatingRequest();
  }

  private void addUpdatingRequest() {
    if (myRefreshingAlarm.isDisposed()) {
      return;
    }
    myRefreshingAlarm.cancelAllRequests();
    myRefreshingAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        updateTable();
        addUpdatingRequest();
      }
    }, 500, ModalityState.stateForComponent(myPanel));
  }

  private void resetSelection(@NotNull String[] selectedSerials) {
    MyDeviceTableModel model = (MyDeviceTableModel)myDeviceTable.getModel();
    Set<String> selectedSerialsSet = new HashSet<String>();
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

  void updateTable() {
    final AndroidDebugBridge bridge = myFacet.getDebugBridge();
    IDevice[] devices = bridge != null ? getFilteredDevices(bridge) : EMPTY_DEVICE_ARRAY;
    if (!Arrays.equals(myOldDevices, devices)) {
      myOldDevices = devices;
      final IDevice[] selectedDevices = getSelectedDevices();
      final TIntArrayList selectedRows = new TIntArrayList();
      for (int i = 0; i < devices.length; i++) {
        if (ArrayUtil.indexOf(selectedDevices, devices[i]) >= 0) {
          selectedRows.add(i);
        }
      }

      myProcessSelectionFlag = false;
      myDeviceTable.setModel(new MyDeviceTableModel(devices));
      if (selectedRows.size() == 0 && devices.length > 0) {
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
  }

  public JTable getDeviceTable() {
    return myDeviceTable;
  }

  @Nullable
  public JComponent getPanel() {
    return myPanel;
  }

  @NotNull
  public IDevice[] getSelectedDevices() {
    int[] rows = mySelectedRows != null ? mySelectedRows : myDeviceTable.getSelectedRows();
    List<IDevice> result = new ArrayList<IDevice>();
    for (int row : rows) {
      if (row >= 0) {
        Object serial = myDeviceTable.getValueAt(row, SERIAL_COLUMN_INDEX);
        final AndroidDebugBridge bridge = myFacet.getDebugBridge();
        if (bridge == null) {
          return EMPTY_DEVICE_ARRAY;
        }
        IDevice[] devices = getFilteredDevices(bridge);
        for (IDevice device : devices) {
          if (device.getSerialNumber().equals(serial.toString())) {
            result.add(device);
            break;
          }
        }
      }
    }
    return result.toArray(new IDevice[result.size()]);
  }

  @NotNull
  private IDevice[] getFilteredDevices(AndroidDebugBridge bridge) {
    final IDevice[] devices = bridge.getDevices();
    if (devices.length == 0 || myFilter == null) {
      return devices;
    }

    final List<IDevice> filteredDevices = new ArrayList<IDevice>();
    for (IDevice device : devices) {
      if (myFilter.value(device)) {
        filteredDevices.add(device);
      }
    }
    return filteredDevices.toArray(new IDevice[filteredDevices.size()]);
  }

  public void finish() {
    mySelectedRows = myDeviceTable.getSelectedRows();
  }

  @Override
  public void dispose() {
  }

  public void setEnabled(boolean enabled) {
    myDeviceTable.setEnabled(enabled);
  }

  @NotNull
  private static String getDeviceState(@NotNull IDevice device) {
    IDevice.DeviceState state = device.getState();
    return state != null ? capitalize(state.name().toLowerCase()) : "";
  }

  public void fireSelectedDevicesChanged() {
    for (DeviceChooserListener listener : myListeners) {
      listener.selectedDevicesChanged();
    }
  }

  public void addListener(@NotNull DeviceChooserListener listener) {
    myListeners.add(listener);
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
          return myFacet.isCompatibleDevice(device);
      }
      return null;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      if (columnIndex == COMPATIBILITY_COLUMN_INDEX) {
        return Boolean.class;
      } else if (columnIndex == DEVICE_NAME_COLUMN_INDEX) {
        return IDevice.class;
      } else {
        return String.class;
      }
    }
  }

  private static class DeviceNameRenderer extends ColoredTableCellRenderer {
    @Override
    protected void customizeCellRenderer(JTable table,
                                         Object value,
                                         boolean selected,
                                         boolean hasFocus,
                                         int row,
                                         int column) {
      if (!(value instanceof IDevice)) {
        return;
      }

      IDevice device = (IDevice)value;
      setIcon(device.isEmulator() ? AndroidIcons.Ddms.Emulator2 : AndroidIcons.Ddms.RealDevice);
      List<Pair<String, SimpleTextAttributes>> l = DevicePanel.renderDeviceName(device);
      for (Pair<String, SimpleTextAttributes> component : l) {
        append(component.getFirst(), component.getSecond());
      }
    }
  }
}
