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
package com.android.tools.idea.gradle.sdk;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_SDK_PATH_CHANGED;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public class GradleAndroidSdkEventListener implements IdeSdks.AndroidSdkEventListener {
  private static final Logger LOG = Logger.getInstance(GradleAndroidSdkEventListener.class);

  /**
   * Updates the path of the SDK manager in the project's local.properties file, only if the new path and the path in the file are
   * different. This method will request a project sync, unless an error ocurred when updating the local.properties file.
   *
   * @param sdkPath the new Android SDK path.
   * @param project one of the projects currently open in the IDE.
   */
  @Override
  public void afterSdkPathChange(@NotNull File sdkPath, @NotNull Project project) {
    if (!(ProjectSystemUtil.getProjectSystem(project) instanceof GradleProjectSystem)) {
      return;
    }
    LocalProperties localProperties;
    try {
      localProperties = new LocalProperties(getBaseDirPath(project));
    }
    catch (IOException e) {
      // Exception thrown when local.properties file exists but cannot be read (e.g. no writing permissions.)
      logAndShowErrorWhenUpdatingLocalProperties(project, e, "read", sdkPath);
      return;
    }
    if (!filesEqual(sdkPath, localProperties.getAndroidSdkPath())) {
      try {
        localProperties.setAndroidSdkPath(sdkPath);
        localProperties.save();
      }
      catch (IOException e) {
        logAndShowErrorWhenUpdatingLocalProperties(project, e, "update", sdkPath);
        return;
      }
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      GradleSyncInvoker.getInstance().requestProjectSync(project, new GradleSyncInvoker.Request(TRIGGER_SDK_PATH_CHANGED), null);
    }
  }

  private static void logAndShowErrorWhenUpdatingLocalProperties(@NotNull Project project,
                                                                 @NotNull Exception error,
                                                                 @NotNull String action,
                                                                 @NotNull File sdkHomePath) {
    LOG.info(error);
    String msg = String.format("Unable to %1$s local.properties file in project '%2$s'.\n\n" +
                               "Cause: %3$s\n\n" +
                               "Please manually update the file's '%4$s' property value to \n" +
                               "'%5$s'\n" +
                               "and sync the project with Gradle files.", action, project.getName(), getMessage(error),
                               SdkConstants.SDK_DIR_PROPERTY, sdkHomePath.getPath());
    Messages.showErrorDialog(project, msg, "Project SDK Update");
  }

  @NotNull
  private static String getMessage(@NotNull Exception e) {
    String cause = e.getMessage();
    return isNullOrEmpty(cause) ? "[Unknown]" : cause;
  }
}
