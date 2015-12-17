/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.avdmanager.AvdWizardConstants;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LaunchableAndroidDevice implements AndroidDevice {
  private final AvdInfo myAvdInfo;

  public LaunchableAndroidDevice(@NotNull AvdInfo avdInfo) {
    myAvdInfo = avdInfo;
  }

  @Override
  public boolean isRunning() {
    return false;
  }

  @Override
  public boolean isVirtual() {
    return true;
  }

  @NotNull
  @Override
  public AndroidVersion getVersion() {
    IAndroidTarget target = myAvdInfo.getTarget();
    return target == null ? AndroidVersion.DEFAULT : target.getVersion();
  }

  @NotNull
  @Override
  public String getSerial() {
    return myAvdInfo.getName();
  }

  @Override
  public boolean supportsFeature(@NotNull IDevice.HardwareFeature feature) {
    switch (feature) {
      case WATCH:
        return AvdWizardConstants.WEAR_TAG.equals(myAvdInfo.getTag());
      case TV:
        return AvdWizardConstants.TV_TAG.equals(myAvdInfo.getTag());
      default:
        return true;
    }
  }

  @NotNull
  @Override
  public String getName() {
    return AvdManagerConnection.getAvdDisplayName(myAvdInfo);
  }

  @Override
  public void renderName(@NotNull SimpleColoredComponent renderer, boolean isCompatible, @Nullable String searchPrefix) {
    renderer.setIcon(AndroidIcons.Ddms.EmulatorDevice);
    SimpleTextAttributes attr = isCompatible ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES;
    SearchUtil.appendFragments(searchPrefix, getName(), attr.getStyle(), attr.getFgColor(), attr.getBgColor(), renderer);
  }

  public ListenableFuture<IDevice> launch(@NotNull Project project) {
    return AvdManagerConnection.getDefaultAvdManagerConnection().startAvd(project, myAvdInfo);
  }

  public AvdInfo getAvdInfo() {
    return myAvdInfo;
  }
}
