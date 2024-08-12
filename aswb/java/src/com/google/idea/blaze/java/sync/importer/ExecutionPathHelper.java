/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.sync.importer;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;

/**
 * Derives an {@link ArtifactLocation} from a workspace-root relative paths, possibly representing a
 * generated artifact.
 *
 * <p>Uses a heuristic to split the workspace-root relative path into an execution-root relative
 * component and a relative path component.
 *
 * <p>For example, 'bazel-out/k8-opt/bin/path/to/artifact.jar' is composed of an execution-root
 * relative path 'bazel-out/k8-opt/bin' and a relative path 'path/to/artifact.jar'.
 */
final class ExecutionPathHelper {

  /** Parse an {@link ArtifactLocation} from an execution-root-relative path. */
  static ArtifactLocation parse(
      WorkspaceRoot root,
      BuildSystemProvider buildSystemProvider,
      String executionRootRelativePath) {
    // Bazel should always use '/' as the file separator char for these paths.
    int firstSep = executionRootRelativePath.indexOf('/');
    if (firstSep < 0) {
      return ArtifactLocation.builder()
          .setIsSource(true)
          .setRelativePath(executionRootRelativePath)
          .build();
    }
    String firstPathComponent = executionRootRelativePath.substring(0, firstSep);
    if (isExternal(firstPathComponent)) {
      return ArtifactLocation.builder()
          .setIsExternal(true)
          .setIsSource(true)
          .setRelativePath(executionRootRelativePath)
          .build();
    }
    ImmutableList<String> outputDirs = buildSystemProvider.buildArtifactDirectories(root);
    boolean isSource = !outputDirs.contains(firstPathComponent);
    if (isSource) {
      return ArtifactLocation.builder()
          .setIsSource(true)
          .setRelativePath(executionRootRelativePath)
          .build();
    }
    int secondSep = executionRootRelativePath.indexOf('/', firstSep + 1);
    int thirdSep = executionRootRelativePath.indexOf('/', secondSep + 1);
    if (secondSep < 0 || thirdSep < 0) {
      return ArtifactLocation.builder()
          .setRootExecutionPathFragment(executionRootRelativePath)
          .setRelativePath("")
          .build();
    }
    return ArtifactLocation.builder()
        .setIsSource(false)
        .setIsExternal(false)
        .setRootExecutionPathFragment(executionRootRelativePath.substring(0, thirdSep))
        .setRelativePath(executionRootRelativePath.substring(thirdSep + 1))
        .build();
  }

  private static boolean isExternal(String firstPathComponent) {
    return firstPathComponent.equals("external");
  }
}
