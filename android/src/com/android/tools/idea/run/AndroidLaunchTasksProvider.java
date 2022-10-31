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
import static com.android.tools.idea.run.AndroidRunConfiguration.LAUNCH_DEEP_LINK;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.deploy.DeploymentConfiguration;
import com.android.tools.idea.editors.literals.LiveEditService;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.run.activity.launch.DeepLinkLaunch;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerContext;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.tasks.AppLaunchTask;
import com.android.tools.idea.run.tasks.ApplyChangesTask;
import com.android.tools.idea.run.tasks.ApplyCodeChangesTask;
import com.android.tools.idea.run.tasks.ClearAppStorageTask;
import com.android.tools.idea.run.tasks.ClearLogcatTask;
import com.android.tools.idea.run.tasks.ConnectDebuggerTask;
import com.android.tools.idea.run.tasks.DeployTask;
import com.android.tools.idea.run.tasks.DismissKeyguardTask;
import com.android.tools.idea.run.tasks.KillAndRestartAppLaunchTask;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.tasks.RunInstantAppTask;
import com.android.tools.idea.run.tasks.ShowLogcatTask;
import com.android.tools.idea.run.tasks.StartLiveUpdateMonitoringTask;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.SwapInfo;
import com.android.tools.idea.stats.RunStats;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLaunchTasksProvider implements LaunchTasksProvider {
  private final Logger myLogger = Logger.getInstance(AndroidLaunchTasksProvider.class);
  private final AndroidRunConfigurationBase myRunConfig;
  private final ExecutionEnvironment myEnv;
  private final AndroidFacet myFacet;
  private final ApplicationIdProvider myApplicationIdProvider;
  private final ApkProvider myApkProvider;
  private final LaunchOptions myLaunchOptions;
  private final Project myProject;

  public AndroidLaunchTasksProvider(@NotNull AndroidRunConfigurationBase runConfig,
                                    @NotNull ExecutionEnvironment env,
                                    @NotNull AndroidFacet facet,
                                    @NotNull ApplicationIdProvider applicationIdProvider,
                                    @NotNull ApkProvider apkProvider,
                                    @NotNull LaunchOptions launchOptions) {
    myRunConfig = runConfig;
    myEnv = env;
    myProject = facet.getModule().getProject();
    myFacet = facet;
    myApplicationIdProvider = applicationIdProvider;
    myApkProvider = apkProvider;
    myLaunchOptions = launchOptions;
  }

  @NotNull
  @Override
  public List<LaunchTask> getTasks(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter consolePrinter) {
    final List<LaunchTask> launchTasks = new ArrayList<>();

    if (myLaunchOptions.isClearLogcatBeforeStart()) {
      launchTasks.add(new ClearLogcatTask(myProject));
    }

    launchTasks.add(new DismissKeyguardTask());

    final boolean useApplyChanges = shouldApplyChanges() || shouldApplyCodeChanges();
    final boolean terminateLaunchOnError = !useApplyChanges && !shouldDeployAsInstant();
    String packageName;
    try {
      packageName = myApplicationIdProvider.getPackageName();
      launchTasks.addAll(getDeployTasks(device, packageName));

      StringBuilder amStartOptions = new StringBuilder();
      // launch the contributors before launching the application in case
      // the contributors need to start listening on logcat for the application launch itself
      for (AndroidLaunchTaskContributor taskContributor : AndroidLaunchTaskContributor.EP_NAME.getExtensions()) {
        String amOptions = taskContributor.getAmStartOptions(packageName, myRunConfig, device, myEnv.getExecutor());
        amStartOptions.append(amStartOptions.length() == 0 ? "" : " ").append(amOptions);

        LaunchTask task = taskContributor.getTask(packageName, myRunConfig, device, myEnv.getExecutor());
        if (task != null) {
          launchTasks.add(task);
        }
      }

      if (!shouldDeployAsInstant()) {
        // A separate deep link launch task is not necessary if launch will be handled by
        // RunInstantAppTask
        AppLaunchTask appLaunchTask = myRunConfig.getApplicationLaunchTask(myApplicationIdProvider, myFacet,
                                                                           amStartOptions.toString(),
                                                                           myLaunchOptions.isDebug(), launchStatus, myApkProvider,
                                                                           consolePrinter, device);

        if (appLaunchTask != null) {
          // Apply (Code) Changes needs additional control over killing/restarting the app.
          launchTasks.add(new KillAndRestartAppLaunchTask(packageName));
          launchTasks.add(appLaunchTask);
        }
      }
    }
    catch (ApkProvisionException e) {
      if (useApplyChanges) {
        // ApkProvisionException should not happen for apply-changes launch. Log it at higher level.
        myLogger.error(e);
      } else {
        myLogger.warn(e);
      }
      launchStatus.terminateLaunch(e.getMessage(), /*destroyProcess=*/terminateLaunchOnError);
      return Collections.emptyList();
    }
    catch (IllegalStateException e) {
      myLogger.error(e);
      launchStatus.terminateLaunch(e.getMessage(), /*destroyProcess=*/terminateLaunchOnError);
      return Collections.emptyList();
    }

    if (!myLaunchOptions.isDebug() && myLaunchOptions.isOpenLogcatAutomatically()) {
      launchTasks.add(new ShowLogcatTask(myProject, packageName));
    }

    return launchTasks;
  }

  @NotNull
  @VisibleForTesting
  List<LaunchTask> getDeployTasks(@NotNull final IDevice device, @NotNull final String packageName) throws ApkProvisionException {

    // regular APK deploy flow
    if (!myLaunchOptions.isDeploy()) {
      return Collections.emptyList();
    }

    List<LaunchTask> tasks = new ArrayList<>();
    DeployType deployType = getDeployType();

    List<String> disabledFeatures = myLaunchOptions.getDisabledDynamicFeatures();
    // Add packages to the deployment, filtering out any dynamic features that are disabled.
    List<ApkInfo> packages = myApkProvider.getApks(device).stream()
      .map(apkInfo -> filterDisabledFeatures(apkInfo, disabledFeatures))
      .collect(Collectors.toList());
    switch (deployType) {
      case RUN_INSTANT_APP:
        if (myLaunchOptions.isClearAppStorage()) {
          tasks.add(new ClearAppStorageTask(packageName));
        }
        AndroidRunConfiguration runConfig = (AndroidRunConfiguration)myRunConfig;
        DeepLinkLaunch.State state = (DeepLinkLaunch.State)runConfig.getLaunchOptionState(LAUNCH_DEEP_LINK);
        assert state != null;
        tasks.add(new RunInstantAppTask(myApkProvider.getApks(device), state.DEEP_LINK, disabledFeatures));
        break;
      case APPLY_CHANGES:
        tasks.add(new ApplyChangesTask(
          myProject,
          packages,
          isApplyChangesFallbackToRun(),
          myLaunchOptions.getAlwaysInstallWithPm()));
        tasks.add(new StartLiveUpdateMonitoringTask(AndroidLiveLiteralDeployMonitor.getCallback(myProject, packageName, device)));
        tasks.add(new StartLiveUpdateMonitoringTask(LiveEditService.getInstance(myProject).getCallback(packageName, device)));

        break;
      case APPLY_CODE_CHANGES:
        tasks.add(new ApplyCodeChangesTask(
          myProject,
          packages,
          isApplyCodeChangesFallbackToRun(),
          myLaunchOptions.getAlwaysInstallWithPm()));
        tasks.add(new StartLiveUpdateMonitoringTask(AndroidLiveLiteralDeployMonitor.getCallback(myProject, packageName, device)));
        tasks.add(new StartLiveUpdateMonitoringTask(LiveEditService.getInstance(myProject).getCallback(packageName, device)));
        break;
      case DEPLOY:
        if (myLaunchOptions.isClearAppStorage()) {
          tasks.add(new ClearAppStorageTask(packageName));
        }
        tasks.add(new DeployTask(
          myProject,
          packages,
          myLaunchOptions.getPmInstallOptions(device),
          myLaunchOptions.getInstallOnAllUsers(),
          myLaunchOptions.getAlwaysInstallWithPm()));
        tasks.add(new StartLiveUpdateMonitoringTask(AndroidLiveLiteralDeployMonitor.getCallback(myProject, packageName, device)));
        if (myEnv.getExecutor() == DefaultDebugExecutor.getDebugExecutorInstance()) {
          LiveEditService.getInstance(myProject).notifyDebug(packageName, device);
        }
        else {
          tasks.add(new StartLiveUpdateMonitoringTask(LiveEditService.getInstance(myProject).getCallback(packageName, device)));
        }
        break;
      default: throw new IllegalStateException("Unhandled Deploy Type");
    }
    return ImmutableList.copyOf(tasks);
  }

  @Override
  public String getLaunchTypeDisplayName() {
    return getDeployType().asDisplayName();
  }


  private boolean isApplyCodeChangesFallbackToRun() {
    return DeploymentConfiguration.getInstance().APPLY_CODE_CHANGES_FALLBACK_TO_RUN;
  }

  private boolean isApplyChangesFallbackToRun() {
    return DeploymentConfiguration.getInstance().APPLY_CHANGES_FALLBACK_TO_RUN;
  }

  @Override
  public void fillStats(RunStats stats) {
    stats.setApplyChangesFallbackToRun(isApplyChangesFallbackToRun());
    stats.setApplyCodeChangesFallbackToRun(isApplyCodeChangesFallbackToRun());
    stats.setRunAlwaysInstallWithPm(myLaunchOptions.getAlwaysInstallWithPm());
  }

  @NotNull
  private static ApkInfo filterDisabledFeatures(ApkInfo apkInfo, List<String> disabledFeatures) {
    if (apkInfo.getFiles().size() > 1) {
      List<ApkFileUnit> filtered = apkInfo.getFiles().stream()
        .filter(feature -> DynamicAppUtils.isFeatureEnabled(disabledFeatures, feature))
        .collect(Collectors.toList());
      return new ApkInfo(filtered, apkInfo.getApplicationId());
    } else {
      return apkInfo;
    }
  }

  @Nullable
  @Override
  public ConnectDebuggerTask getConnectDebuggerTask() {
    if (!myLaunchOptions.isDebug()) {
      return null;
    }
    Logger logger = Logger.getInstance(AndroidLaunchTasksProvider.class);

    AndroidDebuggerContext androidDebuggerContext = myRunConfig.getAndroidDebuggerContext();
    AndroidDebugger debugger = androidDebuggerContext.getAndroidDebugger();
    if (debugger == null) {
      logger.warn("Unable to determine debugger to use for this launch");
      return null;
    }
    logger.info("Using debugger: " + debugger.getId());

    AndroidDebuggerState androidDebuggerState = androidDebuggerContext.getAndroidDebuggerState();
    if (androidDebuggerState != null) {
      //noinspection unchecked
      return debugger.getConnectDebuggerTask(myEnv,
                                             myApplicationIdProvider,
                                             myFacet,
                                             androidDebuggerState);
    }

    logger.warn("No debugger state present and cannot get debugger task");
    return null;
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

  private enum DeployType {
    RUN_INSTANT_APP{
      @Override
      public String asDisplayName() {
        return "Instant App Launch";
      }},
    APPLY_CHANGES{
      @Override
      public String asDisplayName() {
        return "Apply Changes";
      }},
    APPLY_CODE_CHANGES {
      @Override
      public String asDisplayName() {
        return "Apply Code Changes";
      }},
    DEPLOY {
      @Override
      public String asDisplayName() {
        return "Launch";
      }},
    ;
    public abstract String asDisplayName();
  }

  private DeployType getDeployType() {
    if (shouldDeployAsInstant()) {
      return DeployType.RUN_INSTANT_APP;
    } else if (shouldApplyChanges()) {
      return DeployType.APPLY_CHANGES;
    } else if (shouldApplyCodeChanges()) {
      return DeployType.APPLY_CODE_CHANGES;
    } else {
      return DeployType.DEPLOY;
    }
  }
}
