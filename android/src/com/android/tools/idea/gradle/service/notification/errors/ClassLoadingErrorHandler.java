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
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenProjectStructureHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.StopGradleDaemonsHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.SyncProjectWithExtraCommandLineOptionsHyperlink;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.projectRoots.JavaSdkVersion.JDK_1_7;

public class ClassLoadingErrorHandler extends AbstractSyncErrorHandler {
  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull final Project project) {
    String firstLine = message.get(0);
    boolean classNotFound = firstLine.startsWith("Unable to load class");
    if (classNotFound || firstLine.startsWith("Unable to find method") || firstLine.contains("cannot be cast to")) {
      NotificationHyperlink openJdkSettingsHyperlink = null;
      NotificationHyperlink syncProjectHyperlink = SyncProjectWithExtraCommandLineOptionsHyperlink.syncProjectRefreshingDependencies();
      NotificationHyperlink stopDaemonsHyperlink = StopGradleDaemonsHyperlink.createStopGradleDaemonsHyperlink();

      boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();

      boolean isJdk7 = false;
      String jdkVersion = null;
      if (unitTestMode) {
        isJdk7 = true;
      }
      else if (classNotFound) {
        Sdk jdk = IdeSdks.getJdk();
        if (jdk != null) {
          String jdkHomePath = jdk.getHomePath();
          if (jdkHomePath != null) {
            jdkVersion = JavaSdk.getJdkVersion(jdkHomePath);
          }
          JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
          isJdk7 = version == JDK_1_7;
        }
      }

      String jdk7Hint = "";
      if (isJdk7) {
        jdk7Hint = "<li>";
        if (jdkVersion != null) {
          jdk7Hint += String.format("You are using JDK version '%1$s'. ", jdkVersion);
        }
        jdk7Hint += "Some versions of JDK 1.7 (e.g. 1.7.0_10) may cause class loading errors in Gradle.\n" +
                    "Please update to a newer version (e.g. 1.7.0_67).";

        if (!unitTestMode) {
          openJdkSettingsHyperlink = OpenProjectStructureHyperlink.openJdkSettings(project);
          if (openJdkSettingsHyperlink != null) {
            jdk7Hint = jdk7Hint +"\n" + openJdkSettingsHyperlink.toHtml();
          }
        }

        jdk7Hint += "</li>";
      }

      String newMsg = firstLine + "\nPossible causes for this unexpected error include:<ul>" + jdk7Hint +
                      "<li>Gradle's dependency cache may be corrupt (this sometimes occurs after a network connection timeout.)\n" +
                      syncProjectHyperlink.toHtml() + "</li>" +
                      "<li>The state of a Gradle build process (daemon) may be corrupt. Stopping all Gradle daemons may solve this problem.\n" +
                      stopDaemonsHyperlink.toHtml() + "</li>" +
                      "<li>Your project may be using a third-party plugin which is not compatible with the other plugins in the project " +
                      "or the version of Gradle requested by the project.</li></ul>" +
                      "In the case of corrupt Gradle processes, you can also try closing the IDE and then killing all Java processes.";

      String title = String.format(FAILED_TO_SYNC_GRADLE_PROJECT_ERROR_GROUP_FORMAT, project.getName());
      notification.setTitle(title);
      notification.setMessage(newMsg);
      notification.setNotificationCategory(NotificationCategory.convert(DEFAULT_NOTIFICATION_TYPE));

      if (openJdkSettingsHyperlink != null) {
        addNotificationListener(notification, project, openJdkSettingsHyperlink, syncProjectHyperlink, stopDaemonsHyperlink);
      }
      else {
        addNotificationListener(notification, project, syncProjectHyperlink, stopDaemonsHyperlink);
      }
      return true;
    }

    return false;
  }
}
