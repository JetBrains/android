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
package com.google.idea.blaze.qsync.deps;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.common.Interners;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.artifacts.DigestMap;
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass.CcTargetInfo;
import com.google.idea.blaze.qsync.project.ProjectPath;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * C/C++ compilation information. This stores information required to compile C or C++ targets. The
 * information is extracted from the build at build deps time.
 */
@AutoValue
public abstract class CcCompilationInfo {
  public abstract Label target();

  public abstract ImmutableList<String> defines();

  public abstract ImmutableList<ProjectPath> includeDirectories();

  public abstract ImmutableList<ProjectPath> quoteIncludeDirectories();

  public abstract ImmutableList<ProjectPath> systemIncludeDirectories();

  public abstract ImmutableList<ProjectPath> frameworkIncludeDirectories();

  public abstract ImmutableList<BuildArtifact> genHeaders();

  public abstract String toolchainId();

  static CcCompilationInfo.Builder builder() {
    return new AutoValue_CcCompilationInfo.Builder();
  }

  /**
   * There is an inconsistency in bazel between the output paths we get given via the BES (which the
   * digest map is derived from), and the paths given to us by the CC compilation API:
   *
   * <ul>
   *   <li>The BES paths do not contain the {@code bazel-out} component
   *   <li>The cc compilation API does contain {@code bazel-out}, both in the include path flags and
   *       in the list of generated headers.
   * </ul>
   *
   * To workaround this, we strip the {@code bazel-out} prefix when looking up generated headers in
   * the digest map to ensure we can find them.
   */
  static DigestMap stripBazelOutPrefix(DigestMap digestMap) {
    return new DigestMap() {
      @Override
      public Optional<String> digestForArtifactPath(Path path, Label fromTarget) {
        return digestMap.digestForArtifactPath(stripBazelOutPrefix(path), fromTarget);
      }

      @Override
      public Iterator<Path> directoryContents(Path directory) {
        return digestMap.directoryContents(stripBazelOutPrefix(directory));
      }
    };
  }

  static Path stripBazelOutPrefix(Path path) {
    if (path.startsWith("bazel-out") || path.startsWith("blaze-out")) {
      return Interners.PATH.intern(path.getName(0).relativize(path));
    }
    return path;
  }

  public static CcCompilationInfo create(CcTargetInfo targetInfo, DigestMap digestMap) {
    Label target = Label.of(targetInfo.getLabel());
    return builder()
        .target(target)
        .defines(ImmutableList.copyOf(targetInfo.getDefinesList()))
        .includeDirectories(
            targetInfo.getIncludeDirectoriesList().stream()
                .map(ArtifactDirectories::forCcInclude)
                .collect(toImmutableList()))
        .quoteIncludeDirectories(
            targetInfo.getQuoteIncludeDirectoriesList().stream()
                .map(ArtifactDirectories::forCcInclude)
                .collect(toImmutableList()))
        .systemIncludeDirectories(
            targetInfo.getSystemIncludeDirectoriesList().stream()
                .map(ArtifactDirectories::forCcInclude)
                .collect(toImmutableList()))
        .frameworkIncludeDirectories(
            targetInfo.getFrameworkIncludeDirectoriesList().stream()
                .map(ArtifactDirectories::forCcInclude)
                .collect(toImmutableList()))
        .genHeaders(
            BuildArtifact.fromProtos(
                targetInfo.getGenHdrsList(), stripBazelOutPrefix(digestMap), target))
        .toolchainId(targetInfo.getToolchainId())
        .build();
  }

  /** Builder for {@link CcCompilationInfo}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder target(Label value);

    public abstract Builder defines(List<String> value);

    public abstract Builder includeDirectories(List<ProjectPath> value);

    public abstract Builder quoteIncludeDirectories(List<ProjectPath> value);

    public abstract Builder systemIncludeDirectories(List<ProjectPath> value);

    public abstract Builder frameworkIncludeDirectories(List<ProjectPath> value);

    public abstract Builder genHeaders(List<BuildArtifact> value);

    public abstract Builder toolchainId(String value);

    public abstract CcCompilationInfo build();
  }
}
