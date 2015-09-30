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
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.google.common.base.Predicate;
import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.util.ThreeState;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AvdsNotSupportedException;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AvdManagerLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumSet;

public class EmulatorTargetChooser implements TargetChooser {
  private static final Logger LOG = Logger.getInstance(TargetChooser.class);

  private final String myAvd;
  @NotNull private final AndroidFacet myFacet;
  private final boolean mySupportMultipleDevices;
  @NotNull private final EmulatorLaunchOptions myEmulatorLaunchOptions;
  @NotNull private final ConsolePrinter myPrinter;

  public EmulatorTargetChooser(
    @NotNull AndroidFacet facet,
    boolean supportMultipleDevices,
    @NotNull EmulatorLaunchOptions emulatorLaunchOptions,
    @NotNull ConsolePrinter printer,
    @Nullable String avd
  ) {
    myFacet = facet;
    mySupportMultipleDevices = supportMultipleDevices;
    myEmulatorLaunchOptions = emulatorLaunchOptions;
    myPrinter = printer;
    assert avd == null || avd.length() > 0;
    myAvd = avd;
  }

  @Override
  public boolean matchesDevice(@NotNull IDevice device) {
    if (!device.isEmulator()) {
      return false;
    }
    String avdName = device.getAvdName();
    if (myAvd != null) {
      return myAvd.equals(avdName);
    }

    AndroidPlatform androidPlatform = myFacet.getConfiguration().getAndroidPlatform();
    if (androidPlatform == null) {
      LOG.error("Target Android platform not set for module: " + myFacet.getModule().getName());
      return false;
    } else {
      LaunchCompatibility compatibility = LaunchCompatibility.canRunOnDevice(AndroidModuleInfo.get(myFacet).getRuntimeMinSdkVersion(),
                                                                             androidPlatform.getTarget(),
                                                                             EnumSet.noneOf(IDevice.HardwareFeature.class), device, null);
      return compatibility.isCompatible() != ThreeState.NO;
    }
  }

  @Nullable
  @Override
  public DeviceTarget getTarget() {
    Collection<IDevice> runningDevices = DeviceSelectionUtils
      .chooseRunningDevice(myFacet, new TargetDeviceFilter(this), mySupportMultipleDevices);
    if (runningDevices == null) {
      // The user canceled.
      return null;
    }
    if (!runningDevices.isEmpty()) {
      return DeviceTarget.forDevices(runningDevices);
    }

    // We need to launch an emulator.
    final String avd = myAvd != null ? myAvd : chooseAvd();
    if (avd == null) {
      // The user canceled.
      return null;
    }
    myFacet.launchEmulator(avd, myEmulatorLaunchOptions.getCommandLine());

    // Wait for an AVD to come up with name matching the one we just launched.
    Predicate<IDevice> avdNameFilter = new Predicate<IDevice>() {
      @Override
      public boolean apply(IDevice device) {
        return device.isEmulator() && avd.equals(device.getAvdName());
      }
    };

    return DeviceTarget.forFuture(DeviceReadyListener.getReadyDevice(avdNameFilter, myPrinter));
  }

  @Nullable
  private String chooseAvd() {
    IAndroidTarget buildTarget = myFacet.getConfiguration().getAndroidTarget();
    assert buildTarget != null;
    AvdInfo[] avds = myFacet.getValidCompatibleAvds();
    if (avds.length > 0) {
      return avds[0].getName();
    }
    final Project project = myFacet.getModule().getProject();
    AvdManager manager = null;
    try {
      manager = myFacet.getAvdManager(new AvdManagerLog() {
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
    catch (AvdsNotSupportedException e) {
      // can't be
      LOG.error(e);
    }
    catch (final AndroidLocation.AndroidLocationException e) {
      LOG.info(e);
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          Messages.showErrorDialog(project, e.getMessage(), CommonBundle.getErrorTitle());
        }
      });
      return null;
    }
    final AvdManager finalManager = manager;
    assert finalManager != null;
    return UIUtil.invokeAndWaitIfNeeded(new Computable<String>() {
      @Override
      public String compute() {
        CreateAvdDialog dialog = new CreateAvdDialog(project, myFacet, finalManager, true, true);
        dialog.show();
        if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
          AvdInfo createdAvd = dialog.getCreatedAvd();
          if (createdAvd != null) {
            return createdAvd.getName();
          }
        }
        return null;
      }
    });
  }
}
