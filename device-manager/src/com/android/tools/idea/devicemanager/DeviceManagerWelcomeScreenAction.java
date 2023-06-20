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

import com.android.tools.idea.avdmanager.HardwareAccelerationCheck;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.serviceContainer.NonInjectable;
import java.util.function.BooleanSupplier;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

final class DeviceManagerWelcomeScreenAction extends DumbAwareAction {
  private final @NotNull BooleanSupplier myIsChromeOSAndIsNotHWAccelerated;
  private final @NotNull BooleanSupplier myIsAndroidSdkAvailable;
  private @Nullable DeviceManagerWelcomeScreenFrame myDeviceManagerWelcomeScreenFrame;

  @SuppressWarnings("unused")
  private DeviceManagerWelcomeScreenAction() {
    this(HardwareAccelerationCheck::isChromeOSAndIsNotHWAccelerated, AndroidSdkUtils::isAndroidSdkAvailable);
  }

  @VisibleForTesting
  @NonInjectable
  DeviceManagerWelcomeScreenAction(@NotNull BooleanSupplier isChromeOSAndIsNotHWAccelerated,
                                   @NotNull BooleanSupplier isAndroidSdkAvailable) {
    myIsChromeOSAndIsNotHWAccelerated = isChromeOSAndIsNotHWAccelerated;
    myIsAndroidSdkAvailable = isAndroidSdkAvailable;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    if (myIsChromeOSAndIsNotHWAccelerated.getAsBoolean() || event.getProject() != null) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    presentation.setVisible(true);
    presentation.setEnabled(myIsAndroidSdkAvailable.getAsBoolean());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    if (myDeviceManagerWelcomeScreenFrame == null) {
      myDeviceManagerWelcomeScreenFrame = new DeviceManagerWelcomeScreenFrame(event.getProject());
      Disposer.register(myDeviceManagerWelcomeScreenFrame, () -> myDeviceManagerWelcomeScreenFrame = null);
      myDeviceManagerWelcomeScreenFrame.show();
    }
    else {
      myDeviceManagerWelcomeScreenFrame.getFrame().toFront();
    }
  }
}
