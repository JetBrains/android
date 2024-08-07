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
package com.google.idea.blaze.qsync.cc;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerFlag;
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerFlagSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves {@link CcCompilerFlag} proto messages into strings, resolving included paths as
 * necessary.
 */
public class FlagResolver {

  private static final int MISSING_DIR_WARNING_LIMIT = 3;

  private final ProjectPath.Resolver pathResolver;
  private final boolean filterMissingPaths;
  private int missingFlagDirs = 0;

  public FlagResolver(ProjectPath.Resolver pathResolver) {
    this.pathResolver = pathResolver;
    this.filterMissingPaths = true;
  }

  public FlagResolver(ProjectPath.Resolver pathResolver, boolean filterMissingPaths) {
    this.pathResolver = pathResolver;
    this.filterMissingPaths = filterMissingPaths;
  }

  private Optional<String> resolve(CcCompilerFlag flag) {
    if (flag.hasPath()) {
      Path resolved = pathResolver.resolve(ProjectPath.create(flag.getPath()));
      if (!Files.isDirectory(resolved)) {
        // TODO(mathewi) it's unclear if this is necessary, and if so, if this is the right layer to
        //   do it (maybe better in CcWorkspaceBuilder?)
        if (missingFlagDirs < MISSING_DIR_WARNING_LIMIT) {
          System.err.println("Warning: " + flag.getFlag() + " path not found:" + resolved);
        }
        missingFlagDirs++;
        if (filterMissingPaths) {
          return Optional.empty();
        }
      }
      return Optional.of(flag.getFlag() + resolved);
    } else {
      return Optional.of(flag.getFlag() + flag.getPlainValue());
    }
  }

  public ImmutableList<String> resolveAll(CcCompilerFlagSet flagSet) {
    ImmutableList<String> resolved =
        flagSet.getFlagsList().stream()
            .map(this::resolve)
            .flatMap(Optional::stream)
            .collect(toImmutableList());
    if (missingFlagDirs >= MISSING_DIR_WARNING_LIMIT) {
      System.err.println("WARNING: " + missingFlagDirs + " flag paths were not found");
      missingFlagDirs = 0;
    }
    return resolved;
  }
}
