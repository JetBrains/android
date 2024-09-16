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

import com.google.common.base.Supplier;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactDirectories;
import com.google.idea.blaze.qsync.deps.ArtifactDirectoryBuilder;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto.ExternalAndroidLibrary;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact.ArtifactTransform;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Adds external {@code .aar} files to the project proto as {@link ExternalAndroidLibrary}s. This
 * allows resources references to external libraries to be resolved in Android Studio.
 */
public class AddDependencyAars implements ProjectProtoUpdateOperation {

  private final Supplier<ArtifactTracker.State> artifactStateSupplier;
  private final BuildArtifactCache buildCache;
  private final ProjectDefinition projectDefinition;
  private final AndroidManifestParser manifestParser;

  public AddDependencyAars(
      Supplier<ArtifactTracker.State> artifactStateSupplier,
      BuildArtifactCache buildCache,
      ProjectDefinition projectDefinition,
      AndroidManifestParser manifestParser) {
    this.artifactStateSupplier = artifactStateSupplier;
    this.buildCache = buildCache;
    this.projectDefinition = projectDefinition;
    this.manifestParser = manifestParser;
  }

  @Override
  public void update(ProjectProtoUpdate update) throws BuildException {
    ArtifactDirectoryBuilder aarDir = null;
    for (TargetBuildInfo target : artifactStateSupplier.get().depsMap().values()) {
      if (target.javaInfo().isEmpty()) {
        continue;
      }
      JavaArtifactInfo javaInfo = target.javaInfo().get();
      if (projectDefinition.isIncluded(javaInfo.label())) {
        continue;
      }
      for (BuildArtifact aar : javaInfo.ideAars()) {
        if (aarDir == null) {
          aarDir = update.artifactDirectory(ArtifactDirectories.DEFAULT);
        }
        Optional<String> packageName = readPackageFromAarManifest(aar);
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

  public Optional<String> readPackageFromAarManifest(BuildArtifact aar) throws BuildException {
    try (ZipFile zip = aar.blockingGetFrom(buildCache).openAsZipFile()) {

      ZipEntry entry = zip.getEntry("AndroidManifest.xml");
      if (entry != null) {
        return Optional.ofNullable(manifestParser.readPackageNameFrom(zip.getInputStream(entry)));
      }
    } catch (IOException e) {
      throw new BuildException(
          String.format(
              "Failed to read aar file %s (built by %s)", aar.artifactPath(), aar.target()),
          e);
    }
    throw new BuildException(
        String.format(
            "Failed to find AndroidManifest.xml in  %s (built by %s)",
            aar.artifactPath(), aar.target()));
  }
}
