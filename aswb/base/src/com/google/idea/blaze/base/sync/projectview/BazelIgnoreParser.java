/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;

/** A parser for .bazelgnore files, which tells Bazel a list of paths to ignore. */
public class BazelIgnoreParser {

  private static final Logger logger = Logger.getInstance(BazelIgnoreParser.class);

  private final File bazelIgnoreFile;

  public BazelIgnoreParser(WorkspaceRoot workspaceRoot) {
    this.bazelIgnoreFile = workspaceRoot.fileForPath(new WorkspacePath(".bazelignore"));
  }

  /**
   * Parse a .bazelignore file (if it exists) for workspace relative paths.
   *
   * @return a list of validated WorkspacePaths.
   */
  public ImmutableList<WorkspacePath> getIgnoredPaths() {
    if (!FileOperationProvider.getInstance().exists(bazelIgnoreFile)) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<WorkspacePath> ignoredPaths = ImmutableList.builder();

    try {
      for (String path : FileOperationProvider.getInstance().readAllLines(bazelIgnoreFile)) {
        if (path.trim().isEmpty() || path.trim().startsWith("#")) {
          continue;
        }

        if (path.endsWith("/")) {
          // .bazelignore allows the "/" path suffix, but WorkspacePath doesn't.
          path = path.substring(0, path.length() - 1);
        }

        if (!WorkspacePath.isValid(path)) {
          logger.warn(
              String.format(
                  "Found %s in .bazelignore, but unable to parse as relative workspace path.",
                  path));
          continue;
        }

        ignoredPaths.add(new WorkspacePath(path));
      }
    } catch (IOException e) {
      logger.warn(String.format("Unable to read .bazelignore file even though it exists."));
    }

    return ignoredPaths.build();
  }
}
