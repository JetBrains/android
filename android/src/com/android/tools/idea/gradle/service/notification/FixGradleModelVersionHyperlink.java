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
package com.android.tools.idea.gradle.service.notification;

import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;
import static com.android.tools.idea.gradle.parser.BuildFileKey.PLUGIN_VERSION;
import static com.android.tools.idea.gradle.service.notification.FixGradleVersionInWrapperHyperlink.updateGradleVersion;
import static com.intellij.notification.NotificationType.ERROR;

class FixGradleModelVersionHyperlink extends NotificationHyperlink {
  private final boolean myOpenMigrationGuide;

  FixGradleModelVersionHyperlink() {
    this("Open migration guide, fix plug-in version and sync project", true);
  }

  FixGradleModelVersionHyperlink(@NotNull String text, boolean openMigrationGuide) {
    super("fixGradleElements", text);
    myOpenMigrationGuide = openMigrationGuide;
  }

  @Override
  protected void execute(@NotNull Project project) {
    if (myOpenMigrationGuide) {
      BrowserUtil.browse("http://tools.android.com/tech-docs/new-build-system/migrating_to_09");
    }

    ModuleManager moduleManager = ModuleManager.getInstance(project);
    boolean atLeastOnUpdated = false;
    for (Module module : moduleManager.getModules()) {
      VirtualFile file = GradleUtil.getGradleBuildFile(module);
      if (file != null) {
        final GradleBuildFile buildFile = new GradleBuildFile(file, project);
        Object pluginVersion = buildFile.getValue(PLUGIN_VERSION);
        if (pluginVersion != null) {
          WriteCommandAction.runWriteCommandAction(project, new Runnable() {
            @Override
            public void run() {
              buildFile.setValue(PLUGIN_VERSION, GRADLE_PLUGIN_RECOMMENDED_VERSION);
            }
          });
          atLeastOnUpdated = true;
        }
      }
    }
    if (!atLeastOnUpdated) {
      String msg = "Unable to find any references to the Android Gradle plug-in in build.gradle files.\n\n" +
                   "Please click the link to perform a textual search and then update the build files manually.";
      SearchInBuildFilesHyperlink hyperlink = new SearchInBuildFilesHyperlink("com.android.tools.build:gradle");
      AndroidGradleNotification.getInstance(project).showBalloon(ERROR_MSG_TITLE, msg, ERROR, hyperlink);
      return;
    }
    File wrapperPropertiesFile = GradleUtil.findWrapperPropertiesFile(project);
    if (wrapperPropertiesFile != null) {
      updateGradleVersion(project, wrapperPropertiesFile, GRADLE_LATEST_VERSION);
    }
    GradleProjectImporter.getInstance().requestProjectSync(project, null);
  }
}
