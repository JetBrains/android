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
package com.android.tools.idea.gradle.project.importing;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFilePath;
import static com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static com.intellij.openapi.util.io.FileUtil.ensureExists;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;

public abstract class ProjectFolder {
  public abstract void createTopLevelBuildFile() throws IOException;

  public abstract void createIdeaProjectFolder() throws IOException;

  public static class Factory {
    @NotNull public ProjectFolder create(@NotNull File projectFolderPath) {
      return new ProjectFolderImpl(projectFolderPath);
    }
  }

  @VisibleForTesting
  static class ProjectFolderImpl extends ProjectFolder {
    @NotNull private final File myPath;

    ProjectFolderImpl(@NotNull File path) {
      myPath = path;
    }

    @Override
    public void createTopLevelBuildFile() throws IOException {
      File buildFile = getGradleBuildFilePath(myPath);
      if (buildFile.isFile()) {
        return;
      }
      createIfNotExists(buildFile);
      String contents = "// Top-level build file where you can add configuration options common to all sub-projects/modules." +
                        SystemProperties.getLineSeparator();
      writeToFile(buildFile, contents);
    }

    @Override
    public void createIdeaProjectFolder() throws IOException {
      File ideaFolderPath = new File(myPath, DIRECTORY_STORE_FOLDER);
      if (ideaFolderPath.isDirectory()) {
        // "libraries" is hard-coded in com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
        File librariesFolderPath = new File(ideaFolderPath, "libraries");
        if (librariesFolderPath.exists()) {
          // remove contents of libraries. This is useful when importing existing projects that may have invalid library entries (e.g.
          // created with Studio 0.4.3 or earlier.)
          if (!delete(librariesFolderPath)) {
            getLogger().info(String.format("Failed to delete %1$s'", librariesFolderPath.getPath()));
          }
        }
        return;
      }
      ensureExists(ideaFolderPath);
    }

    @NotNull
    private Logger getLogger() {
      return Logger.getInstance(getClass());
    }
  }
}
