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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata;
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata.Extractor;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactDirectories;
import com.google.idea.blaze.qsync.deps.ArtifactTracker.State;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.SrcJarJavaPackageRoots;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto.LibrarySource;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Adds generated {@code .srcjar} files from external dependencies to the {@code .dependencies}
 * library. This means that when navigating to these dependencies, we see the generated sources
 * rather than decompiled code.
 */
public class AddDependencyGenSrcsJars implements ProjectProtoUpdateOperation {

  private final ProjectDefinition projectDefinition;
  private final Extractor<SrcJarJavaPackageRoots> srcJarPathsMetadata;

  public AddDependencyGenSrcsJars(
      ProjectDefinition projectDefinition, Extractor<SrcJarJavaPackageRoots> srcJarPathsMetadata) {
    this.projectDefinition = projectDefinition;
    this.srcJarPathsMetadata = srcJarPathsMetadata;
  }

  private Stream<BuildArtifact> getDependencyGenSrcJars(TargetBuildInfo target) {
    if (target.javaInfo().isEmpty()) {
      return Stream.empty();
    }
    JavaArtifactInfo javaInfo = target.javaInfo().get();
    if (projectDefinition.isIncluded(javaInfo.label())) {
      return Stream.empty();
    }
    return javaInfo.genSrcs().stream()
        .filter(genSrc -> JAVA_ARCHIVE_EXTENSIONS.contains(genSrc.getExtension()));
  }

  @Override
  public ImmutableSetMultimap<BuildArtifact, ArtifactMetadata.Extractor<?>> getRequiredArtifacts(
      TargetBuildInfo forTarget) {
    return getDependencyGenSrcJars(forTarget)
        .collect(
            ImmutableSetMultimap.toImmutableSetMultimap(
                Function.identity(), unused -> srcJarPathsMetadata));
  }

  @Override
  public void update(ProjectProtoUpdate update, State artifactState) throws BuildException {
    for (TargetBuildInfo target : artifactState.depsMap().values()) {
      getDependencyGenSrcJars(target)
          .forEach(
              genSrc -> {
                ProjectPath projectArtifact =
                    update
                        .artifactDirectory(ArtifactDirectories.DEFAULT)
                        .addIfNewer(genSrc.artifactPath(), genSrc, target.buildContext())
                        .orElse(null);

                if (projectArtifact != null) {
                  genSrc
                      .getMetadata(SrcJarJavaPackageRoots.class)
                      .map(SrcJarJavaPackageRoots::roots)
                      .orElse(ImmutableSet.of(Path.of("")))
                      .stream()
                      .map(projectArtifact::withInnerJarPath)
                      .map(ProjectPath::toProto)
                      .map(LibrarySource.newBuilder()::setSrcjar)
                      .forEach(update.library(JAVA_DEPS_LIB_NAME)::addSources);
                }
              });
    }
  }
}
