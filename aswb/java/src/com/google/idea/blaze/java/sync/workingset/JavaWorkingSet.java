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
package com.google.idea.blaze.java.sync.workingset;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.java.sync.source.JavaLikeLanguage;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Computes the working set of files of directories from source control.
 *
 * <p>The working set is: - All new untracked directories (git only) - All modified BUILD files -
 * All modified java files
 *
 * <p>A rule is considered part of the working set if any of the following is true: - Its BUILD file
 * is modified - Its BUILD file is under a new directory - Any of its java files are modified - Any
 * of its java files are under a new directory
 *
 * <p>Rules in the working set get an expanded classpath of their direct deps, i.e. they temporarily
 * defeat classpath reduction.
 */
public class JavaWorkingSet {
  private final Set<String> modifiedBuildFileRelativePaths;
  private final Set<String> modifiedJavaFileRelativePaths;

  public JavaWorkingSet(
      WorkspaceRoot workspaceRoot,
      WorkingSet workingSet,
      Predicate<String> buildFileNamePredicate) {
    Set<String> modifiedBuildFileRelativePaths = Sets.newHashSet();
    Set<String> modifiedJavaLikeFileRelativePaths = Sets.newHashSet();

    for (WorkspacePath workspacePath :
        Iterables.concat(workingSet.addedFiles, workingSet.modifiedFiles)) {
      if (buildFileNamePredicate.test(workspaceRoot.fileForPath(workspacePath).getName())) {
        modifiedBuildFileRelativePaths.add(workspacePath.relativePath());
      } else if (JavaLikeLanguage.getAllFileExtension().stream()
          .anyMatch(fileExtension -> workspacePath.relativePath().endsWith(fileExtension))) {
        modifiedJavaLikeFileRelativePaths.add(workspacePath.relativePath());
      }
    }

    this.modifiedBuildFileRelativePaths = modifiedBuildFileRelativePaths;
    this.modifiedJavaFileRelativePaths = modifiedJavaLikeFileRelativePaths;
  }

  public boolean isTargetInWorkingSet(TargetIdeInfo target) {
    ArtifactLocation buildFile = target.getBuildFile();
    if (buildFile != null) {
      if (modifiedBuildFileRelativePaths.contains(buildFile.getRelativePath())) {
        return true;
      }
    }

    for (ArtifactLocation artifactLocation : target.getSources()) {
      if (isInWorkingSet(artifactLocation)) {
        return true;
      }
    }
    return false;
  }

  public boolean isInWorkingSet(ArtifactLocation artifactLocation) {
    return isInWorkingSet(artifactLocation.getRelativePath());
  }

  boolean isInWorkingSet(String relativePath) {
    return modifiedJavaFileRelativePaths.contains(relativePath);
  }
}
