/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.projectview;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.application.ApplicationManager;
import java.io.File;
import java.io.IOException;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Manages project view storage.
 *
 * <p>For the most part, use ProjectViewManager instead. This is a lower-level API intended for use
 * by ProjectViewManager itself, and during the import process before a project exists.
 */
public abstract class ProjectViewStorageManager {

  private static final String BLAZE_EXTENSION = "blazeproject";
  private static final String BAZEL_EXTENSION = "bazelproject";
  private static final String LEGACY_EXTENSION = "asproject";

  public static final ImmutableList<String> VALID_EXTENSIONS =
      ImmutableList.of(BLAZE_EXTENSION, BAZEL_EXTENSION, LEGACY_EXTENSION);

  public static boolean isProjectViewFile(@NotNull File file) {
    return isProjectViewFile(file.getName());
  }

  public static boolean isProjectViewFile(String fileName) {
    for (String ext : VALID_EXTENSIONS) {
      if (fileName.endsWith("." + ext)) {
        return true;
      }
    }
    return false;
  }

  public static String getProjectViewFileName(BuildSystemName buildSystemName) {
    switch (buildSystemName) {
      case Blaze:
        return "." + BLAZE_EXTENSION;
      case Bazel:
        return "." + BAZEL_EXTENSION;
      default:
        throw new IllegalArgumentException("Unrecognized build system type: " + buildSystemName);
    }
  }

  public static File getLocalProjectViewFileName(
      BuildSystemName buildSystemName, File projectDataDirectory) {
    return new File(projectDataDirectory, getProjectViewFileName(buildSystemName));
  }

  public static ProjectViewStorageManager getInstance() {
    return ApplicationManager.getApplication().getService(ProjectViewStorageManager.class);
  }

  @Nullable
  public abstract String loadProjectView(File projectViewFile) throws IOException;

  public abstract void writeProjectView(String projectViewText, File projectViewFile)
      throws IOException;
}
