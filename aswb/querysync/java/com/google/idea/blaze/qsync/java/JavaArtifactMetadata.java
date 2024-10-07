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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata;
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.JarPath;
import java.nio.file.Path;
import javax.annotation.Nullable;

/** Classes for java artifact metadata, and conversion to/from proto format. */
public interface JavaArtifactMetadata {

  /** Package name as read from the {@code package} statement in a Java or Kotlin source file. */
  record JavaSourcePackage(String name) implements ArtifactMetadata {

    @Override
    public ArtifactTrackerProto.Metadata toProto() {
      return ArtifactTrackerProto.Metadata.newBuilder()
          .setJavaSourcePackage(ArtifactTrackerProto.JavaSourcePackage.newBuilder().setName(name))
          .build();
    }
  }

  /** Paths within a {@code .srcjar} file that correspond to the root java package. */
  record SrcJarJavaPackageRoots(ImmutableSet<Path> roots) implements ArtifactMetadata {

    @Override
    public ArtifactTrackerProto.Metadata toProto() {
      return ArtifactTrackerProto.Metadata.newBuilder()
          .setSrcjarJavaPackageRoots(
              ArtifactTrackerProto.SrcjarJavaPackageRoots.newBuilder()
                  .addAllPath(roots.stream().map(Path::toString).toList()))
          .build();
    }
  }

  /** Paths within a {@code .srcjar} file and their corresponding java package. */
  record SrcJarPrefixedJavaPackageRoots(ImmutableSet<JarPath> paths) implements ArtifactMetadata {

    @Override
    public ArtifactTrackerProto.Metadata toProto() {
      return ArtifactTrackerProto.Metadata.newBuilder()
          .setSrcjarPrefixedJavaPackageRoots(
              ArtifactTrackerProto.SrcjarPrefixedJavaPackageRoots.newBuilder()
                  .putAllPathToPackage(
                      paths.stream()
                          .collect(
                              ImmutableMap.toImmutableMap(
                                  p -> p.path().toString(), JarPath::packagePrefix))))
          .build();
    }
  }

  /** Package name as read from an {@code AndroidManifest.xml} file within an {@code .aar} file. */
  record AarResPackage(String name) implements ArtifactMetadata {

    @Override
    public ArtifactTrackerProto.Metadata toProto() {
      return ArtifactTrackerProto.Metadata.newBuilder()
          .setAarPackage(ArtifactTrackerProto.AarPackage.newBuilder().setName(name))
          .build();
    }
  }

  class Factory implements ArtifactMetadata.Factory {

    @Override
    @Nullable
    public ArtifactMetadata create(ArtifactTrackerProto.Metadata proto) {
      return switch (proto.getMetadataCase()) {
        case JAVA_SOURCE_PACKAGE -> new JavaSourcePackage(proto.getJavaSourcePackage().getName());
        case SRCJAR_JAVA_PACKAGE_ROOTS ->
            new SrcJarJavaPackageRoots(
                proto.getSrcjarJavaPackageRoots().getPathList().stream()
                    .map(Path::of)
                    .collect(toImmutableSet()));
        case SRCJAR_PREFIXED_JAVA_PACKAGE_ROOTS ->
            new SrcJarPrefixedJavaPackageRoots(
                proto.getSrcjarPrefixedJavaPackageRoots().getPathToPackageMap().entrySet().stream()
                    .map(e -> new JarPath(Path.of(e.getKey()), e.getValue()))
                    .collect(toImmutableSet()));
        case AAR_PACKAGE -> new AarResPackage(proto.getAarPackage().getName());
        default -> null;
      };
    }
  }
}
