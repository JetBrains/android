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

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.deployer.ClassRedefiner;
import com.android.tools.deployer.Deployer;
import com.android.tools.deployer.DeployerException;
import com.android.tools.deployer.tasks.Canceller;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.util.DebuggerRedefiner;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class ApplyCodeChangesTask extends AbstractDeployTask {

  private static final Logger LOG = Logger.getInstance(ApplyCodeChangesTask.class);
  private static final String ID = "APPLY_CODE_CHANGES";

  /**
   * Creates a task to deploy a list of apks.
   *
   * @param project             the project that this task is running within.
   * @param packages            a collection of apks representing the packages this task will deploy.
   * @param rerunOnSwapFailure  rerun the app when swap fails
   * @param installPathProvider
   */
  public ApplyCodeChangesTask(
    @NotNull Project project, @NotNull Collection<ApkInfo> packages, boolean rerunOnSwapFailure, boolean alwaysInstallWithPm,
    Computable<String> installPathProvider) {
    super(project, packages, rerunOnSwapFailure, alwaysInstallWithPm, installPathProvider);
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  /**
   * @param device The device we are deploying to.
   * @param apk The apk we want to deploy.
   * @return debugger redefiners that will be used for specific PIDs
   */
  private ImmutableMap<Integer, ClassRedefiner> makeDebuggerRedefiners(Project project, IDevice device, boolean fallback) {
    if (!DebuggerRedefiner.hasDebuggersAttached(project)) {
      return ImmutableMap.of();
    }

    ImmutableMap.Builder<Integer, ClassRedefiner> debugRedefiners = ImmutableMap.builder();
    for (Client client : device.getClients()) {
      if (client.isDebuggerAttached()) {
        int port = client.getDebuggerListenPort();
        if (DebuggerRedefiner.getDebuggerSession(project, port) != null) {
          ClassRedefiner debugRedefiner = new DebuggerRedefiner(project, port, fallback);
          debugRedefiners.put(client.getClientData().getPid(), debugRedefiner);
        }
      }
    }

    return debugRedefiners.build();
  }

  @Override
  protected Deployer.Result perform(IDevice device,
                                    Deployer deployer,
                                    @NotNull ApkInfo apkInfo,
                                    @NotNull Canceller canceller) throws DeployerException {
    LOG.info("Applying code changes to application: " + apkInfo.getApplicationId());
    // TODO: support fallback on R- devices
    ImmutableMap<Integer, ClassRedefiner> debuggerRedefiners = makeDebuggerRedefiners(
      getProject(), device, getFastRerunOnSwapFailure() && deployer.supportsNewPipeline());
    return deployer.codeSwap(getAppToInstall(apkInfo), debuggerRedefiners, canceller);
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Apply Code Changes";
  }

  @NotNull
  @Override
  protected String createSkippedApkInstallMessage(List<String> skippedApkList, boolean all) {
    if (all) {
      return "No code changes detected.";
    } else {
      return "No code changes detected. The ollowing APK(s) are not installed: " +
             skippedApkList.stream().collect(Collectors.joining(", "));
    }
  }
}
