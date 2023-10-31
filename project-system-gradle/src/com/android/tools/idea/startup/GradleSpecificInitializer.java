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
package com.android.tools.idea.startup;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.projectsystem.gradle.IdeGooglePlaySdkIndex;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.lint.checks.GradleDetector;
import com.intellij.ide.ApplicationInitializedListenerJavaShim;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.plugins.gradle.service.project.CommonGradleProjectResolverExtension;

/**
 * Performs Gradle-specific IDE initialization
 */
public final class GradleSpecificInitializer extends ApplicationInitializedListenerJavaShim {

  // Note: this code runs quite early during Android Studio startup and directly affects app startup performance.
  // Any heavy work should be moved to a background thread and/or moved to a later phase.
  @Override
  public void componentsInitialized() {
    checkInstallPath();

    // Recreate JDKs since they can be invalid when changing Java versions (b/185562147)
    IdeInfo ideInfo = IdeInfo.getInstance();
    if (ConfigImportHelper.isConfigImported() && (ideInfo.isAndroidStudio() || ideInfo.isGameTools())) {
      ApplicationManager.getApplication().invokeLaterOnWriteThread(IdeSdks.getInstance()::recreateProjectJdkTable);
    }

    useIdeGooglePlaySdkIndexInGradleDetector();

    //Switch on Idea native navigation/suggestion for version catalog/gradle
    Registry.get(CommonGradleProjectResolverExtension.GRADLE_VERSION_CATALOGS_DYNAMIC_SUPPORT)
      .setValue(true);
  }

  /**
   * Gradle has an issue when the studio path contains ! (http://b.android.com/184588)
   */
  private static void checkInstallPath() {
    if (PathManager.getHomePath().contains("!")) {
      String message = String.format(
        "%1$s must not be installed in a path containing '!' or Gradle sync will fail!",
        ApplicationNamesInfo.getInstance().getProductName());
      Notification notification = getNotificationGroup().createNotification(message, NotificationType.ERROR);
      notification.setImportant(true);
      Notifications.Bus.notify(notification);
    }
  }

  private static NotificationGroup getNotificationGroup() {
    // Use the system health settings by default
    NotificationGroup group = NotificationGroup.findRegisteredGroup("System Health");
    if (group == null) {
      // This shouldn't happen
      group = new NotificationGroup(
        "Gradle Initializer", NotificationDisplayType.STICKY_BALLOON, true, null, null, null, PluginId.getId("org.jetbrains.android"));
    }
    return group;
  }

  private void useIdeGooglePlaySdkIndexInGradleDetector() {
    GradleDetector.setPlaySdkIndexFactory((path, client) -> {
      IdeGooglePlaySdkIndex playIndex = IdeGooglePlaySdkIndex.INSTANCE;
      playIndex.initializeAndSetFlags();
      return playIndex;
    });
  }
}
