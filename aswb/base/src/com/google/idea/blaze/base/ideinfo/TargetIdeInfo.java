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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.Dependency.DependencyType;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import javax.annotation.Nullable;

/** Simple implementation of TargetIdeInfo. */
public final class TargetIdeInfo implements ProtoWrapper<IntellijIdeInfo.TargetIdeInfo> {
  private final TargetKey key;
  private final Kind kind;
  @Nullable private final ArtifactLocation buildFile;
  private final ImmutableList<Dependency> dependencies;
  private final ImmutableList<String> tags;
  @Nullable private final JavaIdeInfo javaIdeInfo;
  @Nullable private final AndroidInstrumentationInfo androidInstrumentationInfo;
  @Nullable private final TestIdeInfo testIdeInfo;
  @Nullable private final Long syncTimeMillis;

  private TargetIdeInfo(
      TargetKey key,
      Kind kind,
      @Nullable ArtifactLocation buildFile,
      ImmutableList<Dependency> dependencies,
      ImmutableList<String> tags,
      @Nullable JavaIdeInfo javaIdeInfo,
      @Nullable AndroidInstrumentationInfo androidInstrumentationInfo,
      @Nullable TestIdeInfo testIdeInfo,
      @Nullable Long syncTimeMillis) {
    this.key = key;
    this.kind = kind != null ? kind : Kind.UNKNOWN;
    this.buildFile = buildFile;
    this.dependencies = dependencies;
    this.tags = tags;
    this.javaIdeInfo = javaIdeInfo;
    this.androidInstrumentationInfo = androidInstrumentationInfo;
    this.testIdeInfo = testIdeInfo;
    this.syncTimeMillis = syncTimeMillis;
  }

  @Override
  public IntellijIdeInfo.TargetIdeInfo toProto() {
    IntellijIdeInfo.TargetIdeInfo.Builder builder =
        IntellijIdeInfo.TargetIdeInfo.newBuilder()
            .setKey(key.toProto())
            .setKindString(kind.getKindString())
            .addAllDeps(ProtoWrapper.mapToProtos(dependencies))
            .addAllTags(tags);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setBuildFileArtifactLocation, buildFile);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setJavaIdeInfo, javaIdeInfo);
    ProtoWrapper.unwrapAndSetIfNotNull(
        builder::setAndroidInstrumentationInfo, androidInstrumentationInfo);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setTestInfo, testIdeInfo);
    ProtoWrapper.setIfNotNull(builder::setSyncTimeMillis, syncTimeMillis);
    return builder.build();
  }

  public TargetKey getKey() {
    return key;
  }

  public Kind getKind() {
    return kind;
  }

  public ImmutableList<Dependency> getDependencies() {
    return dependencies;
  }

  public ImmutableList<String> getTags() {
    return tags;
  }

  @Nullable
  public JavaIdeInfo getJavaIdeInfo() {
    return javaIdeInfo;
  }

  @Nullable
  public AndroidInstrumentationInfo getAndroidInstrumentationInfo() {
    return androidInstrumentationInfo;
  }

  @Nullable
  public TestIdeInfo getTestIdeInfo() {
    return testIdeInfo;
  }

  @Nullable
  public Instant getSyncTime() {
    return syncTimeMillis != null ? Instant.ofEpochMilli(syncTimeMillis) : null;
  }

  public TargetInfo toTargetInfo() {
    return new TargetInfo(
        getKey().getLabel(),
        getKind().getKindString(),
        getTestIdeInfo() != null ? getTestIdeInfo().getTestSize() : null,
        getJavaIdeInfo() != null ? getJavaIdeInfo().getTestClass() : null,
        getSyncTime());
  }

  @Override
  public String toString() {
    return getKey().toString();
  }

  /** Returns whether this rule is one of the kinds. */
  public boolean kindIsOneOf(Collection<Kind> kinds) {
    return kinds.contains(getKind());
  }

  public static Builder builder() {
    return new Builder();
  }
  /** Builder for rule ide info */
  public static class Builder {
    private TargetKey key;
    private Kind kind;
    private ArtifactLocation buildFile;
    private final ImmutableList.Builder<Dependency> dependencies = ImmutableList.builder();
    private final ImmutableList.Builder<String> tags = ImmutableList.builder();
    private JavaIdeInfo javaIdeInfo;
    private TestIdeInfo testIdeInfo;
    private Long syncTime;

    @CanIgnoreReturnValue
    public Builder setLabel(String label) {
      return setLabel(Label.create(label));
    }

    @CanIgnoreReturnValue
    public Builder setLabel(Label label) {
      this.key = TargetKey.forPlainTarget(label);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setBuildFile(ArtifactLocation buildFile) {
      this.buildFile = buildFile;
      return this;
    }

    @VisibleForTesting
    @CanIgnoreReturnValue
    public Builder setKind(String kindString) {
      Kind kind = Preconditions.checkNotNull(Kind.fromRuleName(kindString));
      return setKind(kind);
    }

    @CanIgnoreReturnValue
    public Builder setKind(Kind kind) {
      this.kind = kind;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setJavaInfo(JavaIdeInfo.Builder builder) {
      javaIdeInfo = builder.build();
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setTestInfo(TestIdeInfo.Builder testInfo) {
      this.testIdeInfo = testInfo.build();
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addTag(String s) {
      this.tags.add(s);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addDependency(String s) {
      return addDependency(Label.create(s));
    }

    @CanIgnoreReturnValue
    public Builder addDependency(Label label) {
      this.dependencies.add(
          new Dependency(TargetKey.forPlainTarget(label), DependencyType.COMPILE_TIME));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setSyncTime(@Nullable Instant syncTime) {
      this.syncTime = syncTime != null ? syncTime.toEpochMilli() : null;
      return this;
    }

    public TargetIdeInfo build() {
      return new TargetIdeInfo(
          key,
          kind,
          buildFile,
          dependencies.build(),
          tags.build(),
          javaIdeInfo,
          null,
          testIdeInfo,
          syncTime);
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
    TargetIdeInfo that = (TargetIdeInfo) o;
    return Objects.equals(key, that.key)
        && kind == that.kind
        && Objects.equals(buildFile, that.buildFile)
        && Objects.equals(dependencies, that.dependencies)
        && Objects.equals(tags, that.tags)
        && Objects.equals(javaIdeInfo, that.javaIdeInfo)
        && Objects.equals(androidInstrumentationInfo, that.androidInstrumentationInfo)
        && Objects.equals(testIdeInfo, that.testIdeInfo)
        && Objects.equals(syncTimeMillis, that.syncTimeMillis);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        key,
        kind,
        buildFile,
        dependencies,
        tags,
        javaIdeInfo,
        androidInstrumentationInfo,
        testIdeInfo,
        syncTimeMillis);
  }
}