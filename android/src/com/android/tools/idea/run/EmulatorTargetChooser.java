/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.android.prefs.AndroidLocation;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.avdmanager.AvdOptionsModel;
import com.android.tools.idea.avdmanager.AvdWizardUtils;
import com.android.tools.idea.avdmanager.AvdManagerUtils;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.ImmutableList;
import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ThreeState;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AvdManagerLog;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class EmulatorTargetChooser {
  private static final Logger LOG = Logger.getInstance(EmulatorTargetChooser.class);

  private final String myAvd;
  @NotNull private final AndroidFacet myFacet;

  public EmulatorTargetChooser(@NotNull AndroidFacet facet, @Nullable String avd) {
    myFacet = facet;
    assert avd == null || !avd.isEmpty();
    myAvd = avd;
  }

  @Nullable
  public DeviceFutures getDevices(@NotNull DeviceCount deviceCount) {
    TargetDeviceFilter deviceFilter = new TargetDeviceFilter.EmulatorFilter(myFacet, myAvd);
    Collection<IDevice> runningDevices = DeviceSelectionUtils.chooseRunningDevice(myFacet, deviceFilter, deviceCount);
    if (runningDevices == null) {
      // The user canceled.
      return null;
    }
    if (!runningDevices.isEmpty()) {
      return DeviceFutures.forDevices(runningDevices);
    }

    // We need to launch an emulator.
    final String avd = myAvd != null ? myAvd : chooseAvd();
    if (avd == null) {
      // The user canceled.
      return null;
    }

    AvdManager manager = AvdManagerUtils.getAvdManagerSilently(myFacet);
    if (manager == null) {
      LOG.warn("Could not obtain AVD Manager.");
      return null;
    }

    AvdInfo avdInfo = manager.getAvd(avd, true);
    if (avdInfo == null) {
      LOG.warn("Unable to obtain info for AVD: " + avd);
      return null;
    }

    LaunchableAndroidDevice androidDevice = new LaunchableAndroidDevice(avdInfo);
    androidDevice.launch(myFacet.getModule().getProject()); // LAUNCH EMULATOR
    return new DeviceFutures(Collections.singletonList(androidDevice));
  }

  @Nullable
  private String chooseAvd() {
    IAndroidTarget buildTarget = myFacet.getConfiguration().getAndroidTarget();
    assert buildTarget != null;
    List<AvdInfo> avds = getValidCompatibleAvds(myFacet);
    if (!avds.isEmpty()) {
      return avds.get(0).getName();
    }
    final Project project = myFacet.getModule().getProject();
    AvdManager manager;
    try {
      manager = AvdManager.getInstance(AndroidSdkData.getSdkHolder(myFacet), new AvdManagerLog() {
        @Override
        public void error(Throwable t, String errorFormat, Object... args) {
          super.error(t, errorFormat, args);

          if (errorFormat != null) {
            final String msg = String.format(errorFormat, args);
            LOG.error(msg);
          }
        }
      });
    }
    catch (final AndroidLocation.AndroidLocationException e) {
      LOG.info(e);
      UIUtil.invokeLaterIfNeeded(() -> Messages.showErrorDialog(project, e.getMessage(), CommonBundle.getErrorTitle()));
      return null;
    }
    final AvdManager finalManager = manager;
    assert finalManager != null;
    return UIUtil.invokeAndWaitIfNeeded(() -> {
      int result = Messages.showDialog(project, "To run using the emulator, you must have an AVD defined.", "Define AVD",
                                       new String[]{"Cancel", "Create AVD"}, 1, null);
      AvdInfo createdAvd = null;
      if (result == 1) {
        AvdOptionsModel avdOptionsModel = new AvdOptionsModel(null);
        ModelWizardDialog dialog = AvdWizardUtils.createAvdWizard(null, project, avdOptionsModel);
        if (dialog.showAndGet()) {
          createdAvd = avdOptionsModel.getCreatedAvd();
        }
      }
      return createdAvd == null ? null : createdAvd.getName();
    });
  }

  @NotNull
  private static List<AvdInfo> getValidCompatibleAvds(@NotNull AndroidFacet facet) {
    AvdManager manager = AvdManagerUtils.getAvdManagerSilently(facet);
    if (manager == null || !AvdManagerUtils.reloadAvds(manager, facet.getModule().getProject())) {
      return ImmutableList.of();
    }

    AndroidVersion minSdk = AndroidModuleInfo.getInstance(facet).getRuntimeMinSdkVersion();
    AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
    if (platform == null) {
      Logger.getInstance(EmulatorTargetChooser.class).error("Android Platform not set for module: " + facet.getModule().getName());
      return ImmutableList.of();
    }

    return getCompatibleAvds(manager.getValidAvds(), minSdk, platform);
  }

  @NotNull
  private static List<AvdInfo> getCompatibleAvds(@NotNull AvdInfo[] allAvds,
                                                 @NotNull AndroidVersion minSdk,
                                                 @NotNull AndroidPlatform platform) {
    return Arrays.stream(allAvds)
      .filter(info -> {
        ISystemImage systemImage = info.getSystemImage();
        return systemImage != null &&
               LaunchCompatibility.canRunOnAvd(minSdk, platform.getTarget(), systemImage).isCompatible() != ThreeState.NO;
      })
      .collect(Collectors.toList());
  }

  @NotNull
  public List<ValidationError> validate() {
    if (myAvd == null) {
      return ImmutableList.of();
    }
    AvdManager avdManager = AvdManagerUtils.getAvdManagerSilently(myFacet);
    if (avdManager == null) {
      return ImmutableList.of(ValidationError.fatal(AndroidBundle.message("avd.cannot.be.loaded.error")));
    }
    AvdInfo avdInfo = avdManager.getAvd(myAvd, false);
    if (avdInfo == null) {
      return ImmutableList.of(ValidationError.fatal(AndroidBundle.message("avd.not.found.error", myAvd)));
    }
    if (avdInfo.getStatus() != AvdInfo.AvdStatus.OK) {
      String message = avdInfo.getErrorMessage();
      message = AndroidBundle.message("avd.not.valid.error", myAvd) +
                (message != null ? ": " + message: "") + ". Try to repair it through AVD manager";
      return ImmutableList.of(ValidationError.fatal(message));
    }
    return ImmutableList.of();
  }
}
