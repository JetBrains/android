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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactMetadata;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.JarPath;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * The source roots from a srcjar file.
 *
 * <p>This differs from {@link SourceJarInnerPathsAndPackagePrefixes} in that each root corresponds
 * to the empty package prefix.
 */
public class SourceJarInnerPackageRoots implements ArtifactMetadata {

  private final SrcJarInnerPathFinder jarPathFinder;

  public SourceJarInnerPackageRoots(SrcJarInnerPathFinder jarPathFinder) {
    this.jarPathFinder = jarPathFinder;
  }

  @Override
  public String key() {
    return "SrcJarInnerPackageRoots";
  }

  @Override
  public String extract(CachedArtifact buildArtifact, Object nameForLogs) throws BuildException {
    return jarPathFinder
        .findInnerJarPaths(buildArtifact, EMPTY_PACKAGE_PREFIXES_ONLY, nameForLogs)
        .stream()
        .map(JarPath::path)
        .map(Path::toString)
        .collect(Collectors.joining(";"));
  }

  public ImmutableList<JarPath> toJarPaths(String metadata) {
    if (metadata == null) {
      return ImmutableList.of(JarPath.create("", ""));
    }
    return Splitter.on(';')
        .splitToStream(metadata)
        .map(p -> JarPath.create(p, ""))
        .collect(ImmutableList.toImmutableList());
  }

  public ImmutableList<JarPath> from(TargetBuildInfo target, BuildArtifact artifact) {
    return toJarPaths(target.getMetadata(artifact, key()));
  }
}
