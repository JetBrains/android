/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.idea.blaze.android.run.runner;

import static com.android.tools.idea.run.tasks.AbstractDeployTask.getAppToInstall;

import com.android.ddmlib.IDevice;
import com.android.tools.deployer.Deployer;
import com.android.tools.deployer.DeployerException;
import com.android.tools.deployer.model.App;
import com.android.tools.idea.execution.common.ApplicationDeployer;
import com.android.tools.idea.execution.common.DeployOptions;
import com.android.tools.idea.run.ApkInfo;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

/** Deploys mobile install application. */
public class MobileInstallApplicationDeployer implements ApplicationDeployer {

  public MobileInstallApplicationDeployer() {}

  @NotNull
  @Override
  public Deployer.Result fullDeploy(
      @NotNull IDevice device,
      @NotNull ApkInfo apkInfo,
      @NotNull DeployOptions deployOptions,
      ProgressIndicator indicator) {
    App app = getAppToInstall(apkInfo);
    return new Deployer.Result(false, false, false, app);
  }

  @NotNull
  @Override
  public Deployer.Result applyChangesDeploy(
      @NotNull IDevice device,
      @NotNull ApkInfo app,
      @NotNull DeployOptions deployOptions,
      ProgressIndicator indicator) {
    throw new RuntimeException("Apply changes is not supported for mobile-install");
  }

  @NotNull
  @Override
  public Deployer.Result applyCodeChangesDeploy(
      @NotNull IDevice device,
      @NotNull ApkInfo app,
      @NotNull DeployOptions deployOptions,
      ProgressIndicator indicator)
      throws DeployerException {
    throw new RuntimeException("Apply code changes is not supported for mobile-install");
  }
}
