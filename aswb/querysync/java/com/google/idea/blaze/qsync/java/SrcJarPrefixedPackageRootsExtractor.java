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

import static com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.AllowPackagePrefixes.ALLOW_NON_EMPTY_PACKAGE_PREFIXES;

import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata;
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.SrcJarPrefixedJavaPackageRoots;

/**
 * Extracts the source roots from a srcjar file, along with the java package corresponding to each
 * root.
 */
public class SrcJarPrefixedPackageRootsExtractor
    implements ArtifactMetadata.Extractor<SrcJarPrefixedJavaPackageRoots> {

  private final SrcJarInnerPathFinder jarPathFinder;

  public SrcJarPrefixedPackageRootsExtractor(SrcJarInnerPathFinder jarPathFinder) {
    this.jarPathFinder = jarPathFinder;
  }

  @Override
  public SrcJarPrefixedJavaPackageRoots extractFrom(
      CachedArtifact buildArtifact, Object nameForLogs) {

    return new SrcJarPrefixedJavaPackageRoots(
        jarPathFinder.findInnerJarPaths(
            buildArtifact, ALLOW_NON_EMPTY_PACKAGE_PREFIXES, nameForLogs));
  }

  @Override
  public Class<SrcJarPrefixedJavaPackageRoots> metadataClass() {
    return SrcJarPrefixedJavaPackageRoots.class;
  }
}
