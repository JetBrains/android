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

import com.android.tools.idea.gradle.service.notification.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class UnsupportedModelVersionErrorHandler extends AbstractSyncErrorHandler {
  /**
   * These String constants are being used in {@link com.android.tools.idea.gradle.service.notification.GradleNotificationExtension} to add
   * "quick-fix"/"help" hyperlinks to error messages. Given that the contract between the consumer and producer of error messages is pretty
   * loose, please do not use these constants, to prevent any unexpected side effects during project sync.
   */
  @NotNull public static final String UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX =
    "The project is using an unsupported version of the Android Gradle plug-in";
  @NotNull public static final String READ_MIGRATION_GUIDE_MSG = "Please read the migration guide";

  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String msg = error.getMessage();
    if (msg.startsWith(UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX)) {
      boolean openMigrationGuide = msg.contains(READ_MIGRATION_GUIDE_MSG);
      updateNotification(notification, project, msg, new FixAndroidGradlePluginVersionHyperlink(openMigrationGuide));
      return true;
    }
    return false;
  }
}
