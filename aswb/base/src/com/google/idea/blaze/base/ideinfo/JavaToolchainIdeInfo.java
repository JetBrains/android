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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Collection;
import java.util.Objects;

/** Represents the java_toolchain class */
public final class JavaToolchainIdeInfo
    implements ProtoWrapper<IntellijIdeInfo.JavaToolchainIdeInfo> {
  private final String sourceVersion;
  private final String targetVersion;
  private final ImmutableList<ArtifactLocation> javacJars;

  private JavaToolchainIdeInfo(
      String sourceVersion, String targetVersion, ImmutableList<ArtifactLocation> javacJars) {
    this.sourceVersion = sourceVersion;
    this.targetVersion = targetVersion;
    this.javacJars = javacJars;
  }

  static JavaToolchainIdeInfo fromProto(IntellijIdeInfo.JavaToolchainIdeInfo proto) {
    ImmutableList<ArtifactLocation> javacJars =
        proto.getJavacJarsList().stream()
            .map(ArtifactLocation::fromProto)
            .collect(toImmutableList());
    return new JavaToolchainIdeInfo(proto.getSourceVersion(), proto.getTargetVersion(), javacJars);
  }

  @Override
  public IntellijIdeInfo.JavaToolchainIdeInfo toProto() {
    return IntellijIdeInfo.JavaToolchainIdeInfo.newBuilder()
        .setSourceVersion(sourceVersion)
        .setTargetVersion(targetVersion)
        .addAllJavacJars(ProtoWrapper.mapToProtos(javacJars))
        .build();
  }

  public String getSourceVersion() {
    return sourceVersion;
  }

  public String getTargetVersion() {
    return targetVersion;
  }

  public ImmutableList<ArtifactLocation> getJavacJars() {
    return javacJars;
  }

  @Override
  public String toString() {
    return "JavaToolchainIdeInfo{"
        + "\n"
        + "  sourceVersion="
        + getSourceVersion()
        + "\n"
        + "  targetVersion="
        + getTargetVersion()
        + "\n"
        + "  javacJars="
        + getJavacJars()
        + "\n"
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    JavaToolchainIdeInfo that = (JavaToolchainIdeInfo) o;
    return Objects.equals(sourceVersion, that.sourceVersion)
        && Objects.equals(targetVersion, that.targetVersion)
        && Objects.equals(javacJars, that.javacJars);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceVersion, targetVersion, javacJars);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for java toolchain info */
  public static class Builder {
    String sourceVersion;
    String targetVersion;
    ImmutableList.Builder<ArtifactLocation> javacJars = ImmutableList.builder();

    @CanIgnoreReturnValue
    public Builder setSourceVersion(String sourceVersion) {
      this.sourceVersion = sourceVersion;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setTargetVersion(String targetVersion) {
      this.targetVersion = targetVersion;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addJavacJars(Collection<ArtifactLocation> javacJars) {
      this.javacJars.addAll(javacJars);
      return this;
    }

    public JavaToolchainIdeInfo build() {
      return new JavaToolchainIdeInfo(sourceVersion, targetVersion, javacJars.build());
    }
  }
}
