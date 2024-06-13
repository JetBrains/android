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
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.notification.NotificationsConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.PluginId;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.execution.GradleTaskExecutionMeasuringExtension;
import org.jetbrains.plugins.gradle.service.project.GradleOperationHelperExtension;

/**
 * Performs Gradle-specific IDE initialization
 */
public class GradleSpecificInitializer implements AppLifecycleListener {

  // Note: this code runs quite early during Android Studio startup and directly affects app startup performance.
  // Any heavy work should be moved to a background thread and/or moved to a later phase.
  @Override
  public void appFrameCreated(@NotNull List<String> arguments) {
    checkInstallPath();

    if (ConfigImportHelper.isConfigImported()) {
      cleanProjectJdkTableForNewIdeVersion();
      migrateAgpUpgradeAssistantSettingForNewIdeVersion();
    }
    // Disable the extension because it causes performance issues, see http://b/298372819.
    //noinspection UnstableApiUsage
    GradleOperationHelperExtension.EP_NAME.getPoint().unregisterExtension(GradleTaskExecutionMeasuringExtension.class);

    useIdeGooglePlaySdkIndexInGradleDetector();
  }


  /**
   * The definition of <tt>jar:</tt> scheme URLs uses the sequence <tt>!/</tt> as a separator between the inner URL pointing to a jar
   * file, and the entry within that jar file.  Unfortunately, that <tt>!/</tt> sequence is legal both in the inner URL, where it would
   * indicate a directory ending with an exclamation mark, and in the entry, where it would refer to a component of the jar likewise in a
   * directory ending with an exclamation mark.  Doubly unfortunately, no escaping for such sequences was mandated, so it is impossible to
   * recover intent from an arbitrary <tt>jar:</tt> url.  So running anything Java-related in any path containing a directory ending in
   * an exclamation mark will lead to resource errors, e.g. JDK-4523159 (reported in 2001); see {@link java.net.JarURLConnection} for the
   * <i>de facto</i> interpretation (the first <tt>!/</tt> is treated as the separator).
   * <p>
   * As if this were not enough, Gradle itself (in its {@code ClasspathUtil}) does its own parsing of <tt>jar:</tt> scheme URLs, and it
   * treats (at least as of Gradle 8.0) a bare <tt>!</tt> as a semantically-meaningful separator.  This increases the range of unsafe
   * directories to those which contain any exclamation marks at any position, not just at the end of any directory component.  Ironically
   * this does make it easier to identify when we should warn the user about their choice of installation directory.
   * <p>
   * Given all this, we will probably never be able to do better than alert the user that they have had the temerity to install Android
   * Studio into a directory whose path contains an exclamation mark.
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

  private static void cleanProjectJdkTableForNewIdeVersion() {
    IdeInfo ideInfo = IdeInfo.getInstance();
    if (ideInfo.isAndroidStudio() || ideInfo.isGameTools()) {
      // In older versions of Android Studio, we cleaned and recreated the Project Jdk Table here because
      // otherwise a change in the bundled version of the JDK (among other possibilities) would lead to
      // red symbols in Gradle build files (b/185562147).
      //
      // We now check the project Jdk used for Gradle during Gradle sync, fixing it if it does not exist, and
      // also recreate the table in the UI for the Gradle JVM drop-down, so this cleanup step at application
      // initialization is somewhat less necessary.  There are other JDK drop-downs in the system, though (for
      // example, choosing a JVM for unit-test Run Configurations) so a clean project Jdk table is better than
      // not.
      ApplicationManager.getApplication().invokeLater(IdeSdks.getInstance()::recreateProjectJdkTable);
    }
  }

  private static void migrateAgpUpgradeAssistantSettingForNewIdeVersion() {
    // If the user previously set the application-wide custom setting to not see AGP Upgrade Assistant notifications, migrate that
    // preference to the new, more standard, setting in the NotificationsConfiguration.
    PropertiesComponent properties = PropertiesComponent.getInstance();
    String propertyKey = "recommended.upgrade.do.not.show.again";
    if (properties.isValueSet(propertyKey)) {
      if (properties.getBoolean(propertyKey, false)) {
        ApplicationManager.getApplication().invokeLater(() -> {
          String groupId = "Android Gradle Upgrade Notification";
          NotificationsConfiguration.getNotificationsConfiguration()
            .changeSettings(groupId, NotificationDisplayType.NONE, /* do not log */ false, /* silence */ false);
        });
      }
      properties.unsetValue(propertyKey);
    }
  }

  private static void useIdeGooglePlaySdkIndexInGradleDetector() {
    GradleDetector.setPlaySdkIndexFactory((path, client) -> {
      IdeGooglePlaySdkIndex playIndex = IdeGooglePlaySdkIndex.INSTANCE;
      playIndex.initializeAndSetFlags();
      return playIndex;
    });
  }
}
