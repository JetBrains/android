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

import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.devicemanager.MenuItems;
import com.android.tools.idea.devicemanager.PopUpMenuButtonTableCellEditor;
import com.android.tools.idea.wearpairing.AndroidWearPairingBundle;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JPopupMenu.Separator;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;

final class PhysicalDevicePopUpMenuButtonTableCellEditor extends PopUpMenuButtonTableCellEditor {
  PhysicalDevicePopUpMenuButtonTableCellEditor(@NotNull PhysicalDevicePanel panel, @NotNull WearPairingManager manager) {
    super(panel, manager);
  }

  @Override
  public @NotNull List<JComponent> newItems() {
    List<JComponent> items = new ArrayList<>();
    Optional<JComponent> optionalItem = newViewPairedDevicesItem(EventKind.PHYSICAL_UNPAIR_DEVICE_ACTION);

    items.add(MenuItems.newViewDetailsItem(myPanel));
    optionalItem.ifPresent(item -> items.add(new Separator()));
    items.add(newPairWearableItem());
    optionalItem.ifPresent(items::add);

    return items;
  }

  private @NotNull JComponent newPairWearableItem() {
    JComponent item = newPairWearableItem(EventKind.PHYSICAL_PAIR_DEVICE_ACTION);

    boolean phone = myDevice.getType().equals(DeviceType.PHONE);
    boolean online = myDevice.isOnline();

    item.setEnabled(phone && online);

    if (phone && online) {
      item.setToolTipText(AndroidWearPairingBundle.message("wear.assistant.device.list.tooltip.ok"));
    }
    else if (phone) {
      item.setToolTipText(AndroidWearPairingBundle.message("wear.assistant.device.list.tooltip.offline"));
    }
    else {
      item.setToolTipText(AndroidWearPairingBundle.message("wear.assistant.device.list.tooltip.unsupported"));
    }

    return item;
  }

  @Override
  public @NotNull Component getTableCellEditorComponent(@NotNull JTable table,
                                                        @NotNull Object value,
                                                        boolean selected,
                                                        int viewRowIndex,
                                                        int viewColumnIndex) {
    super.getTableCellEditorComponent(table, value, selected, viewRowIndex, viewColumnIndex);
    myDevice = ((PhysicalDeviceTable)table).getDeviceAt(viewRowIndex);

    return myButton;
  }
}
