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

import com.android.sdklib.devices.Abi;
import com.android.sdklib.internal.avd.AvdInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;

/**
 * Run an Android virtual device
 */
public class RunAvdAction extends AvdUiAction {
  public RunAvdAction(@NotNull AvdInfoProvider provider) {
    super(provider, "Run", "Launch this AVD in the emulator", AllIcons.Actions.Execute);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    AvdInfo avdInfo = getAvdInfo();
    if (avdInfo != null && checkCanStart(avdInfo)) {
      startAvd(avdInfo);
    }
  }

  @Override
  public boolean isEnabled() {
    AvdInfo avdInfo = getAvdInfo();
    return avdInfo != null && avdInfo.getStatus() == AvdInfo.AvdStatus.OK;
  }

  /**
   * Check if we can start the AVD.
   * If there is a problem display a dialog with the error that is preventing the start.
   * @return true if the AVD can be started.
   */
  private boolean checkCanStart(@NotNull final AvdInfo avdInfo) {
    Abi abi = Abi.getEnum(avdInfo.getAbiType());
    boolean isAvdIntel = abi == Abi.X86 || abi == Abi.X86_64;
    AvdManagerConnection manager = AvdManagerConnection.getDefaultAvdManagerConnection();
    AccelerationErrorCode error =  manager.checkAcceration();
    switch (error) {
      case ALREADY_INSTALLED:
      case TOOLS_UPDATE_REQUIRED:
      case PLATFORM_TOOLS_UPDATE_ADVISED:
      case SYSTEM_IMAGE_UPDATE_ADVISED:
        // Do not block emulator from running if we need updates (run with degradated performance):
        startAvd(avdInfo);
        return true;
      case NO_EMULATOR_INSTALLED:
        break;
      default:
        if (!isAvdIntel) {
          // Do not block Arm and Mips emulators from running without an accelerator:
          startAvd(avdInfo);
          return true;
        }
    }
    String accelerator = SystemInfo.isLinux ? "KVM" : "Intel HAXM";
    int result = Messages.showOkCancelDialog(
      myAvdInfoProvider.getComponent(),
      String.format("%1$s is required to run this AVD.\n%2$s\n\n%3$s\n", accelerator, error.getProblem(), error.getSolutionMessage()),
      error.getSolution().getDescription(),
      AllIcons.General.WarningDialog);
    if (result != Messages.OK || error.getSolution() == AccelerationErrorSolution.SolutionCode.NONE) {
      return false;
    }
    Runnable tryAgain = new Runnable() {
      @Override
      public void run() {
        if (checkCanStart(avdInfo)) {
          startAvd(avdInfo);
        }
      }
    };
    Runnable action = AccelerationErrorSolution.getActionForFix(error, myAvdInfoProvider.getProject(), tryAgain);
    ApplicationManager.getApplication().invokeLater(action);
    return false;
  }

  private void startAvd(@NotNull AvdInfo avdInfo) {
    AvdManagerConnection.getDefaultAvdManagerConnection().startAvd(myAvdInfoProvider.getProject(), avdInfo);
  }
}
