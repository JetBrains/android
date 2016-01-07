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
package com.android.tools.idea.gradle.service.notification.hyperlink;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.intellij.openapi.externalSystem.service.notification.EditableNotificationMessageElement;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import javax.swing.event.HyperlinkEvent;
import java.io.File;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.tools.idea.gradle.util.GradleUtil.*;

/**
 * Fixes the Gradle version in a project's Gradle wrapper.
 */
public class FixGradleVersionInWrapperHyperlink extends NotificationHyperlink {
  @NotNull private final File myWrapperPropertiesFile;
  @NotNull private final String myGradleVersion;

  /**
   * Creates a new {@link FixGradleVersionInWrapperHyperlink} if the given project is using the Gradle wrapper.
   *
   * @param project the given project.
   * @param gradleVersion the version of Gradle to set. If {@code null}, this method will use {@link SdkConstants#GRADLE_LATEST_VERSION}.
   * @return the created hyperlink, or {@code null} if the project is not using the Gradle wrapper.
   */
  @Nullable
  public static NotificationHyperlink createIfProjectUsesGradleWrapper(@NotNull Project project, @Nullable String gradleVersion) {
    File wrapperPropertiesFile = findWrapperPropertiesFile(project);
    if (wrapperPropertiesFile != null) {
      String version = gradleVersion != null ? gradleVersion : GRADLE_LATEST_VERSION;
      return new FixGradleVersionInWrapperHyperlink(wrapperPropertiesFile, version);
    }
    return null;
  }

  private FixGradleVersionInWrapperHyperlink(@NotNull File wrapperPropertiesFile, @NotNull String gradleVersion) {
    super("fixGradleVersionInWrapper", "Fix Gradle wrapper and re-import project");
    myWrapperPropertiesFile = wrapperPropertiesFile;
    myGradleVersion = gradleVersion;
  }

  @Override
  protected void execute(@NotNull Project project) {
    updateGradleDistributionUrl(project, myWrapperPropertiesFile, myGradleVersion);
    setDistributionTypeAndSync(project);
  }

  @Override
  public boolean executeIfClicked(@NotNull Project project, @NotNull HyperlinkEvent event) {
    // we need HyperlinkEvent for the link deactivation after the fix apply
    boolean updated = updateGradleDistributionUrl(project, myWrapperPropertiesFile, myGradleVersion);
    if (updated) {
      EditableNotificationMessageElement.disableLink(event);
      setDistributionTypeAndSync(project);
    }
    return updated;
  }

  private static void setDistributionTypeAndSync(@NotNull Project project) {
    GradleProjectSettings settings = getGradleProjectSettings(project);
    if (settings != null) {
      settings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
    }
    GradleProjectImporter.getInstance().requestProjectSync(project, null);
  }
}
