/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification.errors;

import com.android.tools.idea.gradle.service.notification.hyperlink.CreateGradleWrapperHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.FixGradleVersionInWrapperHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenGradleSettingsHyperlink;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.io.File;
import java.util.List;

import static org.jetbrains.plugins.gradle.service.project.AbstractProjectImportErrorHandler.FIX_GRADLE_VERSION;
import static org.jetbrains.plugins.gradle.service.project.AbstractProjectImportErrorHandler.OPEN_GRADLE_SETTINGS;

public class UnsupportedGradleVersionErrorHandler extends AbstractSyncErrorHandler {
  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String lastLine = message.get(message.size() - 1);

    if (OPEN_GRADLE_SETTINGS.equals(lastLine) || lastLine.contains(FIX_GRADLE_VERSION)) {
      List<NotificationHyperlink> hyperlinks = Lists.newArrayList();
      File wrapperPropertiesFile = GradleUtil.findWrapperPropertiesFile(project);
      if (wrapperPropertiesFile != null) {
        // It is very likely that we need to fix the model version as well. Do everything in one shot.
        NotificationHyperlink hyperlink = FixGradleVersionInWrapperHyperlink.createIfProjectUsesGradleWrapper(project);
        if (hyperlink != null) {
          hyperlinks.add(hyperlink);
        }
      }
      else {
        GradleProjectSettings gradleProjectSettings = GradleUtil.getGradleProjectSettings(project);
        if (gradleProjectSettings != null && gradleProjectSettings.getDistributionType() == DistributionType.LOCAL) {
          hyperlinks.add(new CreateGradleWrapperHyperlink());
        }
      }
      hyperlinks.add(new OpenGradleSettingsHyperlink());
      updateNotification(notification, project, error.getMessage(), hyperlinks);
      return true;
    }

    return false;
  }
}
