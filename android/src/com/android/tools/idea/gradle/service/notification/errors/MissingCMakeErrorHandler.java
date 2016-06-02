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
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.createDialogForPaths;

public class MissingCMakeErrorHandler extends AbstractSyncErrorHandler {

  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String firstLine = message.get(0);
    if (firstLine.startsWith("Failed to find CMake.")) {
      List<NotificationHyperlink> hyperlinks = Lists.newArrayList();
      NotificationHyperlink installCMakeLink = getInstallCMakeNotificationHyperlink();
      hyperlinks.add(installCMakeLink);
      updateNotification(notification, project, "Failed to find CMake.", hyperlinks);
      return true;
    }
    return false;
  }

  private static NotificationHyperlink getInstallCMakeNotificationHyperlink() {
    return new NotificationHyperlink("install.cmake", "Install CMake and sync project") {
      @Override
      protected void execute(@NotNull Project project) {
        ModelWizardDialog dialog = createDialogForPaths(project, ImmutableList.of("cmake"));
        if (dialog != null && dialog.showAndGet()) {
          GradleProjectImporter.getInstance().requestProjectSync(project, null);
        }
      }
    };
  }
}