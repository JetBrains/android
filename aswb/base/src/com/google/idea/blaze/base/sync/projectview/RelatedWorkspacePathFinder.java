/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.projectview;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.openapi.application.ApplicationManager;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Utility class to find WorkspacePaths that are related */
public final class RelatedWorkspacePathFinder {

  public static RelatedWorkspacePathFinder getInstance() {
    return ApplicationManager.getApplication().getService(RelatedWorkspacePathFinder.class);
  }

  public ImmutableSet<WorkspacePath> findRelatedWorkspaceDirectories(
      WorkspacePathResolver pathResolver, WorkspacePath workspacePath) {

    Path path = Paths.get(workspacePath.relativePath());
    Path testsPath = Paths.get("");

    boolean foundTests = false;
    for (Path element : path) {
      if (!foundTests && element.toString().equals("java")) {
        Path potentialTestsPath = testsPath.resolve("javatests");
        if (exists(pathResolver.resolveToFile(potentialTestsPath.toString()))) {
          testsPath = potentialTestsPath;
          foundTests = true;
          continue;
        }
      }

      testsPath = testsPath.resolve(element);
    }

    if (!foundTests || !exists(pathResolver.resolveToFile(testsPath.toString()))) {
      return ImmutableSet.of();
    }

    return ImmutableSet.of(new WorkspacePath(testsPath.toString()));
  }

  private boolean exists(File file) {
    return FileOperationProvider.getInstance().exists(file);
  }
}
