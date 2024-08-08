/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.ideinfo;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.devtools.intellij.aspect.Common;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.intellij.openapi.util.text.StringUtil;
import java.nio.file.Paths;
import java.util.List;

/** Represents a blaze-produced artifact. */
public final class ArtifactLocation
    implements ProtoWrapper<Common.ArtifactLocation>, Comparable<ArtifactLocation> {
  private final String rootExecutionPathFragment;
  private final String relativePath;
  private final boolean isSource;
  private final boolean isExternal;

  private ArtifactLocation(
      String rootExecutionPathFragment, String relativePath, boolean isSource, boolean isExternal) {
    this.rootExecutionPathFragment = rootExecutionPathFragment;
    this.relativePath = relativePath;
    this.isSource = isSource;
    this.isExternal = isExternal;
  }

  public static ArtifactLocation fromProto(Common.ArtifactLocation proto) {
    proto = fixProto(proto);
    return ProjectDataInterner.intern(
        new ArtifactLocation(
            proto.getRootExecutionPathFragment().intern(),
            proto.getRelativePath(),
            proto.getIsSource(),
            proto.getIsExternal()));
  }

  private static Common.ArtifactLocation fixProto(Common.ArtifactLocation proto) {
    if (!proto.getIsNewExternalVersion() && proto.getIsExternal()) {
      String relativePath = proto.getRelativePath();
      String rootExecutionPathFragment = proto.getRootExecutionPathFragment();
      // fix up incorrect paths created with older aspect version
      // Note: bazel always uses the '/' separator here, even on windows.
      List<String> components = StringUtil.split(relativePath, "/");
      if (components.size() > 2) {
        relativePath = Joiner.on('/').join(components.subList(2, components.size()));
        String prefix = components.get(0) + "/" + components.get(1);
        rootExecutionPathFragment =
            rootExecutionPathFragment.isEmpty() ? prefix : rootExecutionPathFragment + "/" + prefix;
        return proto
            .toBuilder()
            .setRootExecutionPathFragment(rootExecutionPathFragment)
            .setRelativePath(relativePath)
            .build();
      }
    }
    return proto;
  }

  @Override
  public Common.ArtifactLocation toProto() {
    return Common.ArtifactLocation.newBuilder()
        .setRootExecutionPathFragment(rootExecutionPathFragment)
        .setRelativePath(relativePath)
        .setIsSource(isSource)
        .setIsExternal(isExternal)
        .build();
  }

  private String getRootExecutionPathFragment() {
    return rootExecutionPathFragment;
  }

  /**
   * The root-relative path. For external workspace artifacts, this is relative to the external
   * workspace root.
   */
  public String getRelativePath() {
    return relativePath;
  }

  public boolean isSource() {
    return isSource;
  }

  public boolean isExternal() {
    return isExternal;
  }

  public boolean isGenerated() {
    return !isSource();
  }

  /** Returns false for generated or external artifacts */
  public boolean isMainWorkspaceSourceArtifact() {
    return isSource() && !isExternal();
  }

  /** For main-workspace source artifacts, this is simply the workspace-relative path. */
  public String getExecutionRootRelativePath() {
    return Paths.get(getRootExecutionPathFragment(), getRelativePath()).toString();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for an artifact location */
  public static class Builder {
    String relativePath;
    String rootExecutionPathFragment = "";
    boolean isSource;
    boolean isExternal;

    @CanIgnoreReturnValue
    public Builder setRelativePath(String relativePath) {
      this.relativePath = relativePath;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setRootExecutionPathFragment(String rootExecutionPathFragment) {
      this.rootExecutionPathFragment = rootExecutionPathFragment;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setIsSource(boolean isSource) {
      this.isSource = isSource;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setIsExternal(boolean isExternal) {
      this.isExternal = isExternal;
      return this;
    }

    public static Builder copy(ArtifactLocation artifact) {
      return new Builder()
          .setRelativePath(artifact.getRelativePath())
          .setRootExecutionPathFragment(artifact.getRootExecutionPathFragment())
          .setIsSource(artifact.isSource())
          .setIsExternal(artifact.isExternal());
    }

    public ArtifactLocation build() {
      return new ArtifactLocation(rootExecutionPathFragment, relativePath, isSource, isExternal);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ArtifactLocation that = (ArtifactLocation) o;
    return Objects.equal(getRootExecutionPathFragment(), that.getRootExecutionPathFragment())
        && Objects.equal(getRelativePath(), that.getRelativePath())
        && Objects.equal(isSource(), that.isSource())
        && Objects.equal(isExternal(), that.isExternal());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        getRootExecutionPathFragment(), getRelativePath(), isSource(), isExternal());
  }

  @Override
  public String toString() {
    return getExecutionRootRelativePath();
  }

  @Override
  public int compareTo(ArtifactLocation o) {
    return ComparisonChain.start()
        .compare(getRootExecutionPathFragment(), o.getRootExecutionPathFragment())
        .compare(getRelativePath(), o.getRelativePath())
        .compareFalseFirst(isSource(), o.isSource())
        .compareFalseFirst(isExternal(), o.isExternal())
        .result();
  }
}
