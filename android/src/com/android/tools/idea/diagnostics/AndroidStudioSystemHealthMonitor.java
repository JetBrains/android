/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.diagnostics;

import com.android.tools.idea.diagnostics.report.MemoryReportReason;
import com.android.tools.idea.diagnostics.report.UnanalyzedHeapReport;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.BrowseNotificationAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.BaseComponent;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

/**
 * Extension to System Health Monitor that includes Android Studio-specific code.
 */
public class AndroidStudioSystemHealthMonitor implements BaseComponent {
  public static final HProfDatabase ourHProfDatabase = new HProfDatabase(
          Paths.get(PathManager.getTempPath()));

  // FIXME-ank: this is stub class (com.intellij.ide.AndroidStudioSystemHealthMonitorAdapter;)!

  // The group should be registered by SystemHealthMonitor
  private final NotificationGroup myGroup = NotificationGroup.findRegisteredGroup("System Health");

  public static @Nullable
  AndroidStudioSystemHealthMonitor getInstance() {
    return null;
  }

  void showNotification(@PropertyKey(resourceBundle = "messages.AndroidBundle") String key,
          @Nullable NotificationAction action,
          Object... params) {

  }

  static NotificationAction detailsAction(String url) {
    return new BrowseNotificationAction(IdeBundle.message("sys.health.details"), url);
  }

  public void lowMemoryDetected(MemoryReportReason reason) {
  }

  public void addHeapReportToDatabase(@NotNull UnanalyzedHeapReport report) {

  }

  public static void recordGcPauseTime(String gcName, long durationMs) {
  }

  public boolean hasPendingHeapReport() {
    return false;
  }

  class MyNotification extends Notification {
    public MyNotification(@NotNull String content) {
      super(myGroup.getDisplayId(), "", content, NotificationType.WARNING);
    }
  }

  /**
   * Gets an action name based on its class. For Android Studio code, we use simple names for plugins we use canonical names.
   */
  static String getActionName(@NotNull Class actionClass, @NotNull Presentation templatePresentation) {
    Class currentClass = actionClass;
    while (currentClass.isAnonymousClass()) {
      currentClass = currentClass.getEnclosingClass();
    }
    String packageName = currentClass.getPackage().getName();
    if (packageName.startsWith("com.android.") || packageName.startsWith("com.intellij.") || packageName.startsWith("org.jetbrains.") ||
        packageName.startsWith("or.intellij.") || packageName.startsWith("com.jetbrains.") || packageName.startsWith("git4idea.")) {

      String actionName = currentClass.getSimpleName();
      // ExecutorAction points to many different Run/Debug actions, we use the template text to distinguish.
      if (actionName.equals("ExecutorAction")) {
        actionName += "#" + templatePresentation.getText();
      }
      return actionName;
    }
    return currentClass.getCanonicalName();
  }
}
