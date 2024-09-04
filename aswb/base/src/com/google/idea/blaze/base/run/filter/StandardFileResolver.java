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
package com.google.idea.blaze.base.run.filter;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import javax.annotation.Nullable;

/** Parses absolute and workspace-relative paths. */
public class StandardFileResolver implements FileResolver {

  @Nullable
  @Override
  public File resolve(Project project, String fileString) {
    File file = new File(fileString);
    if (file.isAbsolute()) {
      return new File(getCanonicalPathSafe(file));
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    return projectData.getWorkspacePathResolver().resolveToFile(fileString);
  }

  /**
   * Swallows {@link IOException}s, falling back to returning the absolute, possibly non-canonical
   * path.
   */
  private static String getCanonicalPathSafe(File file) {
    try {
      return file.getCanonicalPath();
    } catch (IOException e) {
      return file.getAbsolutePath();
    }
  }
}
