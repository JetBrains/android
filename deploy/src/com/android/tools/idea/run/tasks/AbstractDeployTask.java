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
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.flags.StudioFlags.OptimisticInstallSupportLevel;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.DeploymentService;
import com.android.tools.idea.run.IdeService;
import com.android.tools.idea.run.ui.ApplyChangesAction;
import com.android.tools.idea.run.ui.BaseAction;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.ApplyChangesAgentError;
import com.google.wireless.android.sdk.stats.LaunchTaskDetail;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.commands.ActionCommand;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractDeployTask implements LaunchTask {

  public static final int MIN_API_VERSION = 26;
  private static final NotificationGroup NOTIFICATION_GROUP =
    NotificationGroup.toolWindowGroup("UnifiedDeployTask", ToolWindowId.RUN, true, PluginId.getId("org.jetbrains.android"));

    private static final Map<OptimisticInstallSupportLevel, EnumSet<ChangeType>>
            OPTIMISTIC_INSTALL_SUPPORT =
                    ImmutableMap.of(
                            OptimisticInstallSupportLevel.DISABLED, EnumSet.noneOf(ChangeType.class),
                            OptimisticInstallSupportLevel.DEX, EnumSet.of(ChangeType.DEX),
                            OptimisticInstallSupportLevel.DEX_AND_NATIVE,
                                    EnumSet.of(ChangeType.DEX, ChangeType.NATIVE_LIBRARY),
                            OptimisticInstallSupportLevel.DEX_AND_NATIVE_AND_RESOURCES,
                                    EnumSet.of(
                                            ChangeType.DEX,
                                            ChangeType.NATIVE_LIBRARY,
                                            ChangeType.RESOURCE));

  @NotNull private final Project myProject;
  @NotNull private final Map<String, List<File>> myPackages;
  @NotNull protected List<LaunchTaskDetail> mySubTaskDetails;
  protected final boolean myRerunOnSwapFailure;
  protected final boolean myAlwaysInstallWithPm;
  private final Computable<String> myInstallPathProvider;

  public static final Logger LOG = Logger.getInstance(AbstractDeployTask.class);

  public AbstractDeployTask(
      @NotNull Project project, @NotNull Map<String, List<File>> packages, boolean rerunOnSwapFailure, boolean alwaysInstallWithPm,
                            Computable<String> installPathProvider) {
    myProject = project;
    myPackages = packages;
    myRerunOnSwapFailure = rerunOnSwapFailure;
    myAlwaysInstallWithPm = alwaysInstallWithPm;
    myInstallPathProvider = installPathProvider;
    mySubTaskDetails = new ArrayList<>();
  }

  @Override
  public int getDuration() {
    return 20;
  }

  @Override
  public LaunchResult run(@NotNull LaunchContext launchContext) {
    Stopwatch stopwatch = Stopwatch.createStarted();
    LogWrapper logger = new LogWrapper(LOG);

    // Collection that will accumulate metrics for the deployment.
    MetricsRecorder metrics = new MetricsRecorder();
    // VM clock timestamp used to snap metric times to wall-clock time.
    long vmClockStartNs = System.nanoTime();
    // Wall-clock start time for the deployment.
    long wallClockStartMs = System.currentTimeMillis();

    IDevice device = launchContext.getDevice();
    Executor executor = launchContext.getExecutor();
    ConsolePrinter printer = launchContext.getConsolePrinter();
    AdbClient adb = new AdbClient(device, logger);

    AdbInstaller.Mode adbInstallerMode = AdbInstaller.Mode.DAEMON;
    if (!StudioFlags.APPLY_CHANGES_KEEP_CONNECTION_ALIVE.get()) {
      adbInstallerMode = AdbInstaller.Mode.ONE_SHOT;
    }
    Installer installer = new AdbInstaller(getLocalInstaller(), adb, metrics.getDeployMetrics(), logger, adbInstallerMode);

    DeploymentService service = DeploymentService.getInstance(myProject);
    IdeService ideService = new IdeService(myProject);

    EnumSet<ChangeType> optimisticInstallSupport = EnumSet.noneOf(ChangeType.class);
    if (!myAlwaysInstallWithPm) {
      optimisticInstallSupport = OPTIMISTIC_INSTALL_SUPPORT.getOrDefault(
        StudioFlags.OPTIMISTIC_INSTALL_SUPPORT_LEVEL.get(), EnumSet.noneOf(ChangeType.class));
    }
    DeployerOption option =
      new DeployerOption.Builder()
        .setUseOptimisticSwap(StudioFlags.APPLY_CHANGES_OPTIMISTIC_SWAP.get())
        .setUseOptimisticResourceSwap(StudioFlags.APPLY_CHANGES_OPTIMISTIC_RESOURCE_SWAP.get())
        .setOptimisticInstallSupport(optimisticInstallSupport)
        .setUseStructuralRedefinition(StudioFlags.APPLY_CHANGES_STRUCTURAL_DEFINITION.get())
        .setUseVariableReinitialization(StudioFlags.APPLY_CHANGES_VARIABLE_REINITIALIZATION.get())
        .setFastRestartOnSwapFail(getFastRerunOnSwapFailure())
        .build();
    Deployer deployer = new Deployer(adb, service.getDeploymentCacheDatabase(), service.getDexDatabase(), service.getTaskRunner(),
                                     installer, ideService, metrics, logger, option);
    List<String> idsSkippedInstall = new ArrayList<>();
    for (Map.Entry<String, List<File>> entry : myPackages.entrySet()) {
      String applicationId = entry.getKey();
      List<File> apkFiles = entry.getValue();
      try {
        launchContext.setLaunchApp(shouldTaskLaunchApp());
        Deployer.Result result = perform(device, deployer, applicationId, apkFiles);
        addSubTaskDetails(metrics.getDeployMetrics(), vmClockStartNs, wallClockStartMs);
        logAgentFailures(metrics.getAgentFailures());
        if (result.skippedInstall) {
          idsSkippedInstall.add(applicationId);
        }
        if (result.needsRestart) {
          // TODO: fall back to using the suggested action, rather than blindly rerun
          launchContext.setKillBeforeLaunch(true);
          launchContext.setLaunchApp(true);
        }
      }
      catch (DeployerException e) {
        logger.warning("%s failed: %s %s", getDescription(), e.getMessage(), e.getDetails());
        return toLaunchResult(executor, e, printer);
      }
    }

    stopwatch.stop();
    long duration = stopwatch.elapsed(TimeUnit.MILLISECONDS);
    if (idsSkippedInstall.isEmpty()) {
      String content = String.format("%s successfully finished in %s.", getDescription(), StringUtil.formatDuration(duration));
      printer.stdout(content);
      logger.info("%s", content);
    } else {
      String title = String.format("%s successfully finished in %s.", getDescription(), StringUtil.formatDuration(duration));
      String content = createSkippedApkInstallMessage(idsSkippedInstall, idsSkippedInstall.size() == myPackages.size());
      printer.stdout(content);
      logger.info("%s. %s", title, content);
    }

    return new LaunchResult();
  }

  abstract protected String getFailureTitle();

  abstract protected boolean shouldTaskLaunchApp();

  abstract protected Deployer.Result perform(
    IDevice device, Deployer deployer, String applicationId, List<File> files) throws DeployerException;

  private String getLocalInstaller() {
    return myInstallPathProvider.compute();
  }

  protected static List<String> getPathsToInstall(@NotNull List<File> apkFiles) {
    return ContainerUtil.map(apkFiles, File::getPath);
  }

  @NotNull
  protected Project getProject() {
    return myProject;
  }

  protected boolean getFastRerunOnSwapFailure() {
    return myRerunOnSwapFailure && StudioFlags.APPLY_CHANGES_FAST_RESTART_ON_SWAP_FAIL.get();
  }

  private void addSubTaskDetails(@NotNull Collection<DeployMetric> metrics, long startNanoTime,
                                 long startWallClockMs) {
    for (DeployMetric metric : metrics) {
      if (!metric.getName().isEmpty()) {
        LaunchTaskDetail.Builder detail = LaunchTaskDetail.newBuilder();

        long startOffsetMs = TimeUnit.NANOSECONDS.toMillis(metric.getStartTimeNs() - startNanoTime);
        long endOffsetMs = TimeUnit.NANOSECONDS.toMillis(metric.getEndTimeNs() - startNanoTime);

        detail.setId(getId() + "." + metric.getName())
          .setStartTimestampMs(startWallClockMs + startOffsetMs)
          .setEndTimestampMs(startWallClockMs + endOffsetMs)
          .setTid((int)metric.getThreadId());

        if (metric.hasStatus()) {
          detail.setStatus(metric.getStatus());
        }
        mySubTaskDetails.add(detail.build());
      }
    }
  }

  private void logAgentFailures(List<Deploy.AgentExceptionLog> agentExceptionLogs) {
    for (Deploy.AgentExceptionLog log : agentExceptionLogs) {
      UsageTracker.log(toStudioEvent(log));
    }
  }

  private static AndroidStudioEvent.Builder toStudioEvent(Deploy.AgentExceptionLog log) {
    ApplyChangesAgentError.AgentPurpose purpose = ApplyChangesAgentError.AgentPurpose.forNumber(log.getAgentPurposeValue());
    ApplyChangesAgentError.Builder builder = ApplyChangesAgentError.newBuilder()
      .setEventTimeMs(TimeUnit.MILLISECONDS.convert(log.getEventTimeNs(), TimeUnit.NANOSECONDS))
      .setAgentAttachTimeMs(TimeUnit.MILLISECONDS.convert(log.getAgentAttachTimeNs(), TimeUnit.NANOSECONDS))
      .setAgentAttachCount(log.getAgentAttachCount())
      .setAgentPurpose(purpose);
    log.getFailedClassesList().stream().map(ApplyChangesAgentError.TargetClass::valueOf).forEach(builder::addTargetClasses);
    return AndroidStudioEvent.newBuilder()
      .setCategory(AndroidStudioEvent.EventCategory.DEPLOYMENT)
      .setKind(AndroidStudioEvent.EventKind.APPLY_CHANGES_AGENT_ERROR)
      .setApplyChangesAgentError(builder);
  }

  @Override
  @NotNull
  public Collection<LaunchTaskDetail> getSubTaskDetails() {
    return mySubTaskDetails;
  }

  public LaunchResult toLaunchResult(@NotNull Executor executor, @NotNull DeployerException e, @NotNull ConsolePrinter printer) {
    LaunchResult result = new LaunchResult();
    result.setSuccess(false);

    StringBuilder bubbleError = new StringBuilder(getFailureTitle());
    bubbleError.append("\n");
    bubbleError.append(e.getMessage());

    DeployerException.Error error = e.getError();
    if (error.getResolution() != DeployerException.ResolutionAction.NONE) {
      if (myRerunOnSwapFailure) {
        bubbleError.append(String.format("\n%s will be done automatically</a>", error.getCallToAction()));
      } else {
        bubbleError.append(String.format("\n<a href='%s'>%s</a>", error.getResolution(), error.getCallToAction()));
      }
    }

    result.setError(bubbleError.toString());
    result.setConsoleError(getFailureTitle() + "\n" + e.getMessage() + "\n" + e.getDetails());
    result.setErrorId(e.getId());

    DeploymentHyperlinkInfo hyperlinkInfo = new DeploymentHyperlinkInfo(executor, error.getResolution(), printer);
    result.setConsoleHyperlink(error.getCallToAction(), hyperlinkInfo);
    result.setNotificationListener(new DeploymentErrorNotificationListener(error.getResolution(),
                                                                           hyperlinkInfo));
    if (myRerunOnSwapFailure) {
      result.addOnFinishedCallback(() -> hyperlinkInfo.navigate(myProject));
    }
    return result;
  }

  protected abstract String createSkippedApkInstallMessage(List<String> skippedApkList, boolean all);

  private class DeploymentErrorNotificationListener implements NotificationListener {
    private final @NotNull DeployerException.ResolutionAction myResolutionAction;
    private final @NotNull DeploymentHyperlinkInfo myHyperlinkInfo;

    public DeploymentErrorNotificationListener(@NotNull DeployerException.ResolutionAction resolutionAction,
                                               @NotNull DeploymentHyperlinkInfo hyperlinkInfo) {
      myResolutionAction = resolutionAction;
      myHyperlinkInfo = hyperlinkInfo;
    }

    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      // Check if the hyperlink target matches the target we set in toLaunchResult.
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED &&
          event.getDescription().equals(myResolutionAction.name())) {
        myHyperlinkInfo.navigate(myProject);
      }
    }
  }

  private class DeploymentHyperlinkInfo implements HyperlinkInfo {
    private final @Nullable String myActionId;
    @NotNull
    private final ConsolePrinter myPrinter;

    public DeploymentHyperlinkInfo(@NotNull Executor executor, @NotNull DeployerException.ResolutionAction resolutionAction, @NotNull ConsolePrinter printer) {
      myPrinter = printer;
      switch (resolutionAction) {
        case APPLY_CHANGES:
          myActionId = ApplyChangesAction.ID;
          break;
        case RUN_APP:
          myActionId = DefaultDebugExecutor.EXECUTOR_ID.equals(executor.getId())
                       ? IdeActions.ACTION_DEFAULT_DEBUGGER
                       : IdeActions.ACTION_DEFAULT_RUNNER;
          break;
        case RETRY:
          myActionId = executor.getActionName();
          break;
        default:
          myActionId = null;
      }
    }

    @Override
    public void navigate(@NotNull Project project) {
      if (myActionId == null) {
        return;
      }

      ActionManager manager = ActionManager.getInstance();
      AnAction action = manager.getAction(myActionId);
      if (action == null) {
        return;
      }

      if (action instanceof BaseAction) {
        BaseAction.DisableMessage message = BaseAction.getDisableMessage(project);
        if (message != null) {
          myPrinter.stderr(
            String.format("%s is disabled because %s.", action.getTemplatePresentation().getText(), message.getDescription()));
          return;
        }
      }

      manager.tryToExecute(action,
                           ActionCommand.getInputEvent(myActionId),
                           null,
                           ActionPlaces.UNKNOWN,
                           true);
    }
  }

  @NotNull
  @Override
  public Collection<ApkInfo> getApkInfos() {
    return myPackages.entrySet().stream().map((eachPackage) ->
                                                ContainerUtil.map(eachPackage.getValue(), file -> new ApkInfo(file, eachPackage.getKey())))
      .flatMap(List::stream).collect(ImmutableList.toImmutableList());
  }
}
