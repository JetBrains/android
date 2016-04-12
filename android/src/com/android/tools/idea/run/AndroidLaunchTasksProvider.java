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
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.idea.fd.*;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.tasks.*;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.tools.fd.client.InstantRunArtifactType.*;

public class AndroidLaunchTasksProvider implements LaunchTasksProvider {
  private final AndroidRunConfigurationBase myRunConfig;
  private final ExecutionEnvironment myEnv;
  private final Project myProject;
  private final AndroidFacet myFacet;
  private final ApkProvider myApkProvider;
  private final LaunchOptions myLaunchOptions;

  public AndroidLaunchTasksProvider(@NotNull AndroidRunConfigurationBase runConfig,
                                    @NotNull ExecutionEnvironment env,
                                    @NotNull AndroidFacet facet,
                                    @NotNull ApkProvider apkProvider,
                                    @NotNull LaunchOptions launchOptions) {
    myRunConfig = runConfig;
    myEnv = env;
    myProject = facet.getModule().getProject();
    myFacet = facet;
    myApkProvider = apkProvider;
    myLaunchOptions = launchOptions;
  }

  @NotNull
  @Override
  public List<LaunchTask> getTasks(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter consolePrinter) {
    final List<LaunchTask> launchTasks = Lists.newArrayList();

    if (myLaunchOptions.isClearLogcatBeforeStart()) {
      launchTasks.add(new ClearLogcatTask(myProject));
    }

    launchTasks.add(new DismissKeyguardTask());

    LaunchTask deployTask = getDeployTask(device, launchStatus, consolePrinter);
    if (deployTask != null) {
      launchTasks.add(deployTask);
    }
    if (launchStatus.isLaunchTerminated()) {
      return launchTasks;
    }

    String packageName;
    try {
      packageName = myApkProvider.getPackageName();
      LaunchTask appLaunchTask = myRunConfig.getApplicationLaunchTask(myApkProvider, myFacet, myLaunchOptions.isDebug(), launchStatus);
      if (appLaunchTask != null) {
        launchTasks.add(appLaunchTask);
      }
    }
    catch (ApkProvisionException e) {
      Logger.getInstance(AndroidLaunchTasksProvider.class).error(e);
      launchStatus.terminateLaunch("Unable to determine application id: " + e);
      return Collections.emptyList();
    }

    if (!myLaunchOptions.isDebug() && myLaunchOptions.isOpenLogcatAutomatically()) {
      launchTasks.add(new ShowLogcatTask(myProject, packageName));
    }

    if (myRunConfig.getProfilerState().isGapidEnabled()) {
      launchTasks.add(new GapidTraceTask(myRunConfig, packageName));
    }

    return launchTasks;
  }

  @Nullable
  private LaunchTask getDeployTask(@NotNull final IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter consolePrinter) {
    if (!myLaunchOptions.isDeploy()) {
      return null;
    }

    if (InstantRunUtils.isInstantRunEnabled(myEnv) && InstantRunSettings.isInstantRunEnabled()) {
      AndroidGradleModel model = AndroidGradleModel.get(myFacet);
      if (model != null) {
        BooleanStatus status = InstantRunGradleUtils.getIrSupportStatus(model, device.getVersion());
        if (status.success) {
          InstantRunBuildInfo buildInfo = InstantRunGradleUtils.getBuildInfo(model);
          if (buildInfo == null) {
            String reason = "Gradle build-info.xml not found for module " + myFacet.getModule().getName() +
                            ". Please make sure that you are using gradle plugin '2.0.0-alpha4' or higher.";
            launchStatus.terminateLaunch(reason);
            return null;
          }

          String pkgName;
          try {
            pkgName = ApkProviderUtil.computePackageName(myFacet);
          }
          catch (ApkProvisionException e) {
            launchStatus.terminateLaunch("Unable to determine application id for module " + myFacet.getModule().getName());
            return null;
          }

          if (!InstantRunSettings.isColdSwapEnabled() && !buildInfo.getVerifierStatus().isEmpty()) {
            InstantRunManager.LOG.info("Coldswap disabled by user setting, restarting build.");
            // We should update the id on the device even if there were no artifact changes, since otherwise the next build will mismatch
            InstantRunManager.transferLocalIdToDeviceId(device, myFacet.getModule());
            DeployApkTask.cacheManifestInstallationData(device, myFacet, pkgName);
            restartBuild(device);
            return null;
          }

          List<InstantRunArtifact> artifacts = buildInfo.getArtifacts();
          if (artifacts.isEmpty()) {
            // We should update the id on the device even if there were no artifact changes, since otherwise the next build will mismatch
            InstantRunManager.transferLocalIdToDeviceId(device, myFacet.getModule());
            DeployApkTask.cacheManifestInstallationData(device, myFacet, pkgName);

            // if we are forced to do a cold swap, but we didn't get any artifacts, then issue a rebuild
            // Note that this check looks at the verifier status being set because the verifier status could be empty if there were no changes,
            // but the buildInfo.canHotswap() treats that differently
            if (!buildInfo.getVerifierStatus().isEmpty()) {
              InstantRunManager.LOG.info("Build info reports verifier failure, but no artifacts were provided. Restarting launch.");
              launchStatus.terminateLaunch("Re-launching since we cannot push the current build results to device");
              restartBuild(device);
              return null;
            }

            consolePrinter.stdout("No local changes, not deploying APK");
            InstantRunManager.LOG.info("List of artifacts is empty, no deployment necessary.");
            new InstantRunUserFeedback(myFacet.getModule()).info("No changes to deploy");
            return null;
          }

          if (buildInfo.hasOneOf(SPLIT) || buildInfo.hasOneOf(SPLIT_MAIN)) {
            InstantRunManager.LOG.info("Using split APK deploy task");
            return new SplitApkDeployTask(pkgName, myFacet, buildInfo);
          }
          if (buildInfo.hasOneOf(DEX, RESOURCES)) {
            InstantRunManager.LOG.info("Using Dex Deploy task");
            return new DexDeployTask(myEnv, myFacet, buildInfo);
          }
        }
        else {
          InstantRunManager.LOG.info("Instant Run not supported: " + status.getCause());
        }
      }
    }

    // regular APK deploy flow
    InstantRunManager.LOG.info("Using legacy/main APK deploy task");
    boolean instantRunAware =
      InstantRunUtils.isInstantRunEnabled(myEnv) &&
      InstantRunSettings.isInstantRunEnabled() &&
      InstantRunGradleUtils.getIrSupportStatus(myFacet.getModule(), device.getVersion()).success;
    return new DeployApkTask(myFacet, myLaunchOptions, myApkProvider, instantRunAware);
  }

  private void restartBuild(@NotNull final IDevice device) {
    InstantRunStatsService.get(myFacet.getModule().getProject()).incrementRestartLaunchCount();

    // There was a verifier failure, but no artifacts: this means
    // we need to kick off a full build (coldswap not available)
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        InstantRunUtils.setRestartSession(myEnv, device);
        ExecutionUtil.restart(myEnv);
      }
    });
  }

  @Nullable
  @Override
  public DebugConnectorTask getConnectDebuggerTask(@NotNull LaunchStatus launchStatus) {
    if (!myLaunchOptions.isDebug()) {
      return null;
    }

    Set<String> packageIds = Sets.newHashSet();
    try {
      String packageName = myApkProvider.getPackageName();
      packageIds.add(packageName);
    }
    catch (ApkProvisionException e) {
      Logger.getInstance(AndroidLaunchTasksProvider.class).error(e);
    }

    try {
      String packageName = myApkProvider.getTestPackageName();
      if (packageName != null) {
        packageIds.add(packageName);
      }
    }
    catch (ApkProvisionException e) {
      // not as severe as failing to obtain package id for main application
      Logger.getInstance(AndroidLaunchTasksProvider.class)
        .warn("Unable to obtain test package name, will not connect debugger if tests don't instantiate main application");
    }

    AndroidDebugger debugger = myRunConfig.getAndroidDebugger();
    AndroidDebuggerState androidDebuggerState = myRunConfig.getAndroidDebuggerState();
    if (debugger != null && androidDebuggerState != null) {
      //noinspection unchecked
      return debugger.getConnectDebuggerTask(myEnv, packageIds, myFacet, androidDebuggerState, myRunConfig.getType().getId());
    }

    return null;
  }

  @Override
  public boolean createsNewProcess() {
    return true;
  }

  @Override
  public boolean monitorRemoteProcess() {
    return myRunConfig.monitorRemoteProcess();
  }
}
