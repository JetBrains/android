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
package com.android.tools.idea.gradle.project.sync.setup.post.project;

import static com.android.SdkConstants.DOT_GRADLE;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.project.messages.MessageType.WARNING;
import static com.android.tools.idea.project.messages.SyncMessage.DEFAULT_GROUP;
import static com.android.utils.BuildScriptUtil.findGradleBuildFile;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueNotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectSetupStep;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.utils.FileUtils;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import java.io.File;
import org.jetbrains.annotations.NotNull;

/**
 * Check if project's build script is ignored (b/67790757)
 */
public class IgnoredBuildScriptSetupStep extends ProjectSetupStep {
  @Override
  public void setUpProject(@NotNull Project project) {
    // Check build script
    File projectPath = getBaseDirPath(project);
    File projectBuildPath = findGradleBuildFile(projectPath);
    if (projectBuildPath.exists()) {
      checkIsNotIgnored("Build script for project " + project.getName(), projectBuildPath, project);
    }
    // Check .gradle folder
    File dotGradleFolder = new File(projectPath, DOT_GRADLE);
    checkIsNotIgnored("Project " + project.getName() + " " + DOT_GRADLE + " folder", dotGradleFolder, project);

    // Check all Gradle Modules
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      GradleFacet gradleFacet = GradleFacet.getInstance(module);
      if (gradleFacet == null) {
        continue;
      }
      GradleModuleModel gradleModel = gradleFacet.getGradleModuleModel();
      if (gradleModel == null) {
        continue;
      }
      File buildPath = gradleModel.getBuildFilePath();
      if (buildPath != null && buildPath.exists() && !FileUtils.isSameFile(buildPath, projectBuildPath)) {
        checkIsNotIgnored("Build script for module " + module.getName(), buildPath, project);
      }
    }
  }

  @Override
  public boolean invokeOnFailedSync() {
    return super.invokeOnFailedSync();
  }

  public static String getIgnoredFileTypesPathInSettings() {
    return String.format("%s → Editor → File Types", ShowSettingsUtil.getSettingsMenuName());
  }

  /**
   * Check that the file is not ignored by the {@link FileTypeManager}. If it is, then create a sync message.
   *
   * @param prefix  Describe what the file is.
   * @param path    Where the file is located.
   * @param project What project this file belongs to.
   */
  public static void checkIsNotIgnored(@NotNull String prefix, @NotNull File path, @NotNull Project project) {
    checkIsNotIgnored(prefix, path, FileTypeManager.getInstance(), GradleSyncMessages.getInstance(project));
  }

  @VisibleForTesting
  static void checkIsNotIgnored(@NotNull String prefix, @NotNull File path, @NotNull FileTypeManager fileTypeManager, @NotNull GradleSyncMessages messages) {
    if (fileTypeManager.isFileIgnored(path.getPath())) {
      String[] text = {
        prefix + " is being ignored. This can cause issues on the IDE.",
        "You can change ignored files and folders from " + getIgnoredFileTypesPathInSettings(),
      };
      SyncMessage message = new SyncMessage(DEFAULT_GROUP, WARNING, text);
      message.add(new SyncIssueNotificationHyperlink("open.settings.filetypes", "Open in Settings", null) {
        @Override
        protected void execute(@NotNull Project project) {
          ShowSettingsUtil.getInstance().showSettingsDialog(project, "preferences.fileTypes");
        }
      });
      message.add(new OpenFileHyperlink(path.getPath(), "Open ignored " + ((path.isDirectory()) ? "folder location" : "file"), -1, -1));
      messages.report(message);
    }
  }
}
