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
package com.google.idea.blaze.qsync.java;

import static com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.AllowPackagePrefixes.EMPTY_PACKAGE_PREFIXES_ONLY;

import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.JarPath;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto.LibrarySource;
import java.nio.file.Path;

/**
 * Adds checked-in {@code .srcjar} files from external dependencies to the project proto. This
 * allows those sources to be shown in the IDE instead of decompiled class files.
 */
public class AddDependencySrcJars implements ProjectProtoUpdateOperation {

  private final ProjectDefinition projectDefinition;
  private final ProjectPath.Resolver pathResolver;
  private final SrcJarInnerPathFinder srcJarInnerPathFinder;

  public AddDependencySrcJars(
      ProjectDefinition projectDefinition,
      ProjectPath.Resolver pathResolver,
      SrcJarInnerPathFinder srcJarInnerPathFinder) {
    this.projectDefinition = projectDefinition;
    this.pathResolver = pathResolver;
    this.srcJarInnerPathFinder = srcJarInnerPathFinder;
  }

  @Override
  public void update(ProjectProtoUpdate update, ArtifactTracker.State artifactState)
      throws BuildException {
    for (TargetBuildInfo target : artifactState.depsMap().values()) {
      if (target.javaInfo().isEmpty()) {
        continue;
      }
      JavaArtifactInfo javaInfo = target.javaInfo().get();
      if (projectDefinition.isIncluded(javaInfo.label())) {
        continue;
      }
      for (Path srcJar : javaInfo.srcJars()) {
        // these are workspace relative srcjar paths.
        ProjectPath jarPath = ProjectPath.workspaceRelative(srcJar);
        srcJarInnerPathFinder
            .findInnerJarPaths(
                pathResolver.resolve(jarPath).toFile(),
                EMPTY_PACKAGE_PREFIXES_ONLY,
                srcJar.toString())
            .stream()
            .map(JarPath::path)
            .map(jarPath::withInnerJarPath)
            .map(ProjectPath::toProto)
            .map(LibrarySource.newBuilder()::setSrcjar)
            .forEach(update.library(JAVA_DEPS_LIB_NAME)::addSources);
      }
    }
  }
}
