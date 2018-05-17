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

import com.android.tools.idea.devicemanager.DeviceManagerFutureCallback;
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.concurrency.EdtExecutorService;
import java.awt.Component;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

final class DeleteItem extends JBMenuItem {
  DeleteItem(@NotNull VirtualDevicePopUpMenuButtonTableCellEditor editor) {
    this(editor, DeleteItem::showCannotDeleteRunningAvdDialog, DeleteItem::showConfirmDeleteDialog);
  }

  @VisibleForTesting
  DeleteItem(@NotNull VirtualDevicePopUpMenuButtonTableCellEditor editor,
             @NotNull Consumer<Component> showCannotDeleteRunningAvdDialog,
             @NotNull BiPredicate<Object, Component> showConfirmDeleteDialog) {
    super("Delete");
    setToolTipText("Delete this AVD");

    addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(EventKind.VIRTUAL_DELETE_ACTION)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);

      VirtualDevice device = editor.getDevice();
      VirtualDeviceTable table = editor.getPanel().getTable();

      if (device.isOnline()) {
        showCannotDeleteRunningAvdDialog.accept(table);
        return;
      }

      if (!showConfirmDeleteDialog.test(device, table)) {
        return;
      }

      // It's possible for an incomplete AVD deletion to occur. On Windows, this can happen where all the files get deleted, except for the
      // empty AVD folder. AvdManagerConnection returns this as a failed deletion, but the AVD .ini file did get deleted, so it's no
      // longer a valid AVD. We refresh the whole table in the case of failed deletion to make sure we show correct AVDs.
      DeviceManagerFutureCallback<Boolean> callback = new DeviceManagerFutureCallback<>(DeleteItem.class, deletionSuccessful -> {
        if (!deletionSuccessful) {
          table.refreshAvds();
          if (showUnsuccessfulDeletionDialog(table) == Messages.OK) {
            RevealFileAction.openDirectory(device.getAvdInfo().getDataFolderPath());
          }
        }
      });

      Futures.addCallback(table.getModel().remove(device), callback, EdtExecutorService.getInstance());
    });
  }

  private static void showCannotDeleteRunningAvdDialog(@NotNull Component component) {
    Messages.showErrorDialog(component,
                             "The selected AVD is currently running in the emulator. Please exit the emulator instance and try " +
                             "deleting again.",
                             "Cannot Delete a Running AVD");
  }

  private static boolean showConfirmDeleteDialog(@NotNull Object device, @NotNull Component component) {
    return MessageDialogBuilder.yesNo("Confirm Deletion", "Do you really want to delete " + device + "?").ask(component);
  }

  private static int showUnsuccessfulDeletionDialog(@NotNull Component component) {
    return Messages.showOkCancelDialog(component,
                                       "There may be additional files remaining in the AVD directory. Open the directory, " +
                                       "manually delete the files, and refresh AVD list.",
                                       "Could Not Delete All AVD Files",
                                       "Open Directory",
                                       "OK",
                                       Messages.getInformationIcon());
  }
}
