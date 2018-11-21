/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.deployer.Deployer;
import com.android.tools.deployer.DeployerException;
import com.android.tools.deployer.InstallOptions;
import com.android.tools.tracer.Trace;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;

public class InstallAction extends DeployAction {
  private static final Logger LOG = Logger.getInstance(InstallAction.class);

  private final String userInstallOptions;

  public InstallAction(String userInstallOptions) {
    this.userInstallOptions = userInstallOptions;
  }

  @Override
  public String getName() {
    return "Install";
  }

  @Override
  public void deploy(Project project, IDevice device, Deployer deployer, String applicationId, List<File> apkFiles)
    throws DeployerException {
    InstallOptions.Builder options = InstallOptions.builder().setAllowDebuggable();

    // Embedded devices (Android Things) have all runtime permissions granted since there's no requirement for user
    // interaction/display. However, regular installation will not grant some permissions until the next device reboot.
    // Installing with "-g" guarantees that the permissions are properly granted at install time.
    if (device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)) {
      options.setGrantAllPermissions();
    }

    // We can just append this, since all these options get string-joined in the end anyways.
    if (userInstallOptions != null) {
      options.setUserInstallOptions(userInstallOptions);
    }

    LOG.info("Installing application: " + applicationId);
    try (Trace trace = Trace.begin("Unified.install")) {
      deployer.install(applicationId, getPathsToInstall(apkFiles), options.build());
    }
  }
}