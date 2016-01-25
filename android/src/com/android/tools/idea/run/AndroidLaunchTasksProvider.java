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
import com.android.tools.idea.fd.*;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.tasks.*;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.fd.InstantRunArtifactType.*;

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

    return launchTasks;
  }

  @Nullable
  private LaunchTask getDeployTask(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter consolePrinter) {
    if (!myLaunchOptions.isDeploy()) {
      return null;
    }

    if (InstantRunUtils.isInstantRunEnabled(myEnv) && InstantRunSettings.isInstantRunEnabled(myProject)) {
      AndroidGradleModel model = AndroidGradleModel.get(myFacet);

      if (model != null && InstantRunManager.variantSupportsInstantRun(model)) {
        InstantRunBuildInfo buildInfo = InstantRunBuildInfo.get(model);
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

        List<InstantRunArtifact> artifacts = buildInfo.getArtifacts();
        if (artifacts.isEmpty()) {
          // We should update the id on the device even if there were no
          // artifact changes, since otherwise the next build will mismatch
          InstantRunManager.transferLocalIdToDeviceId(device, myFacet.getModule());
          DeployApkTask.cacheManifestInstallationData(device, myFacet, pkgName);
          consolePrinter.stdout("No local changes, not deploying APK");
          InstantRunManager.LOG.info("List of artifacts is empty, no deployment necessary.");
          new InstantRunUserFeedback(myFacet.getModule()).info("No changes to deploy");
          return null;
        }

        if (buildInfo.hasOneOf(SPLIT) || buildInfo.getApiLevel() >= 23 && buildInfo.hasOneOf(MAIN)) {
          InstantRunManager.LOG.info("Using split APK deploy task");
          return new SplitApkDeployTask(pkgName, myFacet, buildInfo);
        }
        if (buildInfo.hasOneOf(RESTART_DEX, DEX, RESOURCES)) {
          InstantRunManager.LOG.info("Using Dex Deploy task");
          return new DexDeployTask(myFacet, buildInfo);
        }
      }
    }

    // regular APK deploy flow
    InstantRunManager.LOG.info("Using legacy/main APK deploy task");
    boolean instantRunAware =
      InstantRunUtils.isInstantRunEnabled(myEnv) &&
      InstantRunSettings.isInstantRunEnabled(myProject) &&
      InstantRunManager.variantSupportsInstantRun(myFacet.getModule());
    return new DeployApkTask(myFacet, myLaunchOptions, myApkProvider, instantRunAware);
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
}
