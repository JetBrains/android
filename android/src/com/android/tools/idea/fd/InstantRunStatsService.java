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

import com.android.ddmlib.IDevice;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.stats.AndroidStudioUsageTracker;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.AndroidStudioEvent.EventCategory;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.AndroidStudioEvent.EventKind;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.InstantRun;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.InstantRun.InstantRunDeploymentKind;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.InstantRun.InstantRunIdeBuildCause;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class InstantRunStatsService {
  private final Object LOCK = new Object();

  /**
   * Current session id: A session starts from installing an APK, continues through multiple hot/cold swaps until the next full apk install
   */
  @NotNull
  private UUID mySessionId = UUID.randomUUID();

  /**
   * Time (in ms) at which the build was started. The build & deploy is considered done when {@link #notifyDeployType(DeployType)} is
   * invoked. It is possible for the deploy type notification to never show up (e.g. build failures).
   *
   * NOTE: the combination of a single variable for build start time, and this service being scoped at a project level could cause
   * conflicts if multiple launches from multiple modules are performed in parallel
   */
  private long myBuildStartTime;


  public static InstantRunStatsService get(@NotNull Project project) {
    return ServiceManager.getService(project, InstantRunStatsService.class);
  }

  private InstantRunStatsService() {
  }

  public void notifyBuildStarted() {
    synchronized (LOCK) {
      myBuildStartTime = System.currentTimeMillis();
    }
  }

  public void notifyDeployStarted() {
  }

  public void notifyDeployType(@NotNull DeployType type, @NotNull BuildCause buildCause, @NotNull IDevice device) {
    long buildAndDeployTime;
    String sessionId;

    synchronized (LOCK) {
      buildAndDeployTime = System.currentTimeMillis() - myBuildStartTime;

      if (type == DeployType.FULLAPK || type == DeployType.LEGACY || type == DeployType.SPLITAPK) {
        // We want to assign a session id for all launches in order to compute the number of hot/coldswaps between each APK push
        // Installing an APK starts a new session.
        resetSession();
      }

      sessionId = mySessionId.toString();
    }

    AndroidStudioEvent.Builder studioEvent = AndroidStudioEvent.newBuilder()
      .setCategory(EventCategory.STUDIO_BUILD)
      .setKind(EventKind.INSTANT_RUN)
      .setInstantRun(InstantRun.newBuilder()
                       .setSessionId(sessionId)
                       .setBuildTime(buildAndDeployTime)
                       .setDeploymentKind(deployTypeToDeploymentKind(type))
                       .setIdeBuildCause(buildCauseToProto(buildCause)));
    if (buildCause == BuildCause.API_TOO_LOW_FOR_INSTANT_RUN || buildCause == BuildCause.FREEZE_SWAP_REQUIRES_API21
      || buildCause == BuildCause.FREEZE_SWAP_REQUIRES_WORKING_RUN_AS) {
      studioEvent.setDeviceInfo(AndroidStudioUsageTracker.deviceToDeviceInfo(device));
    }
    UsageTracker.getInstance().log(studioEvent);
  }

  private static InstantRunIdeBuildCause buildCauseToProto(@Nullable BuildCause buildCause) {
    if (buildCause == null) {
      return InstantRunIdeBuildCause.UNKNOWN_INSTANT_RUN_IDE_BUILD_CAUSE;
    }
    try {
      return InstantRunIdeBuildCause.valueOf(buildCause.toString());
    } catch (IllegalArgumentException e) {
      return InstantRunIdeBuildCause.UNKNOWN_INSTANT_RUN_IDE_BUILD_CAUSE;
    }
  }

  private static InstantRunDeploymentKind deployTypeToDeploymentKind(@NotNull DeployType type) {
    switch (type) {
      case LEGACY:
        return InstantRunDeploymentKind.LEGACY;
      case FULLAPK:
        return InstantRunDeploymentKind.FULL_APK;
      case HOTSWAP:
        return InstantRunDeploymentKind.HOT_SWAP;
      case SPLITAPK:
        return InstantRunDeploymentKind.SPLIT_APK;
      case DEX:
        return InstantRunDeploymentKind.DEX;
      case WARMSWAP:
        return InstantRunDeploymentKind.WARM_SWAP;
      case NO_CHANGES:
        return InstantRunDeploymentKind.NO_CHANGES;
      default:
        return InstantRunDeploymentKind.UNKNOWN_INSTANT_RUN_DEPLOYMENT_KIND;
    }
  }

  private void resetSession() {
    synchronized (LOCK) {
      mySessionId = UUID.randomUUID();
    }
  }
}
