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
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata;
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata.Extractor;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactDirectories;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.SrcJarPrefixedJavaPackageRoots;
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.JarPath;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact.ArtifactTransform;
import com.google.idea.blaze.qsync.project.TestSourceGlobMatcher;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Adds in-project generated {@code .srcjar} files to the project proto. This allows these sources
 * to be resolved and viewed.
 */
public class AddProjectGenSrcJars implements ProjectProtoUpdateOperation {

  private final ProjectDefinition projectDefinition;
  private final Extractor<SrcJarPrefixedJavaPackageRoots> srcJarPathMetadata;
  private final TestSourceGlobMatcher testSourceMatcher;

  public AddProjectGenSrcJars(
      ProjectDefinition projectDefinition,
      Extractor<SrcJarPrefixedJavaPackageRoots> srcJarPathMetadata) {
    this.projectDefinition = projectDefinition;
    this.srcJarPathMetadata = srcJarPathMetadata;
    testSourceMatcher = TestSourceGlobMatcher.create(projectDefinition);
  }

  private Stream<BuildArtifact> getProjectGenSrcJars(TargetBuildInfo target) {
    if (target.javaInfo().isEmpty()) {
      return Stream.empty();
    }
    JavaArtifactInfo javaInfo = target.javaInfo().get();
    if (!projectDefinition.isIncluded(javaInfo.label())) {
      return Stream.empty();
    }
    return javaInfo.genSrcs().stream()
        .filter(genSrc -> JAVA_ARCHIVE_EXTENSIONS.contains(genSrc.getExtension()));
  }

  @Override
  public ImmutableSetMultimap<BuildArtifact, ArtifactMetadata.Extractor<?>> getRequiredArtifacts(
      TargetBuildInfo forTarget) {
    return getProjectGenSrcJars(forTarget)
        .collect(
            ImmutableSetMultimap.toImmutableSetMultimap(
                Function.identity(), unused -> srcJarPathMetadata));
  }

  @Override
  public void update(ProjectProtoUpdate update, ArtifactTracker.State artifactState) {
    for (TargetBuildInfo target : artifactState.depsMap().values()) {
      getProjectGenSrcJars(target)
          .forEach(
              genSrc -> {
                // a zip of generated sources
                ProjectPath added =
                    update
                        .artifactDirectory(ArtifactDirectories.DEFAULT)
                        .addIfNewer(
                            genSrc.artifactPath(),
                            genSrc,
                            target.buildContext(),
                            ArtifactTransform.STRIP_SUPPORTED_GENERATED_SOURCES)
                        .orElse(null);
                if (added != null) {
                  ProjectProto.ContentEntry.Builder genSrcJarContentEntry =
                      ProjectProto.ContentEntry.newBuilder().setRoot(added.toProto());

                  ImmutableSet<JarPath> packageRoots =
                      genSrc
                          .getMetadata(SrcJarPrefixedJavaPackageRoots.class)
                          .map(SrcJarPrefixedJavaPackageRoots::paths)
                          .orElse(ImmutableSet.of(JarPath.create("", "")));
                  for (JarPath innerPath : packageRoots) {

                    genSrcJarContentEntry.addSources(
                        ProjectProto.SourceFolder.newBuilder()
                            .setProjectPath(added.withInnerJarPath(innerPath.path()).toProto())
                            .setIsGenerated(true)
                            .setIsTest(testSourceMatcher.matches(genSrc.target().getPackage()))
                            .setPackagePrefix(innerPath.packagePrefix())
                            .build());
                  }
                  update.workspaceModule().addContentEntries(genSrcJarContentEntry.build());
                }
              });
    }
  }
}
