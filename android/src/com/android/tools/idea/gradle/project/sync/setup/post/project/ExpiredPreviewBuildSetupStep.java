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
package com.android.tools.idea.gradle.project.sync.setup.post.project;

import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectSetupStep;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Calendar;

import static com.intellij.notification.NotificationType.INFORMATION;
import static java.util.Calendar.MONTH;

public class ExpiredPreviewBuildSetupStep extends ProjectSetupStep {
  @NotNull private final ApplicationInfo myApplicationInfo;

  private volatile boolean myExpirationChecked;

  @SuppressWarnings("unused") // Instantiated by IDEA
  public ExpiredPreviewBuildSetupStep() {
    this(ApplicationInfo.getInstance());
  }

  public ExpiredPreviewBuildSetupStep(@NotNull ApplicationInfo applicationInfo) {
    myApplicationInfo = applicationInfo;
  }

  @Override
  public void setUpProject(@NotNull Project project, @Nullable ProgressIndicator indicator) {
    if (myExpirationChecked) {
      return;
    }
    String ideVersion = myApplicationInfo.getFullVersion();
    if (isPreview(ideVersion)) {
      // Expire preview builds two months after their build date (which is going to be roughly six weeks after release; by
      // then will definitely have updated the build
      Calendar expirationDate = (Calendar)myApplicationInfo.getBuildDate().clone();
      expirationDate.add(MONTH, 2);

      Calendar now = Calendar.getInstance();
      if (now.after(expirationDate)) {
        String message = String.format("This preview build (%1$s) is old; please update to a newer preview or a stable version.",
                                       ideVersion);
        OpenUrlHyperlink hyperlink = new OpenUrlHyperlink("https://developer.android.com/r/studio-ui/download-canary.html", "Get the Latest Version");
        AndroidNotification.getInstance(project).showBalloon("Old Preview Build", message, INFORMATION, hyperlink);

        // If we show an expiration message, don't also show a second balloon.
        myExpirationChecked = true;
      }
    }
  }

  private static boolean isPreview(@NotNull String ideVersion) {
    return ideVersion.contains("Preview") || ideVersion.contains("Beta") || ideVersion.contains("RC");
  }

  @Override
  public boolean invokeOnFailedSync() {
    return true;
  }

  @VisibleForTesting
  boolean isExpirationChecked() {
    return myExpirationChecked;
  }
}
