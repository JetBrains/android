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
import static com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.AllowPackagePrefixes.EMPTY_PACKAGE_PREFIXES_ONLY;

import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata;
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.SrcJarJavaPackageRoots;
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.JarPath;

/**
 * Extracts the source roots from a srcjar file.
 *
 * <p>This differs from {@link SrcJarPrefixedPackageRootsExtractor} in that each root corresponds to
 * the empty package prefix.
 */
public class SrcJarPackageRootsExtractor
    implements ArtifactMetadata.Extractor<SrcJarJavaPackageRoots> {

  private final SrcJarInnerPathFinder jarPathFinder;

  public SrcJarPackageRootsExtractor(SrcJarInnerPathFinder jarPathFinder) {
    this.jarPathFinder = jarPathFinder;
  }

  @Override
  public SrcJarJavaPackageRoots extractFrom(CachedArtifact buildArtifact, Object nameForLogs) {
    return new SrcJarJavaPackageRoots(
        jarPathFinder
            .findInnerJarPaths(buildArtifact, EMPTY_PACKAGE_PREFIXES_ONLY, nameForLogs)
            .stream()
            .map(JarPath::path)
            .collect(toImmutableSet()));
  }

  @Override
  public Class<SrcJarJavaPackageRoots> metadataClass() {
    return SrcJarJavaPackageRoots.class;
  }
}
