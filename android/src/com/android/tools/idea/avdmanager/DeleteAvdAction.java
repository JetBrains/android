/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.concurrency.FutureUtils;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;

import com.intellij.util.concurrency.EdtExecutorService;
import java.awt.event.ActionEvent;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Delete an AVD with confirmation.
 */
public class DeleteAvdAction extends AvdUiAction {
  private final @NotNull Function<@NotNull AvdInfoProvider, @NotNull ListenableFuture<@NotNull Boolean>> myIsAvdRunning;

  private final @NotNull Executor myExecutor;

  public DeleteAvdAction(@NotNull AvdInfoProvider provider) {
    super(provider, "Delete", "Delete this AVD", AllIcons.Actions.Cancel);
    myExecutor = EdtExecutorService.getInstance();
    myIsAvdRunning = AvdManagerConnection.getDefaultAvdManagerConnection()::isAvdRunning;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    AvdManagerConnection connection = AvdManagerConnection.getDefaultAvdManagerConnection();
    AvdInfo info = getAvdInfo();
    if (info == null) {
      return;
    }

    FutureUtils.addCallback(myIsAvdRunning.apply(myAvdInfoProvider), myExecutor, new FutureCallback<Boolean>() {
      @Override
      public void onSuccess(@Nullable Boolean running) {
        assert running != null;

        if (running) {
          Messages.showErrorDialog(
            myAvdInfoProvider.getAvdProviderComponent(),
            "The selected AVD is currently running in the Emulator. Please exit the emulator instance and try deleting again.",
            "Cannot Delete A Running AVD"
          );
          return;
        }
        int result = Messages.showYesNoDialog(
          myAvdInfoProvider.getAvdProviderComponent(),
          "Do you really want to delete AVD " + info.getName() + "?", "Confirm Deletion",
          AllIcons.General.QuestionDialog
        );
        if (result == Messages.YES) {
          if (!connection.deleteAvd(info)) {
            Messages.showErrorDialog(
              myAvdInfoProvider.getAvdProviderComponent(),
              "An error occurred while deleting the AVD. See idea.log for details.", "Error Deleting AVD"
            );
          }
          refreshAvds();
        }
      }

      @Override
      public void onFailure(@NotNull Throwable throwable) {
        Logger.getInstance(DeleteAvdAction.class).warn(throwable);
      }
    });
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
