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


import com.android.ddmlib.AdbHelper;
import com.android.ddmlib.IDevice;
import com.android.ide.common.build.BaselineProfileDetails;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.AdbClient;
import com.android.tools.deployer.AdbInstaller;
import com.android.tools.deployer.ChangeType;
import com.android.tools.deployer.DeployMetric;
import com.android.tools.deployer.Deployer;
import com.android.tools.deployer.DeployerException;
import com.android.tools.deployer.DeployerOption;
import com.android.tools.deployer.Installer;
import com.android.tools.deployer.MetricsRecorder;
import com.android.tools.deployer.model.App;
import com.android.tools.deployer.model.BaselineProfile;
import com.android.tools.deployer.tasks.Canceller;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.flags.StudioFlags.OptimisticInstallSupportLevel;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.run.ApkFileUnit;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.DeploymentService;
import com.android.tools.idea.run.IdeService;
import com.android.utils.ILogger;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.ApplyChangesAgentError;
import com.google.wireless.android.sdk.stats.LaunchTaskDetail;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractDeployTask {

  public static final int MIN_API_VERSION = 26;
  public static final Logger LOG = Logger.getInstance(AbstractDeployTask.class);
  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroupManager.getInstance().getNotificationGroup("Deploy");
  private static final Map<OptimisticInstallSupportLevel, EnumSet<ChangeType>> OPTIMISTIC_INSTALL_SUPPORT =
    ImmutableMap.of(OptimisticInstallSupportLevel.DISABLED, EnumSet.noneOf(ChangeType.class), OptimisticInstallSupportLevel.DEX,
                    EnumSet.of(ChangeType.DEX), OptimisticInstallSupportLevel.DEX_AND_NATIVE,
                    EnumSet.of(ChangeType.DEX, ChangeType.NATIVE_LIBRARY), OptimisticInstallSupportLevel.DEX_AND_NATIVE_AND_RESOURCES,
                    EnumSet.of(ChangeType.DEX, ChangeType.NATIVE_LIBRARY, ChangeType.RESOURCE));
  protected final boolean myRerunOnSwapFailure;
  protected final boolean myAlwaysInstallWithPm;
  private final Computable<String> myInstallPathProvider;
  @NotNull private final Project myProject;
  @NotNull private final Collection<ApkInfo> myPackages;
  @NotNull protected List<LaunchTaskDetail> mySubTaskDetails;

  public AbstractDeployTask(@NotNull Project project,
                            @NotNull Collection<ApkInfo> packages,
                            boolean rerunOnSwapFailure,
                            boolean alwaysInstallWithPm,
                            Computable<String> installPathProvider
  ) {
    myProject = project;
    myPackages = packages;
    myRerunOnSwapFailure = rerunOnSwapFailure;
    myAlwaysInstallWithPm = alwaysInstallWithPm;
    myInstallPathProvider = installPathProvider;
    mySubTaskDetails = new ArrayList<>();
  }

    public List<Deployer.Result> run(@NotNull IDevice device, ProgressIndicator indicator)
            throws DeployerException {
    Canceller canceller = new Canceller() {
      @Override
      public boolean cancelled() {
        return indicator.isCanceled();
      }
    };

    ILogger logger = new LogWrapper(LOG);
    Stopwatch stopwatch = Stopwatch.createStarted();

    // Collection that will accumulate metrics for the deployment.
    MetricsRecorder metrics = new MetricsRecorder();
    // VM clock timestamp used to snap metric times to wall-clock time.
    long vmClockStartNs = System.nanoTime();
    // Wall-clock start time for the deployment.
    long wallClockStartMs = System.currentTimeMillis();

    AdbClient adb = new AdbClient(device, logger);

    AdbHelper.setAbbExecAllowed(StudioFlags.DDMLIB_ABB_EXEC_INSTALL_ENABLE.get());

    AdbInstaller.Mode adbInstallerMode = AdbInstaller.Mode.DAEMON;
    if (!StudioFlags.APPLY_CHANGES_KEEP_CONNECTION_ALIVE.get()) {
      adbInstallerMode = AdbInstaller.Mode.ONE_SHOT;
    }
    Installer installer = new AdbInstaller(myInstallPathProvider.get(), adb, metrics.getDeployMetrics(), logger, adbInstallerMode);

    DeploymentService service = DeploymentService.getInstance();
    IdeService ideService = new IdeService(myProject);

    EnumSet<ChangeType> optimisticInstallSupport = EnumSet.noneOf(ChangeType.class);
    if (!myAlwaysInstallWithPm) {
      optimisticInstallSupport =
        OPTIMISTIC_INSTALL_SUPPORT.getOrDefault(StudioFlags.OPTIMISTIC_INSTALL_SUPPORT_LEVEL.get(), EnumSet.noneOf(ChangeType.class));
    }
    DeployerOption option = new DeployerOption.Builder().setUseOptimisticSwap(StudioFlags.APPLY_CHANGES_OPTIMISTIC_SWAP.get())
      .setUseOptimisticResourceSwap(StudioFlags.APPLY_CHANGES_OPTIMISTIC_RESOURCE_SWAP.get())
      .setOptimisticInstallSupport(optimisticInstallSupport)
      .setUseStructuralRedefinition(StudioFlags.APPLY_CHANGES_STRUCTURAL_DEFINITION.get())
      .setUseVariableReinitialization(StudioFlags.APPLY_CHANGES_VARIABLE_REINITIALIZATION.get())
      .setFastRestartOnSwapFail(getFastRerunOnSwapFailure()).enableCoroutineDebugger(StudioFlags.COROUTINE_DEBUGGER_ENABLE.get()).build();
    Deployer deployer =
      new Deployer(adb, service.getDeploymentCacheDatabase(), service.getDexDatabase(), service.getTaskRunner(), installer, ideService,
                   metrics, logger, option);
    List<String> idsSkippedInstall = new ArrayList<>();
    List<Deployer.Result> results = new ArrayList<>();
    for (ApkInfo apkInfo : myPackages) {
      Deployer.Result result = perform(device, deployer, apkInfo, canceller);

      if (result.skippedInstall) {
        idsSkippedInstall.add(apkInfo.getApplicationId());
      }

      results.add(result);
    }

    addSubTaskDetails(metrics.getDeployMetrics(), vmClockStartNs, wallClockStartMs);
    logAgentFailures(metrics.getAgentFailures());

    stopwatch.stop();
    long duration = stopwatch.elapsed(TimeUnit.MILLISECONDS);
    if (idsSkippedInstall.isEmpty()) {
      String content = String.format("%s successfully finished in %s.", getDescription(), StringUtil.formatDuration(duration));
      NOTIFICATION_GROUP.createNotification(content, NotificationType.INFORMATION).notify(myProject);
    }
    else {
      String title = String.format("%s successfully finished in %s.", getDescription(), StringUtil.formatDuration(duration));
      String content = createSkippedApkInstallMessage(idsSkippedInstall, idsSkippedInstall.size() == myPackages.size());
      NOTIFICATION_GROUP.createNotification(title, content, NotificationType.INFORMATION).notify(myProject);
    }

    return results;
  }

    protected abstract String getDescription();

  abstract protected Deployer.Result perform(IDevice device, Deployer deployer, @NotNull ApkInfo apkInfo, @NotNull Canceller canceller)
    throws DeployerException;

  @NotNull
  protected Project getProject() {
    return myProject;
  }

  final boolean getFastRerunOnSwapFailure() {
    return myRerunOnSwapFailure;
  }

  private void addSubTaskDetails(@NotNull Collection<DeployMetric> metrics, long startNanoTime, long startWallClockMs) {
    for (DeployMetric metric : metrics) {
      if (!metric.getName().isEmpty()) {
        LaunchTaskDetail.Builder detail = LaunchTaskDetail.newBuilder();

        long startOffsetMs = TimeUnit.NANOSECONDS.toMillis(metric.getStartTimeNs() - startNanoTime);
        long endOffsetMs = TimeUnit.NANOSECONDS.toMillis(metric.getEndTimeNs() - startNanoTime);

        detail.setId(getId() + "." + metric.getName()).setStartTimestampMs(startWallClockMs + startOffsetMs)
          .setEndTimestampMs(startWallClockMs + endOffsetMs).setTid((int)metric.getThreadId());

        if (metric.hasStatus()) {
          detail.setStatus(metric.getStatus());
        }
        mySubTaskDetails.add(detail.build());
      }
    }
  }

    public abstract String getId();

  private void logAgentFailures(List<Deploy.AgentExceptionLog> agentExceptionLogs) {
    for (Deploy.AgentExceptionLog log : agentExceptionLogs) {
      UsageTracker.log(toStudioEvent(log));
    }
  }

  @NotNull
  public Collection<LaunchTaskDetail> getSubTaskDetails() {
    return mySubTaskDetails;
  }

  protected abstract String createSkippedApkInstallMessage(List<String> skippedApkList, boolean all);

  @NotNull
  public Collection<ApkInfo> getApkInfos() {
    return myPackages;
  }

  public static App getAppToInstall(@NotNull ApkInfo apkInfo) {
    List<Path> paths = apkInfo.getFiles().stream().map(ApkFileUnit::getApkPath).collect(Collectors.toList());
    List<BaselineProfile> baselineProfiles = convertBaseLinesProfiles(apkInfo.getBaselineProfiles());
    return App.fromPaths(apkInfo.getApplicationId(),paths, baselineProfiles);
  }

  private static List<BaselineProfile> convertBaseLinesProfiles(List<BaselineProfileDetails> profiles) {
    return profiles.stream().map(p -> new BaselineProfile(
      p.getMinApi(),
      p.getMaxApi(),
      p.getBaselineProfiles().stream().map(File::toPath).collect(Collectors.toList()))
    ).collect(Collectors.toList());
  }

  private static AndroidStudioEvent.Builder toStudioEvent(Deploy.AgentExceptionLog log) {
    ApplyChangesAgentError.AgentPurpose purpose = ApplyChangesAgentError.AgentPurpose.forNumber(log.getAgentPurposeValue());
    ApplyChangesAgentError.Builder builder =
      ApplyChangesAgentError.newBuilder().setEventTimeMs(TimeUnit.MILLISECONDS.convert(log.getEventTimeNs(), TimeUnit.NANOSECONDS))
        .setAgentAttachTimeMs(TimeUnit.MILLISECONDS.convert(log.getAgentAttachTimeNs(), TimeUnit.NANOSECONDS))
        .setAgentAttachCount(log.getAgentAttachCount()).setAgentPurpose(purpose);
    log.getFailedClassesList().stream().map(ApplyChangesAgentError.TargetClass::valueOf).forEach(builder::addTargetClasses);
    return AndroidStudioEvent.newBuilder().setCategory(AndroidStudioEvent.EventCategory.DEPLOYMENT)
      .setKind(AndroidStudioEvent.EventKind.APPLY_CHANGES_AGENT_ERROR).setApplyChangesAgentError(builder);
  }
}
