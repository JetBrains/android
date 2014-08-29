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

import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.StopGradleDaemonsAndSyncHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.SyncProjectWithExtraCommandLineOptionsHyperlink;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ClassLoadingErrorHandler extends AbstractSyncErrorHandler {
  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull final Project project) {
    String firstLine = message.get(0);
    if (firstLine.startsWith("Unable to load class") ||
        firstLine.startsWith("Unable to find method") ||
        firstLine.contains("cannot be cast to")) {
      NotificationHyperlink syncProjectHyperlink = SyncProjectWithExtraCommandLineOptionsHyperlink.syncProjectRefreshingDependencies();
      NotificationHyperlink stopDaemonsHyperlink = new StopGradleDaemonsAndSyncHyperlink();
      String newMsg = firstLine + "\nPossible causes for this unexpected error include:<ul>" +
                      "<li>Gradle's dependency cache may be corrupt (this sometimes occurs after a network connection timeout.)\n" +
                      syncProjectHyperlink.toString() + "</li>" +
                      "<li>The state of a Gradle build process may be corrupt.\n" +
                      stopDaemonsHyperlink.toString() + "</li></ul>" +
                      "In the case of corrupt Gradle processes, you can also try closing the IDE and then killing all Java processes.";

      String title = String.format(FAILED_TO_SYNC_GRADLE_PROJECT_ERROR_GROUP_FORMAT, project.getName());
      notification.setTitle(title);
      notification.setMessage(newMsg);
      notification.setNotificationCategory(NotificationCategory.convert(DEFAULT_NOTIFICATION_TYPE));
      addNotificationListener(notification, project, syncProjectHyperlink, stopDaemonsHyperlink);
      return true;
    }

    return false;
  }
}
