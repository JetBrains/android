/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.deps;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.project.ProjectPath;

/**
 * An update to the project proto that operates on a {@link ProjectProtoUpdate}. Also defines some
 * constants that are useful to implementations.
 *
 * <p>Implementations of this interface must not depend on any project state other than {@link
 * com.google.idea.blaze.qsync.project.ProjectDefinition}.
 */
public interface ProjectProtoUpdateOperation {

  @FunctionalInterface
  interface CachedArtifactProvider {
    CachedArtifact apply(BuildArtifact buildArtifact, ProjectPath artifactDirectory) throws BuildException;
  }

  String JAVA_DEPS_LIB_NAME = ".dependencies";
  ImmutableSet<String> JAVA_ARCHIVE_EXTENSIONS = ImmutableSet.of("jar", "srcjar");

  default ImmutableSetMultimap<BuildArtifact, ArtifactMetadata> getRequiredArtifacts(
      TargetBuildInfo forTarget) {
    return ImmutableSetMultimap.of();
  }

  void update(ProjectProtoUpdate update) throws BuildException;
}
