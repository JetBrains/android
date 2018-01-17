/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.fd;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.ir.client.InstantRunArtifact;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.android.tools.idea.diagnostics.crash.CrashReporter;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.tasks.*;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.tools.ir.client.InstantRunArtifactType.*;

/**
 * {@link InstantRunBuildAnalyzer} analyzes the result of a gradle instant run build, and provides the list of deploy tasks
 * to update the state of the app on the device.
 */
public class InstantRunBuildAnalyzer {
  private final Project myProject;
  private final InstantRunContext myContext;
  private final ProcessHandler myCurrentSession;
  private final InstantRunBuildInfo myBuildInfo;
  private final boolean myIsRestartActivity;
  private final Collection<ApkInfo> myApks;

  public InstantRunBuildAnalyzer(@NotNull Project project,
                                 @NotNull InstantRunContext context,
                                 @Nullable ProcessHandler currentSession,
                                 boolean isRestartActivity) {
    this(project, context, currentSession, Collections.EMPTY_LIST, isRestartActivity);
  }

  public InstantRunBuildAnalyzer(@NotNull Project project,
                                 @NotNull InstantRunContext context,
                                 @Nullable ProcessHandler currentSession,
                                 @NotNull Collection<ApkInfo> apks,
                                 boolean isRestartActivity) {
    myProject = project;
    myContext = context;
    myCurrentSession = currentSession;
    myApks = apks;
    myIsRestartActivity = isRestartActivity;

    myBuildInfo = myContext.getInstantRunBuildInfo();
    if (myBuildInfo == null) {
      throw new IllegalArgumentException("Instant Run Build Information must be available post build");
    }

    if (!myBuildInfo.isCompatibleFormat()) {
      throw new IllegalStateException("This version of Android Studio is incompatible with the Gradle Plugin used. " +
                                      "Try disabling Instant Run (or updating either the IDE or the Gradle plugin to " +
                                      "the latest version)");
    }
  }

  /**
   * Returns whether the existing process handler (corresponding to the run session) can be reused based on this build's artifacts.
   * For instance, we can reuse the existing session if the current session is active, and the results indicate that the changes can be
   * hot swapped.
   */
  public boolean canReuseProcessHandler() {
    if (myCurrentSession == null || myCurrentSession.isProcessTerminated()) {
      return false;
    }

    BuildSelection buildSelection = myContext.getBuildSelection();
    assert buildSelection != null : "Build must have completed before results are analyzed";
    return buildSelection.getBuildMode() == BuildMode.HOT && myBuildInfo.getBuildMode().equals("HOT_WARM");
  }

  /**
   * Returns the list of deploy tasks that will update the instant run state on the device.
   */
  @NotNull
  public List<LaunchTask> getDeployTasks(@NotNull final IDevice device, @NotNull LaunchOptions launchOptions) {
    LaunchTask updateStateTask = new UpdateInstantRunStateTask(myContext);

    DeployType deployType = getDeployType();
    List<LaunchTask> tasks = new ArrayList<>();
    if (StudioFlags.UNINSTALL_LAUNCHER_APPS_ENABLED.get() &&
        device.supportsFeature(IDevice.HardwareFeature.EMBEDDED) &&
        (deployType == DeployType.SPLITAPK || deployType == DeployType.FULLAPK)) {
      tasks.add(new UninstallIotLauncherAppsTask(myProject, myContext.getApplicationId()));
    }
    switch (deployType) {
      case NO_CHANGES:
        return ImmutableList.of(new NoChangesTask(myProject, myContext), updateStateTask);
      case RESTART:
        // Kill the app, it'll be started as a part of AndroidLaunchTasksProvider
        return ImmutableList.of(new KillTask(myProject, myContext), updateStateTask);
      case HOTSWAP:
      case WARMSWAP:
        ImmutableList.Builder<LaunchTask> taskBuilder = new ImmutableList.Builder<>();
        // Deploy resources APK(s) for O and above
        if (myBuildInfo.hasOneOf(SPLIT)) {
          taskBuilder.add(new SplitApkDeployTask(myProject, myContext, true));
          taskBuilder.add(new UpdateAppInfoTask(myProject, myContext));
        }
        // Deploy Code and Resources changes below O
        if (myBuildInfo.hasOneOf(RELOAD_DEX) || myBuildInfo.hasHotSwapResources()) {
          taskBuilder.add(new HotSwapTask(myProject, myContext, deployType == DeployType.WARMSWAP));
        }
        return taskBuilder.add(updateStateTask).build();
      case SPLITAPK:
        tasks.add(new SplitApkDeployTask(myProject, myContext));
        tasks.add(updateStateTask);
        return ImmutableList.copyOf(tasks);
      case FULLAPK:
        tasks.add(new DeployApkTask(myProject, launchOptions, myApks, myContext));
        return ImmutableList.copyOf(tasks);
      default:
        // https://code.google.com/p/android/issues/detail?id=232515
        // We don't know as yet how this happened, so we collect some information
        if (StatisticsUploadAssistant.isSendAllowed()) {
          CrashReporter.getInstance().submit(getIrDebugSignals(deployType));
        }
        throw new IllegalStateException(AndroidBundle.message("instant.run.build.error"));
    }
  }

  @NotNull
  private Map<String, String> getIrDebugSignals(@NotNull DeployType deployType) {
    Map<String, String> m = new HashMap<>();

    m.put("deployType", deployType.toString());
    m.put("canReuseProcessHandler", Boolean.toString(canReuseProcessHandler()));
    m.put("androidGradlePluginVersion", myContext.getGradlePluginVersion().toString());

    BuildSelection selection = myContext.getBuildSelection();
    if (selection != null) {
      m.put("buildSelection.mode", selection.getBuildMode().toString());
      m.put("buildSelection.why", selection.why.toString());
    }

    InstantRunBuildInfo buildInfo = myContext.getInstantRunBuildInfo();
    if (buildInfo != null) {
      m.put("buildinfo.buildMode", buildInfo.getBuildMode());
      m.put("buildinfo.verifierStatus", buildInfo.getVerifierStatus());
      m.put("buildinfo.format", Integer.toString(buildInfo.getFormat()));

      List<InstantRunArtifact> artifacts = buildInfo.getArtifacts();
      m.put("buildinfo.nArtifacts", Integer.toString(artifacts.size()));
      for (int i = 0; i < artifacts.size(); i++) {
        InstantRunArtifact artifact = artifacts.get(i);
        String prefix = "buildInfo.artifact[" + i + "]";

        m.put(prefix + ".type", artifact.type.toString());
        m.put(prefix + ".file", artifact.file.getName());
      }
    }

    return m;
  }

  @NotNull
  public LaunchTask getNotificationTask() {
    DeployType deployType = getDeployType();
    BuildSelection buildSelection = myContext.getBuildSelection();

    InstantRunNotificationProvider notificationProvider =
      new InstantRunNotificationProvider(buildSelection, deployType, myBuildInfo.getVerifierStatus());

    return new InstantRunNotificationTask(myProject, myContext, notificationProvider, buildSelection);
  }

  @VisibleForTesting
  @NotNull
  DeployType getDeployType() {
    if (canReuseProcessHandler()) { // is this needed? do we make sure we do a cold swap when there is no process handler?
      if (myBuildInfo.hasNoChanges()) {
        return DeployType.NO_CHANGES;
      }
      else if (myBuildInfo.canHotswap()) {
        return myIsRestartActivity ? DeployType.WARMSWAP : DeployType.HOTSWAP;
      }
    }

    List<InstantRunArtifact> artifacts = myBuildInfo.getArtifacts();
    if (artifacts.isEmpty()) {
      if (myBuildInfo.getVerifierStatus().equals(DeployType.NO_CHANGES.toString())) {
        return DeployType.NO_CHANGES;
      }
      else {
        // If somehow gradle tells us a change is not hotswapable but returns no artifacts, restart the app.
        return DeployType.RESTART;
      }
    }

    if (myBuildInfo.hasOneOf(SPLIT) || myBuildInfo.hasOneOf(SPLIT_MAIN)) {
      return DeployType.SPLITAPK;
    }

    return DeployType.FULLAPK;
  }
}
