/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.fd;

import com.android.ddmlib.*;
import com.google.common.collect.Maps;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RunAsValidityService implements RunAsValidator {
  // Originally, we wanted this persisted across restarts of the IDE. But then, we'd have to make sure we also persist the build version
  // of the device as well, so that we retry if a device gets a software update. However, there concerns that this could be a transient
  // issue, so the compromise for now is to save this setting per launch of Studio. Hence this is just stored inside a static variable.
  private static final Map<String,Boolean> ourRunAsStateByDevice = Maps.newConcurrentMap();

  public static RunAsValidityService getInstance() {
    return ServiceManager.getService(RunAsValidityService.class);
  }

  private RunAsValidityService() {
  }

  @Override
  public boolean hasWorkingRunAs(@NotNull IDevice device, @NotNull String applicationId) {
    if (device.isEmulator()) {
      // AVD's are all fine
      return true;
    }

    Boolean workingRunAs = ourRunAsStateByDevice.get(device.getSerialNumber());
    if (workingRunAs == null) {
      workingRunAs = checkForWorkingRunAs(device, applicationId);
      ourRunAsStateByDevice.put(device.getSerialNumber(), workingRunAs);
    }
    return workingRunAs;
  }

  private Boolean checkForWorkingRunAs(@NotNull IDevice device, @NotNull String applicationId) {
    String cmd = "run-as " + applicationId + " ls";
    CollectingOutputReceiver receiver = new CollectingOutputReceiver();
    try {
      device.executeShellCommand(cmd, receiver, 2, TimeUnit.SECONDS);
    }
    catch (Exception e) {
      return null;
    }

    String runAsOutput = receiver.getOutput();
    Logger.getInstance(RunAsValidityService.class).info("Output of " + cmd + ": " + runAsOutput);

    // Broken devices give a "run-as: Package 'com.foo' is unknown" message
    // Note that some devices may have other spurious output, so we explicitly check for the existence
    // of the error message
    return !runAsOutput.contains("run-as: Package");
  }
}
