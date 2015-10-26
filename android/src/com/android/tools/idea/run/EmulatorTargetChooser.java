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
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AvdsNotSupportedException;
import org.jetbrains.android.sdk.AvdManagerLog;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class EmulatorTargetChooser {
  private static final Logger LOG = Logger.getInstance(EmulatorTargetChooser.class);

  private final String myAvd;
  @NotNull private final AndroidFacet myFacet;

  public EmulatorTargetChooser(@NotNull AndroidFacet facet, @Nullable String avd) {
    myFacet = facet;
    assert avd == null || avd.length() > 0;
    myAvd = avd;
  }

  @Nullable
  public DeviceTarget getTarget(@NotNull ConsolePrinter printer, @NotNull DeviceCount deviceCount, boolean debug) {
    TargetDeviceFilter deviceFilter = new TargetDeviceFilter.EmulatorFilter(myFacet, myAvd);
    Collection<IDevice> runningDevices = DeviceSelectionUtils.chooseRunningDevice(myFacet, deviceFilter, deviceCount);
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
    myFacet.launchEmulator(avd);

    // Wait for an AVD to come up with name matching the one we just launched.
    Predicate<IDevice> avdNameFilter = new Predicate<IDevice>() {
      @Override
      public boolean apply(IDevice device) {
        return device.isEmulator() && avd.equals(device.getAvdName());
      }
    };

    return DeviceTarget.forFuture(DeviceReadyListener.getReadyDevice(avdNameFilter, printer));
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

  @NotNull
  public List<ValidationError> validate() {
    if (myAvd == null) {
      return ImmutableList.of();
    }
    AvdManager avdManager = myFacet.getAvdManagerSilently();
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
