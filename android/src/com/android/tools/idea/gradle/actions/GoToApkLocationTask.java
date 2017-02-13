/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.sync.hyperlink.NotificationHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.notification.EventLog;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.*;

import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.notification.NotificationType.INFORMATION;

public class GoToApkLocationTask implements GradleBuildInvoker.AfterGradleInvocationTask {
  @NotNull private final Project myProject;
  @NotNull private final List<Pair<Module, String>> myModulesAndApkPaths;
  @NotNull private final ApkPathFinder myApkPathFinder;
  @NotNull private final String myNotificationTitle;

  GoToApkLocationTask(@NotNull List<Module> modules, @NotNull String notificationTitle) {
    this(modules.get(0).getProject(), createModulesAndApkPaths(modules), new ApkPathFinder(), notificationTitle);
  }

  @NotNull
  private static List<Pair<Module, String>> createModulesAndApkPaths(@NotNull List<Module> modules) {
    List<Pair<Module, String>> modulesAndApkPaths = new ArrayList<>(modules.size());
    for (Module module : modules) {
      modulesAndApkPaths.add(Pair.create(module, null));
    }
    return modulesAndApkPaths;
  }

  public GoToApkLocationTask(@NotNull Module module, @NotNull String notificationTitle, @Nullable String apkPathValue) {
    this(module.getProject(), Collections.singletonList(Pair.create(module, apkPathValue)), new ApkPathFinder(), notificationTitle);
  }

  @VisibleForTesting
  GoToApkLocationTask(@NotNull Project project,
                      @NotNull List<Pair<Module, String>> modulesAndApkPaths,
                      @NotNull ApkPathFinder apkPathFinder,
                      @NotNull String notificationTitle) {
    myProject = project;
    myModulesAndApkPaths = modulesAndApkPaths;
    myApkPathFinder = apkPathFinder;
    myNotificationTitle = notificationTitle;
  }

  @Override
  public void execute(@NotNull GradleInvocationResult result) {
    try {
      List<String> moduleNames = new ArrayList<>();
      Map<String, File> apkPathsByModule = new HashMap<>();
      for (Pair<Module, String> moduleAndApkPath : myModulesAndApkPaths) {
        Module module = moduleAndApkPath.getFirst();
        File apkPath = myApkPathFinder.findExistingApkPath(module, moduleAndApkPath.getSecond());
        if (apkPath != null) {
          String moduleName = module.getName();
          moduleNames.add(moduleName);
          apkPathsByModule.put(moduleName, apkPath);
        }
      }

      Collections.sort(moduleNames);

      AndroidGradleNotification notification = AndroidGradleNotification.getInstance(myProject);
      if (result.isBuildSuccessful()) {
        if (ShowFilePathAction.isSupported()) {
          StringBuilder buffer = new StringBuilder();
          buffer.append("APK(s) generated successfully: ");
          int moduleCount = moduleNames.size();
          for (int i = 0; i < moduleCount; i++) {
            String moduleName = moduleNames.get(i);
            buffer.append("<a href=\"").append(moduleName).append("\">").append(moduleName).append("</a>");
            if (i < moduleCount - 1) {
              buffer.append(", ");
            }
          }
          buffer.append(".");
          String text = buffer.toString();
          notification.showBalloon(myNotificationTitle, text, INFORMATION, new OpenFolderNotificationListener(apkPathsByModule));
        }
        else {
          // Platform does not support showing the location of a file.
          // Display file paths in the 'Log' view, since they could be too long to show in a balloon notification.
          StringBuilder buffer = new StringBuilder();
          buffer.append("APK(s) generated successfully:\n");
          int moduleCount = moduleNames.size();
          for (int i = 0; i < moduleCount; i++) {
            String moduleName = moduleNames.get(i);
            buffer.append(" - ").append(moduleName).append(": ");
            buffer.append(apkPathsByModule.get(moduleName).getPath());
            if (i < moduleCount - 1) {
              buffer.append("\n");
            }
          }
          notification.showBalloon(myNotificationTitle, "APK(s) generated successfully.", INFORMATION, new OpenEventLogHyperlink());
          notification.addLogEvent(myNotificationTitle, buffer.toString(), INFORMATION);
        }
      }
      else if (result.isBuildCancelled()) {
        notification.showBalloon(myNotificationTitle, "Build cancelled.", INFORMATION);
      }
      else {
        String msg = "Errors while building APK. You can find the errors in the 'Messages' view.";
        notification.showBalloon(myNotificationTitle, msg, ERROR);
      }
    }
    finally {
      // See https://code.google.com/p/android/issues/detail?id=195369
      GradleBuildInvoker.getInstance(myProject).remove(this);
    }
  }

  @VisibleForTesting
  static class OpenFolderNotificationListener extends NotificationListener.Adapter {
    @NotNull private final Map<String, File> myApkPathsPerModule;

    OpenFolderNotificationListener(@NotNull Map<String, File> apkPathsPerModule) {
      myApkPathsPerModule = apkPathsPerModule;
    }

    @Override
    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
      String moduleName = e.getDescription();
      File apkPath = myApkPathsPerModule.get(moduleName);
      assert apkPath != null;
      ShowFilePathAction.openDirectory(apkPath);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      OpenFolderNotificationListener listener = (OpenFolderNotificationListener)o;
      return Objects.equals(myApkPathsPerModule, listener.myApkPathsPerModule);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myApkPathsPerModule);
    }
  }

  private static class OpenEventLogHyperlink extends NotificationHyperlink {
    OpenEventLogHyperlink() {
      super("open.event.log", "Show APK path(s) in the 'Event Log' view");
    }

    @Override
    protected void execute(@NotNull Project project) {
      ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(EventLog.LOG_TOOL_WINDOW_ID);
      if (tw != null) {
        tw.activate(null, false);
      }
    }
  }
}
