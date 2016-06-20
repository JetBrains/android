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

import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.idea.run.ApkInstaller;
import com.android.tools.idea.run.tasks.*;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.fd.client.InstantRunArtifactType.*;

/**
 * {@link InstantRunBuildAnalyzer} analyzes the result of a gradle instant run build, and provides the list of deploy tasks
 * to update the state of the app on the device.
 */
public class InstantRunBuildAnalyzer {
  private final Project myProject;
  private final InstantRunContext myContext;
  private final ProcessHandler myCurrentSession;
  private final InstantRunBuildInfo myBuildInfo;

  public InstantRunBuildAnalyzer(@NotNull Project project, @NotNull InstantRunContext context, @Nullable ProcessHandler currentSession) {
    myProject = project;
    myContext = context;
    myCurrentSession = currentSession;

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
    return buildSelection.mode == BuildMode.HOT && (myBuildInfo.hasNoChanges() || myBuildInfo.canHotswap());
  }

  /**
   * Returns the list of deploy tasks that will update the instant run state on the device.
   * @param apkDeployTask Currently, the full apk deploy task needs a lot of information about the build system, so it has
   *                      to be passed in as an argument in case the buid results indicate that the apk has to be redeployed.
   *                      TODO: this argument will be removed once {@link DeployApkTask} and {@link ApkInstaller} are cleaned up.
   */
  @NotNull
  public List<LaunchTask> getDeployTasks(@Nullable LaunchTask apkDeployTask) {
    LaunchTask updateStateTask = new UpdateInstantRunStateTask(myContext);

    DeployType deployType = getDeployType();
    switch (deployType) {
      case NO_CHANGES:
        return ImmutableList.of(new NoChangesTask(myProject, myContext), updateStateTask);
      case HOTSWAP:
      case WARMSWAP:
        return ImmutableList
          .of(new HotSwapTask(myProject, myContext, deployType == DeployType.WARMSWAP), updateStateTask);
      case SPLITAPK:
        return ImmutableList.of(new SplitApkDeployTask(myProject, myContext), updateStateTask);
      case DEX:
        return ImmutableList.of(new DexDeployTask(myProject, myContext), updateStateTask);
      case LEGACY:
      case FULLAPK:
        Preconditions.checkNotNull(apkDeployTask); // apkDeployTask can be null only under NO_CHANGES or HOTSWAP scenarios
        return ImmutableList.of(apkDeployTask, updateStateTask);
      default:
        throw new IllegalStateException("Unhandled deploy type: " + deployType);
    }
  }

  @NotNull
  public LaunchTask getNotificationTask() {
    DeployType deployType = getDeployType();
    InstantRunNotificationProvider notificationProvider =
      new InstantRunNotificationProvider(myContext.getBuildSelection(), deployType, myBuildInfo.getVerifierStatus());
    return new InstantRunNotificationTask(myProject, myContext, notificationProvider);
  }

  @NotNull
  private DeployType getDeployType() {
    if (canReuseProcessHandler()) { // is this needed? do we make sure we do a cold swap when there is no process handler?
      if (myBuildInfo.hasNoChanges()) {
        return DeployType.NO_CHANGES;
      }
      else if (myBuildInfo.canHotswap()) {
        return InstantRunSettings.isRestartActivity() ? DeployType.WARMSWAP : DeployType.HOTSWAP;
      }
    }

    List<InstantRunArtifact> artifacts = myBuildInfo.getArtifacts();
    if (artifacts.isEmpty()) {
      return DeployType.NO_CHANGES;
    }

    if (myBuildInfo.hasOneOf(SPLIT) || myBuildInfo.hasOneOf(SPLIT_MAIN)) {
      return DeployType.SPLITAPK;
    }

    if (myBuildInfo.hasOneOf(DEX, RESOURCES)) {
      return DeployType.DEX;
    }

    return DeployType.FULLAPK;
  }
}
