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
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFilePath;
import static com.android.tools.idea.project.messages.MessageType.WARNING;
import static com.android.tools.idea.project.messages.SyncMessage.DEFAULT_GROUP;

import com.google.common.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleModelProvider;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectSetupStep;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.utils.FileUtils;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Check if project's build script is ignored (b/67790757)
 */
public class IgnoredBuildScriptSetupStep extends ProjectSetupStep {
  public static final String IGNORED_PATH_IN_SETTINGS = "File → Settings → Editor → File Types";

  @Override
  public void setUpProject(@NotNull Project project, @Nullable ProgressIndicator indicator) {
    // Check build script
    File projectPath = getBaseDirPath(project);
    File projectBuildPath = getGradleBuildFilePath(projectPath);
    if (projectBuildPath.exists()) {
      checkIsNotIgnored("Build script for project " + project.getName(), projectBuildPath, project);
    }
    // Check .gradle folder
    File dotGradleFolder = new File(projectPath, DOT_GRADLE);
    checkIsNotIgnored("Project " + project.getName() + " " + DOT_GRADLE + " folder", dotGradleFolder, project);

    // Check all Gradle Modules
    GradleModelProvider modelProvider = GradleModelProvider.get();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      GradleBuildModel buildModel = modelProvider.getBuildModel(module);
      if (buildModel != null) {
        File moduleBuildPath = new File(buildModel.getVirtualFile().getPath());
        if (moduleBuildPath.exists() && !FileUtils.isSameFile(moduleBuildPath, projectBuildPath)) {
          // Only check for modules other than Project module
          checkIsNotIgnored("Build script for module " + module.getName(), moduleBuildPath, project);
        }
      }
    }
  }

  @Override
  public boolean invokeOnFailedSync() {
    return false;
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
        "Ignored path: " + path,
        "You can change ignored files and folders from " + IGNORED_PATH_IN_SETTINGS,
      };
      messages.report(new SyncMessage(DEFAULT_GROUP, WARNING, text));
    }
  }
}
