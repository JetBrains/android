/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import java.awt.Component;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

final class WipeDataItem extends JBMenuItem {
  private final @NotNull Consumer<@NotNull Component> myShowCannotWipeARunningAvdDialog;
  private final @NotNull BiPredicate<@NotNull Object, @NotNull Component> myShowConfirmDataWipeDialog;
  private final @NotNull Supplier<@NotNull AvdManagerConnection> myGetDefaultAvdManagerConnection;

  WipeDataItem(@NotNull VirtualDevicePopUpMenuButtonTableCellEditor editor) {
    this(editor,
         WipeDataItem::showCannotWipeARunningAvdDialog,
         WipeDataItem::showConfirmDataWipeDialog,
         AvdManagerConnection::getDefaultAvdManagerConnection);
  }

  @VisibleForTesting
  WipeDataItem(@NotNull VirtualDevicePopUpMenuButtonTableCellEditor editor,
               @NotNull Consumer<@NotNull Component> showCannotWipeARunningAvdDialog,
               @NotNull BiPredicate<@NotNull Object, @NotNull Component> showConfirmDataWipeDialog,
               @NotNull Supplier<@NotNull AvdManagerConnection> getDefaultAvdManagerConnection) {
    super("Wipe Data");

    myShowCannotWipeARunningAvdDialog = showCannotWipeARunningAvdDialog;
    myShowConfirmDataWipeDialog = showConfirmDataWipeDialog;
    myGetDefaultAvdManagerConnection = getDefaultAvdManagerConnection;

    setToolTipText("Wipe the user data of this AVD");

    addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(EventKind.VIRTUAL_WIPE_DATA_ACTION)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);

      VirtualDevice device = editor.getDevice();
      VirtualDeviceTable table = editor.getPanel().getTable();

      if (device.isOnline()) {
        myShowCannotWipeARunningAvdDialog.accept(table);
        return;
      }

      if (!myShowConfirmDataWipeDialog.test(device, table)) {
        return;
      }

      myGetDefaultAvdManagerConnection.get().wipeUserData(device.getAvdInfo());
      table.refreshAvds();
    });
  }

  private static void showCannotWipeARunningAvdDialog(@NotNull Component component) {
    Messages.showErrorDialog(component,
                             "The selected AVD is currently running in the emulator. Please exit the emulator instance and try wiping " +
                             "again.",
                             "Cannot Wipe a Running AVD");
  }

  private static boolean showConfirmDataWipeDialog(@NotNull Object device, @NotNull Component component) {
    return MessageDialogBuilder.yesNo("Confirm Data Wipe", "Do you really want to wipe user files from " + device + "?").ask(component);
  }
}
