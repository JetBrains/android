/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.action;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.project.TargetsToBuild;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import java.util.Collections;

/** Utility for identifying ambiguities in targets to build for files */
public class TargetDisambiguator {
  public final ImmutableSet<Label> unambiguousTargets;
  public final ImmutableSet<TargetsToBuild> ambiguousTargetSets;

  private TargetDisambiguator(ImmutableMap<TargetsToBuild, Path> targetsToPath) {
    unambiguousTargets = TargetsToBuild.getAllUnambiguous(targetsToPath.keySet());
    ambiguousTargetSets = TargetsToBuild.getAllAmbiguous(targetsToPath.keySet());
  }

  public static TargetDisambiguator createForFiles(
      Project project, ImmutableSet<VirtualFile> files, BuildDependenciesHelper helper) {
    Path workspaceRoot = WorkspaceRoot.fromProject(project).path();
    return createForPaths(
        files.stream()
            .map(VirtualFile::toNioPath)
            .filter(p -> p.startsWith(workspaceRoot))
            .map(workspaceRoot::relativize)
            .collect(toImmutableSet()),
        helper);
  }

  public static TargetDisambiguator createForPaths(
      ImmutableSet<Path> workspaceRelativePaths, BuildDependenciesHelper helper) {
    // Find the targets to build per source file, and de-dupe then such that if several source files
    // are built by the same set of targets, we consider them as one. Map these results back to an
    // original source file to so we can show it in the UI:
    ImmutableMap.Builder<TargetsToBuild, Path> targetsByFileBuilder = ImmutableMap.builder();
    for (Path path : workspaceRelativePaths) {
      TargetsToBuild tabTargets = helper.getTargetsToEnableAnalysisFor(path);
      targetsByFileBuilder.put(tabTargets, path);
    }
    ImmutableMap<TargetsToBuild, Path> targetsToBuild = targetsByFileBuilder.buildKeepingLast();
    return new TargetDisambiguator(targetsToBuild);
  }

  /**
   * Finds the sets of targets that cannot be unambiguously resolved.
   *
   * <p>The is the set of ambiguous targets sets which contain no targets that overlap with the
   * unambiguous set of targets.
   */
  public ImmutableSet<TargetsToBuild> calculateUnresolvableTargets() {
    ImmutableSet.Builder<TargetsToBuild> ambiguousTargetsBuilder = ImmutableSet.builder();
    for (TargetsToBuild ambiguous : ambiguousTargetSets) {
      if (!Collections.disjoint(ambiguous.targets(), unambiguousTargets)) {
        // we already have (at least) one of these targets from the unambiguous set, so don't need
        // to choose one.
      } else {
        ambiguousTargetsBuilder.add(ambiguous);
      }
    }
    return ambiguousTargetsBuilder.build();
  }
}
