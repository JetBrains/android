/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.devicemanager.Device;
import com.google.common.annotations.VisibleForTesting;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.NotNull;

@UiThread
final class PhysicalDeviceTableModel extends AbstractTableModel {
  static final boolean SPLIT_ACTIONS_ENABLED = false;

  static final int DEVICE_MODEL_COLUMN_INDEX = 0;
  static final int API_MODEL_COLUMN_INDEX = 1;
  static final int TYPE_MODEL_COLUMN_INDEX = 2;
  static final int ACTIONS_MODEL_COLUMN_INDEX = 3;

  private static final int ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX = 3;
  private static final int REMOVE_MODEL_COLUMN_INDEX = 4;
  private static final int MORE_MODEL_COLUMN_INDEX = 5;

  private @NotNull List<@NotNull PhysicalDevice> myDevices;
  private @NotNull List<@NotNull PhysicalDevice> myCombinedDevices;

  /**
   * Supplies a key for JTable.defaultRenderersByColumnClass and ActionsComponent
   */
  static final class Actions {
    @SuppressWarnings("InstantiationOfUtilityClass")
    static final Actions INSTANCE = new Actions();

    private Actions() {
    }
  }

  static final class ActivateDeviceFileExplorerWindowValue {
    @SuppressWarnings("InstantiationOfUtilityClass")
    static final ActivateDeviceFileExplorerWindowValue INSTANCE = new ActivateDeviceFileExplorerWindowValue();

    private ActivateDeviceFileExplorerWindowValue() {
    }
  }

  static final class RemoveValue {
    @SuppressWarnings("InstantiationOfUtilityClass")
    static final RemoveValue INSTANCE = new RemoveValue();

    private RemoveValue() {
    }
  }

  private static final class MoreValue {
    private static final MoreValue INSTANCE = new MoreValue();

    private MoreValue() {
    }
  }

  PhysicalDeviceTableModel() {
    this(Collections.emptyList());
  }

  @VisibleForTesting
  PhysicalDeviceTableModel(@NotNull List<@NotNull PhysicalDevice> devices) {
    myDevices = devices;
    myCombinedDevices = Collections.emptyList();

    combineDevices();
  }

  @NotNull Collection<@NotNull PhysicalDevice> getDevices() {
    return myDevices;
  }

  void setDevices(@NotNull List<@NotNull PhysicalDevice> devices) {
    myDevices = devices;

    combineDevices();
    fireTableDataChanged();
  }

  void addOrSet(@NotNull PhysicalDevice device) {
    int modelRowIndex = PhysicalDevices.indexOf(myDevices, device);

    if (modelRowIndex == -1) {
      myDevices.add(device);
    }
    else {
      myDevices.set(modelRowIndex, device);
    }

    combineDevices();
    fireTableDataChanged();
  }

  void setNameOverride(@NotNull Key key, @NotNull String nameOverride) {
    for (int i = 0, size = myDevices.size(); i < size; i++) {
      PhysicalDevice device = myDevices.get(i);
      Key k = device.getKey();

      if (!(k.equals(key) || k.getSerialNumber().equals(key))) {
        continue;
      }

      PhysicalDevice newDevice = new PhysicalDevice.Builder()
        .setKey(k)
        .setLastOnlineTime(device.getLastOnlineTime())
        .setType(device.getType())
        .setName(device.getName())
        .setNameOverride(nameOverride)
        .setTarget(device.getTarget())
        .setApi(device.getApi())
        .addAllConnectionTypes(device.getConnectionTypes())
        .build();

      myDevices.set(i, newDevice);
    }

    combineDevices();
    fireTableDataChanged();
  }

  void remove(@NotNull Key key) {
    myDevices.removeIf(device -> {
      Key k = device.getKey();
      return k.equals(key) || k.getSerialNumber().equals(key);
    });

    combineDevices();
    fireTableDataChanged();
  }

  private void combineDevices() {
    Collection<PhysicalDevice> domainNameDevices = filterDevicesBy(DomainName.class);
    Collection<PhysicalDevice> serialNumberDevices = filterDevicesBy(SerialNumber.class);

    List<PhysicalDevice> combinedDevices = new ArrayList<>(myDevices.size());

    for (Iterator<PhysicalDevice> domainNameDeviceIterator = domainNameDevices.iterator(); domainNameDeviceIterator.hasNext(); ) {
      PhysicalDevice domainNameDevice = domainNameDeviceIterator.next();
      Object domainNameSerialNumber = domainNameDevice.getKey().getSerialNumber();

      for (Iterator<PhysicalDevice> serialNumberDeviceIterator = serialNumberDevices.iterator(); serialNumberDeviceIterator.hasNext(); ) {
        PhysicalDevice serialNumberDevice = serialNumberDeviceIterator.next();

        if (domainNameSerialNumber.equals(serialNumberDevice.getKey())) {
          combinedDevices.add(combine(domainNameDevice, serialNumberDevice));

          domainNameDeviceIterator.remove();
          serialNumberDeviceIterator.remove();

          break;
        }
      }
    }

    combinedDevices.addAll(domainNameDevices);
    combinedDevices.addAll(serialNumberDevices);
    combinedDevices.sort(null);

    myCombinedDevices = combinedDevices;
  }

  private @NotNull Collection<@NotNull PhysicalDevice> filterDevicesBy(@NotNull Class<? extends Key> keyClass) {
    return myDevices.stream()
      .filter(device -> keyClass.isInstance(device.getKey()))
      .collect(Collectors.toCollection(() -> new ArrayList<>(myDevices.size())));
  }

  private static @NotNull PhysicalDevice combine(@NotNull PhysicalDevice domainNameDevice, @NotNull PhysicalDevice serialNumberDevice) {
    Collection<Instant> times = Arrays.asList(domainNameDevice.getLastOnlineTime(), serialNumberDevice.getLastOnlineTime());

    return new PhysicalDevice.Builder()
      .setKey(serialNumberDevice.getKey())
      .setLastOnlineTime(Collections.min(times, PhysicalDevice.LAST_ONLINE_TIME_COMPARATOR))
      .setType(serialNumberDevice.getType())
      .setName(serialNumberDevice.getName())
      .setNameOverride(serialNumberDevice.getNameOverride())
      .setTarget(serialNumberDevice.getTarget())
      .setApi(serialNumberDevice.getApi())
      .addAllConnectionTypes(domainNameDevice.getConnectionTypes())
      .addAllConnectionTypes(serialNumberDevice.getConnectionTypes())
      .build();
  }

  @NotNull List<@NotNull PhysicalDevice> getCombinedDevices() {
    return myCombinedDevices;
  }

  @Override
  public int getRowCount() {
    return myCombinedDevices.size();
  }

  @Override
  public int getColumnCount() {
    return SPLIT_ACTIONS_ENABLED ? 6 : 4;
  }

  @Override
  public @NotNull String getColumnName(int modelColumnIndex) {
    if (SPLIT_ACTIONS_ENABLED) {
      switch (modelColumnIndex) {
        case DEVICE_MODEL_COLUMN_INDEX:
          return "Device";
        case API_MODEL_COLUMN_INDEX:
          return "API";
        case TYPE_MODEL_COLUMN_INDEX:
          return "Type";
        case ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX:
          return "Actions";
        case REMOVE_MODEL_COLUMN_INDEX:
        case MORE_MODEL_COLUMN_INDEX:
          return "";
        default:
          throw new AssertionError(modelColumnIndex);
      }
    }

    switch (modelColumnIndex) {
      case DEVICE_MODEL_COLUMN_INDEX:
        return "Device";
      case API_MODEL_COLUMN_INDEX:
        return "API";
      case TYPE_MODEL_COLUMN_INDEX:
        return "Type";
      case ACTIONS_MODEL_COLUMN_INDEX:
        return "Actions";
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @Override
  public @NotNull Class<?> getColumnClass(int modelColumnIndex) {
    if (SPLIT_ACTIONS_ENABLED) {
      switch (modelColumnIndex) {
        case DEVICE_MODEL_COLUMN_INDEX:
          return Device.class;
        case API_MODEL_COLUMN_INDEX:
        case TYPE_MODEL_COLUMN_INDEX:
          return Object.class;
        case ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX:
          return ActivateDeviceFileExplorerWindowValue.class;
        case REMOVE_MODEL_COLUMN_INDEX:
          return RemoveValue.class;
        case MORE_MODEL_COLUMN_INDEX:
          return MoreValue.class;
        default:
          throw new AssertionError(modelColumnIndex);
      }
    }

    switch (modelColumnIndex) {
      case DEVICE_MODEL_COLUMN_INDEX:
        return Device.class;
      case API_MODEL_COLUMN_INDEX:
      case TYPE_MODEL_COLUMN_INDEX:
        return Object.class;
      case ACTIONS_MODEL_COLUMN_INDEX:
        return Actions.class;
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @Override
  public boolean isCellEditable(int modelRowIndex, int modelColumnIndex) {
    if (SPLIT_ACTIONS_ENABLED) {
      switch (modelColumnIndex) {
        case DEVICE_MODEL_COLUMN_INDEX:
        case API_MODEL_COLUMN_INDEX:
        case TYPE_MODEL_COLUMN_INDEX:
          return false;
        case ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX:
        case REMOVE_MODEL_COLUMN_INDEX:
        case MORE_MODEL_COLUMN_INDEX:
          return true;
        default:
          throw new AssertionError(modelColumnIndex);
      }
    }

    return modelColumnIndex == ACTIONS_MODEL_COLUMN_INDEX;
  }

  @Override
  public @NotNull Object getValueAt(int modelRowIndex, int modelColumnIndex) {
    if (SPLIT_ACTIONS_ENABLED) {
      switch (modelColumnIndex) {
        case DEVICE_MODEL_COLUMN_INDEX:
          return myCombinedDevices.get(modelRowIndex);
        case API_MODEL_COLUMN_INDEX:
          return myCombinedDevices.get(modelRowIndex).getApi();
        case TYPE_MODEL_COLUMN_INDEX:
          return myCombinedDevices.get(modelRowIndex).getConnectionTypes().stream()
            .map(Object::toString)
            .collect(Collectors.joining(" "));
        case ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX:
          return ActivateDeviceFileExplorerWindowValue.INSTANCE;
        case REMOVE_MODEL_COLUMN_INDEX:
          return RemoveValue.INSTANCE;
        case MORE_MODEL_COLUMN_INDEX:
          return MoreValue.INSTANCE;
        default:
          throw new AssertionError(modelColumnIndex);
      }
    }

    switch (modelColumnIndex) {
      case DEVICE_MODEL_COLUMN_INDEX:
        return myCombinedDevices.get(modelRowIndex);
      case API_MODEL_COLUMN_INDEX:
        return myCombinedDevices.get(modelRowIndex).getApi();
      case TYPE_MODEL_COLUMN_INDEX:
        return myCombinedDevices.get(modelRowIndex).getConnectionTypes().stream()
          .map(Object::toString)
          .collect(Collectors.joining(" "));
      case ACTIONS_MODEL_COLUMN_INDEX:
        return Actions.INSTANCE;
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }
}
