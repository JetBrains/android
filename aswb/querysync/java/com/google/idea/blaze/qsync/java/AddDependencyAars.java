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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata;
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata.Extractor;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactDirectories;
import com.google.idea.blaze.qsync.deps.ArtifactDirectoryBuilder;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.AarResPackage;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto.ExternalAndroidLibrary;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact.ArtifactTransform;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

/**
 * Adds external {@code .aar} files to the project proto as {@link ExternalAndroidLibrary}s. This
 * allows resources references to external libraries to be resolved in Android Studio.
 */
public class AddDependencyAars implements ProjectProtoUpdateOperation {

  private final ProjectDefinition projectDefinition;
  private final Extractor<AarResPackage> aarPackageNameMetadata;

  public AddDependencyAars(
      ProjectDefinition projectDefinition, Extractor<AarResPackage> aarPackageNameMetadata) {
    this.projectDefinition = projectDefinition;
    this.aarPackageNameMetadata = aarPackageNameMetadata;
  }

  private ImmutableList<BuildArtifact> getDependencyAars(TargetBuildInfo target) {
    if (target.javaInfo().isEmpty()) {
      return ImmutableList.of();
    }
    JavaArtifactInfo javaInfo = target.javaInfo().get();
    if (projectDefinition.isIncluded(javaInfo.label())) {
      return ImmutableList.of();
    }
    return javaInfo.ideAars();
  }

  public ImmutableSetMultimap<BuildArtifact, ArtifactMetadata.Extractor<?>> getRequiredArtifacts(
      TargetBuildInfo forTarget) {
    return getDependencyAars(forTarget).stream()
        .collect(
            ImmutableSetMultimap.toImmutableSetMultimap(
                Function.identity(), unused -> aarPackageNameMetadata));
  }

  @Override
  public void update(ProjectProtoUpdate update, ArtifactTracker.State artifactState)
      throws BuildException {
    ArtifactDirectoryBuilder aarDir = null;
    for (TargetBuildInfo target : artifactState.depsMap().values()) {
      for (BuildArtifact aar : getDependencyAars(target)) {
        if (aarDir == null) {
          aarDir = update.artifactDirectory(ArtifactDirectories.DEFAULT);
        }
        Optional<String> packageName =
            aar.getMetadata(AarResPackage.class).map(AarResPackage::name);
        ProjectPath dest =
            aarDir
                .addIfNewer(aar.artifactPath(), aar, target.buildContext(), ArtifactTransform.UNZIP)
                .orElse(null);
        if (dest != null) {
          ExternalAndroidLibrary.Builder lib =
              ExternalAndroidLibrary.newBuilder()
                  .setName(aar.artifactPath().toString().replace('/', '_'))
                  .setLocation(dest.toProto())
                  .setManifestFile(dest.resolveChild(Path.of("AndroidManifest.xml")).toProto())
                  .setResFolder(dest.resolveChild(Path.of("res")).toProto())
                  .setSymbolFile(dest.resolveChild(Path.of("R.txt")).toProto());
          packageName.ifPresent(lib::setPackageName);
          update.workspaceModule().addAndroidExternalLibraries(lib);
        }
      }
    }
  }
}
