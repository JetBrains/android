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

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableCollection;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactDirectories;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.JarPath;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto.LibrarySource;

/**
 * Adds generated {@code .srcjar} files from external dependencies to the {@code .dependencies}
 * library. This means that when navigating to these dependencies, we see the generated sources
 * rather than decompiled code.
 */
public class AddDependencyGenSrcsJars implements ProjectProtoUpdateOperation {

  private final Supplier<ImmutableCollection<TargetBuildInfo>> builtTargetsSupplier;
  private final CachedArtifactProvider cachedArtifactProvider;
  private final ProjectDefinition projectDefinition;
  private final SrcJarInnerPathFinder srcJarInnerPathFinder;

  public AddDependencyGenSrcsJars(
      Supplier<ImmutableCollection<TargetBuildInfo>> builtTargetsSupplier,
      ProjectDefinition projectDefinition,
      CachedArtifactProvider cachedArtifactProvider,
      SrcJarInnerPathFinder srcJarInnerPathFinder) {
    this.builtTargetsSupplier = builtTargetsSupplier;
    this.projectDefinition = projectDefinition;
    this.cachedArtifactProvider = cachedArtifactProvider;
    this.srcJarInnerPathFinder = srcJarInnerPathFinder;
  }

  @Override
  public void update(ProjectProtoUpdate update) throws BuildException {
    for (TargetBuildInfo target : builtTargetsSupplier.get()) {
      if (target.javaInfo().isEmpty()) {
        continue;
      }
      JavaArtifactInfo javaInfo = target.javaInfo().get();
      if (projectDefinition.isIncluded(javaInfo.label())) {
        continue;
      }
      for (BuildArtifact genSrc : javaInfo.genSrcs()) {
        if (!JAVA_ARCHIVE_EXTENSIONS.contains(genSrc.getExtension())) {
          continue;
        }

        ProjectPath projectArtifact =
            update
                .artifactDirectory(ArtifactDirectories.DEFAULT)
                .addIfNewer(genSrc.artifactPath(), genSrc, target.buildContext())
                .orElse(null);

        if (projectArtifact != null) {
          srcJarInnerPathFinder
              .findInnerJarPaths(
                  cachedArtifactProvider.apply(genSrc, ArtifactDirectories.DEFAULT),
                  EMPTY_PACKAGE_PREFIXES_ONLY,
                  genSrc.artifactPath().toString())
              .stream()
              .map(JarPath::path)
              .map(projectArtifact::withInnerJarPath)
              .map(ProjectPath::toProto)
              .map(LibrarySource.newBuilder()::setSrcjar)
              .forEach(update.library(JAVA_DEPS_LIB_NAME)::addSources);
        }
      }
    }
  }
}
