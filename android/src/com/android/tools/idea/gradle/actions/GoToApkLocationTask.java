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

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.google.common.collect.Lists;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.gradle.util.Projects.isBuildWithGradle;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.notification.NotificationType.INFORMATION;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class GoToApkLocationTask implements GradleInvoker.AfterGradleInvocationTask {
  @NotNull private final Project myProject;
  @NotNull private final String myNotificationTitle;
  @NotNull private final List<File> myPotentialApkLocations;

  public GoToApkLocationTask(@NotNull String notificationTitle, @NotNull Module module, @Nullable String apkPath) {
    myProject = module.getProject();
    myNotificationTitle = notificationTitle;

    myPotentialApkLocations = Lists.newArrayList();

    if (isNotEmpty(apkPath)) {
      myPotentialApkLocations.add(new File(apkPath));
    }
    if (isBuildWithGradle(myProject)) {
      AndroidGradleModel androidModel = AndroidGradleModel.get(module);
      if (androidModel != null) {
        File buildDirPath = androidModel.getAndroidProject().getBuildFolder();
        myPotentialApkLocations.add(new File(buildDirPath, join("outputs", "apk")));
        myPotentialApkLocations.add(buildDirPath);
      }
    }
    File moduleFilePath = new File(toSystemDependentName(module.getModuleFilePath()));
    myPotentialApkLocations.add(moduleFilePath.getParentFile());

  }

  @Override
  public void execute(@NotNull GradleInvocationResult result) {
    try {
      File apkPath = getExistingApkPath();
      AndroidGradleNotification notification = AndroidGradleNotification.getInstance(myProject);
      if (result.isBuildSuccessful()) {
        if (ShowFilePathAction.isSupported()) {
          notification.showBalloon(myNotificationTitle, "APK(s) generated successfully.", INFORMATION, new GoToPathHyperlink(apkPath));
        }
        else {
          String msg = String.format("APK(s) location is\n%1$s.", apkPath.getPath());
          notification.showBalloon(myNotificationTitle, msg, INFORMATION);
        }
      }
      else {
        String msg = "Errors while building APK. You can find the errors in the 'Messages' view.";
        notification.showBalloon(myNotificationTitle, msg, ERROR);
      }
    }
    finally {
      // See https://code.google.com/p/android/issues/detail?id=195369
      GradleInvoker.getInstance(myProject).removeAfterGradleInvocationTask(this);
    }
  }

  private File getExistingApkPath() {
    for (File path : myPotentialApkLocations) {
      if (path.isDirectory()) {
        return path;
      }
    }
    // Should not happen.
    String msg = String.format("None of the paths %1$s represent an existing directory", myPotentialApkLocations);
    throw new IllegalArgumentException(msg);
  }

  private static class GoToPathHyperlink extends NotificationHyperlink {
    @NotNull private final File myDirPath;

    protected GoToPathHyperlink(@NotNull File dirPath) {
      super("go.to.path", RevealFileAction.getActionName());
      myDirPath = dirPath;
    }

    @Override
    protected void execute(@NotNull Project project) {
      ShowFilePathAction.openDirectory(myDirPath);
    }
  }
}
