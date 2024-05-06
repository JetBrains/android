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
import com.android.tools.deployer.tasks.Canceller;
import com.android.tools.idea.run.ApkInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class ApplyChangesTask extends AbstractDeployTask {

  private static final Logger LOG = Logger.getInstance(ApplyChangesTask.class);
  private static final String ID = "APPLY_CHANGES";

  public ApplyChangesTask(@NotNull Project project,
                          @NotNull Collection<ApkInfo> packages,
                          boolean rerunOnSwapFailure,
                          boolean alwaysInstallWithPm,
                          Computable<String> installPathProvider) {
    super(project, packages, rerunOnSwapFailure, alwaysInstallWithPm, installPathProvider);
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Apply Changes";
  }

  @Override
  protected Deployer.Result perform(IDevice device,
                                    Deployer deployer,
                                    @NotNull ApkInfo apkInfo,
                                    @NotNull Canceller canceller) throws DeployerException {
    LOG.info("Applying changes to application: " + apkInfo.getApplicationId());
    return deployer.fullSwap(getAppToInstall(apkInfo), canceller);
  }

  @NotNull
  @Override
  protected String createSkippedApkInstallMessage(List<String> skippedApkList, boolean all) {
    if (all) {
      return "Activity restarted. No code or resource changes detected.";
    } else {
      return "Activity restarted without re-installing the following APK(s): " +
             skippedApkList.stream().collect(Collectors.joining(", "));
    }
  }
}
