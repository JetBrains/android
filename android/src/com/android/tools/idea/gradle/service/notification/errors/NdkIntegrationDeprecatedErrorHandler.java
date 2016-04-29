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
package com.android.tools.idea.gradle.service.notification.errors;

import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenUrlHyperlink;
import com.android.tools.idea.gradle.util.GradleProperties;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

public class NdkIntegrationDeprecatedErrorHandler extends AbstractSyncErrorHandler {
  private static final String NDK_INTEGRATION_DEPRECATED = "NDK integration is deprecated in the current plugin.";

  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String firstLine = message.get(0);
    if (firstLine.contains(NDK_INTEGRATION_DEPRECATED)) {
      List<NotificationHyperlink> hyperlinks = Lists.newArrayList();
      hyperlinks.add(new OpenUrlHyperlink("http://tools.android.com/tech-docs/new-build-system/gradle-experimental",
                                          "Consider trying the new experimental plugin"));
      hyperlinks.add(new SetUseDeprecatedNdkHyperlink());
      updateNotification(notification, project, NDK_INTEGRATION_DEPRECATED, hyperlinks);
      return true;
    }
    return false;
  }

  private static class SetUseDeprecatedNdkHyperlink extends NotificationHyperlink {
    public SetUseDeprecatedNdkHyperlink() {
      super("useDeprecatedNdk",
            "Set \"android.useDeprecatedNdk=true\" in gradle.properties to continue using the current NDK integration");
    }

    @Override
    protected void execute(@NotNull Project project) {
      GradleProperties gradleProperties;
      try {
        gradleProperties = new GradleProperties(project);
      } catch (IOException e) {
        Messages.showErrorDialog(project, "Failed to read gradle.properties: " + e.getMessage(), "Quick Fix");
        return;
      }

      gradleProperties.getProperties().setProperty("android.useDeprecatedNdk", "true");

      try {
        gradleProperties.save();
      } catch (IOException e) {
        Messages.showErrorDialog(project, "Failed to update gradle.properties: " + e.getMessage(), "Quick Fix");
        return;
      }

      GradleProjectImporter.getInstance().requestProjectSync(project, null);
    }
  }
}
