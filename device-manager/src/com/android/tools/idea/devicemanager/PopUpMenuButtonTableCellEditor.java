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
package com.android.tools.idea.devicemanager;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.wearpairing.AndroidWearPairingBundle;
import com.android.tools.idea.wearpairing.WearDevicePairingWizard;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.android.tools.idea.wearpairing.WearPairingManager.PhoneWearPair;
import com.android.tools.idea.wearpairing.WearPairingManagerKt;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.PopupMenuListenerAdapter;
import java.util.List;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.NotNull;

public abstract class PopUpMenuButtonTableCellEditor extends IconButtonTableCellEditor {
  protected final @NotNull DevicePanel myPanel;
  private final @NotNull WearPairingManager myManager;

  protected Device myDevice;

  protected PopUpMenuButtonTableCellEditor(@NotNull DevicePanel panel) {
    super(PopUpMenuValue.INSTANCE, AllIcons.Actions.More);

    myPanel = panel;
    myManager = WearPairingManager.INSTANCE;

    myButton.addActionListener(event -> {
      JPopupMenu menu = new JBPopupMenu();
      newItems().forEach(menu::add);

      menu.addPopupMenuListener(new PopupMenuListenerAdapter() {
        @Override
        public void popupMenuWillBecomeInvisible(@NotNull PopupMenuEvent event) {
          fireEditingCanceled();
        }
      });

      menu.show(myButton, 0, myButton.getHeight());
    });
  }

  @VisibleForTesting
  public abstract @NotNull List<@NotNull JComponent> newItems();

  protected final @NotNull JComponent newPairWearableItem(@NotNull EventKind kind) {
    AbstractButton item = new JBMenuItem("Pair Wearable");

    item.addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(kind)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);
      new WearDevicePairingWizard().show(myPanel.getProject(), myDevice.getKey().toString());
    });

    return item;
  }

  protected final @NotNull Optional<@NotNull JComponent> newViewPairedDevicesItem(@NotNull EventKind kind) {
    String key = myDevice.getKey().toString();
    List<PhoneWearPair> pairs = myManager.getPairsForDevice(key);

    if (pairs.isEmpty()) {
      return Optional.empty();
    }

    boolean enabled = StudioFlags.PAIRED_DEVICES_TAB_ENABLED.get();
    AbstractButton item = new JBMenuItem(enabled ? "View Paired Device(s)" : "Unpair Device");

    item.addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(kind)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);

      if (enabled) {
        myPanel.viewDetails(DetailsPanel.PAIRED_DEVICES_TAB_INDEX);
      }
      else {
        Object name = pairs.get(0).getPeerDevice(key).getDisplayName();
        item.setToolTipText(AndroidWearPairingBundle.message("wear.assistant.device.list.forget.connection", name));

        WearPairingManagerKt.removeAllPairedDevicesAsync(myManager, key, true);
      }
    });

    return Optional.of(item);
  }
}
