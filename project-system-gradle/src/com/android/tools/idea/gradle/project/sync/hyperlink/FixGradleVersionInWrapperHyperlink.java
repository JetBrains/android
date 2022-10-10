/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_WRAPPER_GRADLE_VERSION_FIXED;

import com.android.tools.idea.gradle.project.sync.issues.SyncIssueNotificationHyperlink;
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.openapi.externalSystem.service.notification.EditableNotificationMessageElement;
import com.intellij.openapi.project.Project;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

/**
 * Fixes the Gradle version in a project's Gradle wrapper.
 */
public class FixGradleVersionInWrapperHyperlink extends SyncIssueNotificationHyperlink {
  @NotNull private final GradleWrapper myGradleWrapper;
  @NotNull private final String myGradleVersion;

  /**
   * Creates a new {@link FixGradleVersionInWrapperHyperlink} if the given project is using the Gradle wrapper.
   *
   * @param project the given project.
   * @param gradleVersion the version of Gradle to set. If {@code null}, this method will use
   * {@link com.android.tools.idea.flags.StudioFlags#AGP_VERSION_TO_USE}.
   * @return the created hyperlink, or {@code null} if the project is not using the Gradle wrapper.
   */
  @Nullable
  public static SyncIssueNotificationHyperlink createIfProjectUsesGradleWrapper(@NotNull Project project, @Nullable String gradleVersion) {
    GradleWrapper gradleWrapper = GradleWrapper.find(project);
    if (gradleWrapper != null) {
      String version = gradleVersion != null ? gradleVersion : GradleWrapper.getGradleVersionToUse();
      return new FixGradleVersionInWrapperHyperlink(gradleWrapper, version);
    }
    return null;
  }

  private FixGradleVersionInWrapperHyperlink(@NotNull GradleWrapper gradleWrapper, @NotNull String gradleVersion) {
    super("fixGradleVersionInWrapper",
          "Fix Gradle wrapper and re-import project",
          AndroidStudioEvent.GradleSyncQuickFix.FIX_GRADLE_VERSION_IN_WRAPPER_HYPERLINK);
    myGradleWrapper = gradleWrapper;
    myGradleVersion = gradleVersion;
  }

  @Override
  protected void execute(@NotNull Project project) {
    myGradleWrapper.updateDistributionUrlAndDisplayFailure(myGradleVersion);
    setDistributionTypeAndSync(project);
  }

  @Override
  public boolean executeIfClicked(@NotNull Project project, @NotNull HyperlinkEvent event) {
    // we need HyperlinkEvent for the link deactivation after the fix apply
    boolean updated = myGradleWrapper.updateDistributionUrlAndDisplayFailure(myGradleVersion);
    if (updated) {
      EditableNotificationMessageElement.disableLink(event);
      setDistributionTypeAndSync(project);
    }
    return updated;
  }

  private static void setDistributionTypeAndSync(@NotNull Project project) {
    GradleProjectSettings settings = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project);
    if (settings != null) {
      settings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
    }
    requestSync(project);
  }

  private static void requestSync(@NotNull Project project) {
    HyperlinkUtil.requestProjectSync(project, TRIGGER_QF_WRAPPER_GRADLE_VERSION_FIXED);
  }

  @VisibleForTesting
  @NotNull
  public String getGradleVersion() {
    return myGradleVersion;
  }
}
