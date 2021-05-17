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
package com.android.tools.idea.gradle.project.build;

import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * After a build is complete, this class will execute the following tasks:
 * <ul>
 * <li>Refresh Studio's view of the file system (to see generated files)</li>
 * <li>Remove any build-related data stored in the project itself (e.g. modules to build, current "build mode", etc.)</li>
 * </ul>
 */
public class PostProjectBuildTasksExecutor {
  private static final Key<Long> PROJECT_LAST_BUILD_TIMESTAMP_KEY = Key.create("android.gradle.project.last.build.timestamp");

  @NotNull private final Project myProject;

  @NotNull
  public static PostProjectBuildTasksExecutor getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, PostProjectBuildTasksExecutor.class);
  }

  public PostProjectBuildTasksExecutor(@NotNull Project project) {
    myProject = project;
  }

  @Nullable
  public Long getLastBuildTimestamp() {
    return myProject.getUserData(PROJECT_LAST_BUILD_TIMESTAMP_KEY);
  }

  public void onBuildCompletion() {
    if (AndroidProjectInfo.getInstance(myProject).requiresAndroidModel()) {
      BuildSettings buildSettings = BuildSettings.getInstance(myProject);
      String runConfigurationTypeId = buildSettings.getRunConfigurationTypeId();
      buildSettings.clear();

      // Refresh Studio's view of the file system after a compile. This is necessary for Studio to see generated code.
      // If this build is invoked from a run configuration, then we should refresh synchronously since subsequent task,
      // e.g. unit test run, might need updated VFS state immediately.
      refreshProject(runConfigurationTypeId == null);

      myProject.putUserData(PROJECT_LAST_BUILD_TIMESTAMP_KEY, System.currentTimeMillis());
    }
  }

  /**
   * Refreshes the cached view of the project's contents.
   */
  private void refreshProject(boolean asynchronous) {
    String projectPath = myProject.getBasePath();
    if (projectPath != null) {
      VirtualFile rootDir = LocalFileSystem.getInstance().findFileByPath(projectPath);
      if (rootDir != null && rootDir.isDirectory()) {
        rootDir.refresh(asynchronous, true);
      }
    }
  }
}
