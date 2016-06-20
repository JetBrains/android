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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class InstantRunNotificationProvider {
  private static final Set<BuildCause> ourCausesThatDontNeedNotifications = ImmutableSet.of(
    BuildCause.FIRST_INSTALLATION_TO_DEVICE,
    BuildCause.APP_NOT_INSTALLED
  );

  private static final Map<BuildCause, String> ourNotificationsByCause = new ImmutableMap.Builder<BuildCause, String>()
    .put(BuildCause.USER_REQUESTED_CLEAN_BUILD, AndroidBundle.message("instant.run.notification.cleanbuild.on.user.request"))
    .put(BuildCause.MISMATCHING_TIMESTAMPS, AndroidBundle.message("instant.run.notification.cleanbuild.mismatching.timestamps"))
    .put(BuildCause.API_TOO_LOW_FOR_INSTANT_RUN, AndroidBundle.message("instant.run.notification.fullbuild.api.less.than.15"))
    .put(BuildCause.MANIFEST_RESOURCE_CHANGED, AndroidBundle.message("instant.run.notification.fullbuild.manifestresourcechanged"))
    .put(BuildCause.FREEZE_SWAP_REQUIRES_API21, AndroidBundle.message("instant.run.notification.fullbuild.api.less.than.21"))
    .put(BuildCause.FREEZE_SWAP_REQUIRES_WORKING_RUN_AS, AndroidBundle.message("instant.run.notification.fullbuild.broken.runas"))
    .put(BuildCause.APP_USES_MULTIPLE_PROCESSES, AndroidBundle.message("instant.run.notification.coldswap.multiprocess"))
    .build();

  private final BuildSelection myBuildSelection;
  private final DeployType myDeployType;
  private final String myVerifierStatus;

  public InstantRunNotificationProvider(@NotNull BuildSelection buildSelection,
                                        @NotNull DeployType deployType,
                                        @NotNull String verifierStatus) {
    myBuildSelection = buildSelection;
    myDeployType = deployType;
    myVerifierStatus = verifierStatus;
  }

  @Nullable
  public String getNotificationText() {
    BuildMode buildMode = myBuildSelection.mode;
    BuildCause buildCause = myBuildSelection.why;

    if (ourCausesThatDontNeedNotifications.contains(buildCause)) {
      return null;
    }

    if (ourNotificationsByCause.containsKey(buildCause)) {
      return ourNotificationsByCause.get(buildCause);
    }

    if (buildCause == BuildCause.APP_NOT_RUNNING) {
      //noinspection ConditionalExpressionWithIdenticalBranches don't consider AndroidBundle.message as a method common to both branches
      return myDeployType == DeployType.NO_CHANGES ?
             AndroidBundle.message("instant.run.notification.coldswap.nochanges") :
             AndroidBundle.message("instant.run.notification.coldswap");
    }

    switch (myDeployType) {
      case NO_CHANGES:
        return AndroidBundle.message("instant.run.notification.nochanges");
      case HOTSWAP:
        return AndroidBundle.message("instant.run.notification.hotswap", getRestartActivityShortcutText());
      case WARMSWAP:
        return AndroidBundle.message("instant.run.notification.warmswap");
      case SPLITAPK:
      case DEX: {
        StringBuilder sb = new StringBuilder("Instant Run applied code changes and restarted the app.");
        if (buildMode == BuildMode.HOT) {
          // we requested a hot swap build, but we got cold swap artifacts
          if (!myVerifierStatus.isEmpty()) {
            sb.append(' ');
            // Convert tokens like "FIELD_REMOVED" to "Field Removed" for better readability
            sb.append(StringUtil.capitalizeWords(myVerifierStatus.toLowerCase(Locale.US).replace('_', ' '), true));
            sb.append('.');
          }
        }
        else if (buildMode == BuildMode.COLD) {
          // we requested a cold swap build, so mention why we requested such a build
          sb.append(' ').append(buildCause).append('.');
        }
        return sb.toString();
      }
      case FULLAPK:
        return "Instant Run re-installed and restarted the app";
      default:
        return null;
    }
  }

  @NotNull
  private static String getRestartActivityShortcutText() {
    if (ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isUnitTestMode()) {
      return "";
    }

    Shortcut[] shortcuts = ActionManager.getInstance().getAction("Android.RestartActivity").getShortcutSet().getShortcuts();
    return shortcuts.length > 0 ? " (" + KeymapUtil.getShortcutText(shortcuts[0]) + ") " : "";
  }
}
