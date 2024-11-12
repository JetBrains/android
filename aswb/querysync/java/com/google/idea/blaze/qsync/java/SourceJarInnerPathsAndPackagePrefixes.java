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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.AllowPackagePrefixes.ALLOW_NON_EMPTY_PACKAGE_PREFIXES;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactMetadata;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.JarPath;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/** The source roots from a srcjar file, along with the java package corresponding to each root. */
public class SourceJarInnerPathsAndPackagePrefixes implements ArtifactMetadata {

  private final SrcJarInnerPathFinder jarPathFinder;

  public SourceJarInnerPathsAndPackagePrefixes(SrcJarInnerPathFinder jarPathFinder) {
    this.jarPathFinder = jarPathFinder;
  }

  @Override
  public String key() {
    return "SrcJarInnerPathsAndPackagePrefixes";
  }

  @Override
  public String extract(CachedArtifact buildArtifact, Object nameForLogs) throws BuildException {

    return jarPathFinder
        .findInnerJarPaths(buildArtifact, ALLOW_NON_EMPTY_PACKAGE_PREFIXES, nameForLogs)
        .stream()
        .map(jarPath -> String.format("%s=%s", jarPath.path(), jarPath.packagePrefix()))
        .collect(Collectors.joining(";"));
  }

  @VisibleForTesting
  public ImmutableList<JarPath> toJarPaths(String metadata) {
    if (Strings.isNullOrEmpty(metadata)) {
      return ImmutableList.of(JarPath.create("", ""));
    }
    return Splitter.on(';')
        .splitToStream(metadata)
        .map(this::fromString)
        .collect(toImmutableList());
  }

  private JarPath fromString(String jarPath) {
    List<String> parts = Splitter.on('=').limit(2).splitToList(jarPath);
    return new JarPath(Path.of(parts.get(0)), parts.get(1));
  }

  public ImmutableList<JarPath> from(TargetBuildInfo target, BuildArtifact artifact) {
    return toJarPaths(target.getMetadata(artifact, key()));
  }
}
