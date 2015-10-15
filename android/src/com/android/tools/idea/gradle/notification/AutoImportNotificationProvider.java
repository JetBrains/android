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
package com.android.tools.idea.gradle.notification;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleProjectSettings;
import static com.android.tools.idea.gradle.util.Projects.isBuildWithGradle;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.intellij.ide.BrowserUtil.browse;

/**
 * Notifies users that Gradle "auto-import" feature is enabled, explains the issues with this feature and offers a way to disable it. This
 * notification only appears on build.gradle and settings.gradle files.
 */
public class AutoImportNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("android.gradle.auto.import");

  @NotNull private final Project myProject;
  @NotNull private final EditorNotifications myNotifications;

  public AutoImportNotificationProvider(@NotNull Project project, @NotNull EditorNotifications notifications) {
    myProject = project;
    myNotifications = notifications;
  }

  @Override
  @NotNull
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  @Nullable
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (!isBuildWithGradle(myProject)) {
      return null;
    }
    String name = file.getName();
    if (FN_BUILD_GRADLE.equals(name) || FN_SETTINGS_GRADLE.equals(name)) {
      GradleProjectSettings settings = getGradleProjectSettings(myProject);
      if (isAndroidStudio() && settings != null && settings.isUseAutoImport()) {
        return new DisableAutoImportNotificationPanel(settings);
      }
    }
    return null;
  }

  private class DisableAutoImportNotificationPanel extends EditorNotificationPanel {
    DisableAutoImportNotificationPanel(@NotNull final GradleProjectSettings settings) {
      setText("Gradle 'auto-import' will considerably slow down the IDE, due to a known bug.");

      createActionLabel("Open bug report", new Runnable() {
        @Override
        public void run() {
          browse("https://code.google.com/p/android/issues/detail?id=59965");
        }
      });

      createActionLabel("Disable 'auto-import'", new Runnable() {
        @Override
        public void run() {
          settings.setUseAutoImport(false);
          myNotifications.updateAllNotifications();
        }
      });
    }
  }

}
