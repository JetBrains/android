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

import static com.android.AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP;
import static com.android.sdklib.AndroidVersion.VersionCodes.TIRAMISU;
import static com.android.tools.idea.flags.StudioFlags.LAUNCH_SANDBOX_SDK_PROCESS_WITH_DEBUGGER_ATTACHED_ON_DEBUG;
import static com.android.tools.idea.run.AndroidRunConfiguration.LAUNCH_DEEP_LINK;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.deploy.DeploymentConfiguration;
import com.android.tools.idea.editors.literals.LiveEditService;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.run.activity.launch.DeepLinkLaunch;
import com.android.tools.idea.run.deployment.liveedit.LiveEditApp;
import com.android.tools.idea.run.tasks.AppLaunchTask;
import com.android.tools.idea.run.tasks.ApplyChangesTask;
import com.android.tools.idea.run.tasks.ApplyCodeChangesTask;
import com.android.tools.idea.run.tasks.DeployTask;
import com.android.tools.idea.run.tasks.KillAndRestartAppLaunchTask;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.RunInstantAppTask;
import com.android.tools.idea.run.tasks.SandboxSdkLaunchTask;
import com.android.tools.idea.run.tasks.StartLiveUpdateMonitoringTask;
import com.android.tools.idea.run.util.SwapInfo;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class AndroidLaunchTasksProvider {
  private final Logger myLogger = Logger.getInstance(AndroidLaunchTasksProvider.class);
  private final AndroidRunConfiguration myRunConfig;
  private final ExecutionEnvironment myEnv;
  private final AndroidFacet myFacet;
  private final String myPackageName;
  private final ApkProvider myApkProvider;
  private final LaunchOptions myLaunchOptions;
  private final Boolean myDebug;
  private final Project myProject;

  public AndroidLaunchTasksProvider(@NotNull AndroidRunConfiguration runConfig,
                                    @NotNull ExecutionEnvironment env,
                                    @NotNull AndroidFacet facet,
                                    @NotNull String packageName,
                                    @NotNull ApkProvider apkProvider,
                                    @NotNull LaunchOptions launchOptions,
                                    Boolean isDebug
  ) {
    myRunConfig = runConfig;
    myEnv = env;
    myProject = facet.getModule().getProject();
    myFacet = facet;
    myPackageName = packageName;
    myApkProvider = apkProvider;
    myLaunchOptions = launchOptions;
    myDebug = isDebug;
  }

  @NotNull
  public List<LaunchTask> getLaunchTasks(@NotNull IDevice device) throws ExecutionException {
    final List<LaunchTask> launchTasks = new ArrayList<>();

    if (!shouldDeployAsInstant()) {
      // A separate deep link launch task is not necessary if launch will be handled by
      // RunInstantAppTask

      StringBuilder amStartOptions = new StringBuilder();
      // launch the contributors before launching the application in case
      // the contributors need to start listening on logcat for the application launch itself
      for (AndroidLaunchTaskContributor taskContributor : AndroidLaunchTaskContributor.EP_NAME.getExtensions()) {
        String amOptions = taskContributor.getAmStartOptions(myPackageName, myRunConfig, device, myEnv.getExecutor());
        amStartOptions.append(amStartOptions.length() == 0 ? "" : " ").append(amOptions);
      }

      AppLaunchTask appLaunchTask = myRunConfig.getApplicationLaunchTask(myPackageName, myFacet,
                                                                         amStartOptions.toString(),
                                                                         myDebug,
                                                                         myApkProvider,
                                                                         device);

      if (appLaunchTask != null) {
        // Apply (Code) Changes needs additional control over killing/restarting the app.
        launchTasks.add(new KillAndRestartAppLaunchTask(myPackageName));
        if (shouldDebugSandboxSdk(device)) {
          launchTasks.add(new SandboxSdkLaunchTask(myPackageName));
        }
        launchTasks.add(appLaunchTask);
      }
    }

    return launchTasks;
  }

  @NotNull
  public List<LaunchTask> getDeployTasks(@NotNull final IDevice device, @NotNull final String packageName) throws ExecutionException {
    // regular APK deploy flow
    if (!myLaunchOptions.isDeploy()) {
      return Collections.emptyList();
    }

    List<LaunchTask> tasks = new ArrayList<>();
    DeployType deployType = getDeployType();

    List<String> disabledFeatures = myRunConfig.getDisabledDynamicFeatures();
    // Add packages to the deployment, filtering out any dynamic features that are disabled.
    Collection<ApkInfo> apks = null;
    try {
      apks = myApkProvider.getApks(device);
    }
    catch (ApkProvisionException e) {
      throw new ExecutionException(e);
    }
    List<ApkInfo> packages = apks.stream()
      .map(apkInfo -> filterDisabledFeatures(apkInfo, disabledFeatures))
      .collect(Collectors.toList());
    switch (deployType) {
      case RUN_INSTANT_APP:
        DeepLinkLaunch.State state = (DeepLinkLaunch.State)myRunConfig.getLaunchOptionState(LAUNCH_DEEP_LINK);
        assert state != null;
        tasks.add(new RunInstantAppTask(apks, state.DEEP_LINK, disabledFeatures));
        break;
      case APPLY_CHANGES:
        tasks.add(new ApplyChangesTask(
          myProject,
          packages,
          isApplyChangesFallbackToRun(),
          myRunConfig.ALWAYS_INSTALL_WITH_PM));
        tasks.add(new StartLiveUpdateMonitoringTask(AndroidLiveLiteralDeployMonitor.getCallback(myProject, packageName, device)));
        tasks.add(new StartLiveUpdateMonitoringTask(() -> LiveEditService.getInstance(myProject).notifyAppRefresh(device)));

        break;
      case APPLY_CODE_CHANGES:
        tasks.add(new ApplyCodeChangesTask(
          myProject,
          packages,
          isApplyCodeChangesFallbackToRun(),
          myRunConfig.ALWAYS_INSTALL_WITH_PM));
        tasks.add(new StartLiveUpdateMonitoringTask(AndroidLiveLiteralDeployMonitor.getCallback(myProject, packageName, device)));
        tasks.add(new StartLiveUpdateMonitoringTask(() -> LiveEditService.getInstance(myProject).notifyAppRefresh(device)));
        break;
      case DEPLOY:
        tasks.add(new DeployTask(
          myProject,
          packages,
          myRunConfig.PM_INSTALL_OPTIONS,
          myRunConfig.ALL_USERS,
          myRunConfig.ALWAYS_INSTALL_WITH_PM));
        tasks.add(new StartLiveUpdateMonitoringTask(AndroidLiveLiteralDeployMonitor.getCallback(myProject, packageName, device)));
        LiveEditApp app = new LiveEditApp(getApkPaths(apks), device.getVersion().getApiLevel());
        tasks.add(new StartLiveUpdateMonitoringTask(() -> LiveEditService.getInstance(myProject).notifyAppDeploy(
          myRunConfig, myEnv.getExecutor(), packageName, device, app)));
        break;
      default:
        throw new IllegalStateException("Unhandled Deploy Type");
    }
    return ImmutableList.copyOf(tasks);
  }

  private boolean isApplyCodeChangesFallbackToRun() {
    return DeploymentConfiguration.getInstance().APPLY_CODE_CHANGES_FALLBACK_TO_RUN;
  }

  private boolean isApplyChangesFallbackToRun() {
    return DeploymentConfiguration.getInstance().APPLY_CHANGES_FALLBACK_TO_RUN;
  }

  private boolean shouldDeployAsInstant() {
    return (myFacet.getConfiguration().getProjectType() == PROJECT_TYPE_INSTANTAPP ||
            myLaunchOptions.isDeployAsInstant());
  }

  private boolean shouldApplyChanges() {
    SwapInfo swapInfo = myEnv.getUserData(SwapInfo.SWAP_INFO_KEY);
    return swapInfo != null && swapInfo.getType() == SwapInfo.SwapType.APPLY_CHANGES;
  }

  private boolean shouldApplyCodeChanges() {
    SwapInfo swapInfo = myEnv.getUserData(SwapInfo.SWAP_INFO_KEY);
    return swapInfo != null && swapInfo.getType() == SwapInfo.SwapType.APPLY_CODE_CHANGES;
  }

  private boolean shouldDebugSandboxSdk(IDevice device) {
    return hasDebugSandboxSdkEnabled() &&
           device.getVersion().isGreaterOrEqualThan(TIRAMISU) &&
           myDebug &&
           hasPrivacySandboxSdk(device);
  }

  private boolean hasDebugSandboxSdkEnabled() {
    AndroidDebuggerState state = myRunConfig.getAndroidDebuggerContext().getAndroidDebuggerState();
    if (state != null) {
      return LAUNCH_SANDBOX_SDK_PROCESS_WITH_DEBUGGER_ATTACHED_ON_DEBUG.get() && state.DEBUG_SANDBOX_SDK;
    }

    return false;
  }

  private boolean hasPrivacySandboxSdk(IDevice device) {
    try {
      Collection<ApkInfo> apkList = myApkProvider.getApks(device);
      for (ApkInfo apk : apkList) {
        if (apk.isSandboxApk()) {
          return true;
        }
      }

      return false;
    }
    catch (ApkProvisionException e) {
      return false;
    }
  }

  private DeployType getDeployType() {
    if (shouldDeployAsInstant()) {
      return DeployType.RUN_INSTANT_APP;
    }
    else if (shouldApplyChanges()) {
      return DeployType.APPLY_CHANGES;
    }
    else if (shouldApplyCodeChanges()) {
      return DeployType.APPLY_CODE_CHANGES;
    }
    else {
      return DeployType.DEPLOY;
    }
  }

  private static Set<Path> getApkPaths(Iterable<? extends ApkInfo> apks) {
    Set<Path> apksPaths = new HashSet<>();
    for (ApkInfo apkInfo : apks) {
      for (ApkFileUnit apkFileUnit : apkInfo.getFiles()) {
        apksPaths.add(apkFileUnit.getApkPath());
      }
    }
    return apksPaths;
  }

  @NotNull
  private static ApkInfo filterDisabledFeatures(ApkInfo apkInfo, List<String> disabledFeatures) {
    if (apkInfo.getFiles().size() > 1) {
      List<ApkFileUnit> filtered = apkInfo.getFiles().stream()
        .filter(feature -> DynamicAppUtils.isFeatureEnabled(disabledFeatures, feature))
        .collect(Collectors.toList());
      return new ApkInfo(filtered, apkInfo.getApplicationId());
    }
    else {
      return apkInfo;
    }
  }

  private enum DeployType {
    RUN_INSTANT_APP {
      @Override
      public String asDisplayName() {
        return "Instant App Launch";
      }
    },
    APPLY_CHANGES {
      @Override
      public String asDisplayName() {
        return "Apply Changes";
      }
    },
    APPLY_CODE_CHANGES {
      @Override
      public String asDisplayName() {
        return "Apply Code Changes";
      }
    },
    DEPLOY {
      @Override
      public String asDisplayName() {
        return "Launch";
      }
    },
    ;

    public abstract String asDisplayName();
  }
}
