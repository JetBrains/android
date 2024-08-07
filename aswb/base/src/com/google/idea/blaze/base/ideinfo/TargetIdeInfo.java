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
import java.util.Arrays;
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
  private final ImmutableSet<ArtifactLocation> sources;
  @Nullable private final CIdeInfo cIdeInfo;
  @Nullable private final CToolchainIdeInfo cToolchainIdeInfo;
  @Nullable private final JavaIdeInfo javaIdeInfo;
  @Nullable private final AndroidIdeInfo androidIdeInfo;
  @Nullable private final AndroidSdkIdeInfo androidSdkIdeInfo;
  @Nullable private final AndroidAarIdeInfo androidAarIdeInfo;
  @Nullable private final AndroidInstrumentationInfo androidInstrumentationInfo;
  @Nullable private final PyIdeInfo pyIdeInfo;
  @Nullable private final GoIdeInfo goIdeInfo;
  @Nullable private final JsIdeInfo jsIdeInfo;
  @Nullable private final TsIdeInfo tsIdeInfo;
  @Nullable private final DartIdeInfo dartIdeInfo;
  @Nullable private final TestIdeInfo testIdeInfo;
  @Nullable private final JavaToolchainIdeInfo javaToolchainIdeInfo;
  @Nullable private final KotlinToolchainIdeInfo kotlinToolchainIdeInfo;
  @Nullable private final Long syncTimeMillis;

  private TargetIdeInfo(
      TargetKey key,
      Kind kind,
      @Nullable ArtifactLocation buildFile,
      ImmutableList<Dependency> dependencies,
      ImmutableList<String> tags,
      ImmutableSet<ArtifactLocation> sources,
      @Nullable CIdeInfo cIdeInfo,
      @Nullable CToolchainIdeInfo cToolchainIdeInfo,
      @Nullable JavaIdeInfo javaIdeInfo,
      @Nullable AndroidIdeInfo androidIdeInfo,
      @Nullable AndroidSdkIdeInfo androidSdkIdeInfo,
      @Nullable AndroidAarIdeInfo androidAarIdeInfo,
      @Nullable AndroidInstrumentationInfo androidInstrumentationInfo,
      @Nullable PyIdeInfo pyIdeInfo,
      @Nullable GoIdeInfo goIdeInfo,
      @Nullable JsIdeInfo jsIdeInfo,
      @Nullable TsIdeInfo tsIdeInfo,
      @Nullable DartIdeInfo dartIdeInfo,
      @Nullable TestIdeInfo testIdeInfo,
      @Nullable JavaToolchainIdeInfo javaToolchainIdeInfo,
      @Nullable KotlinToolchainIdeInfo kotlinToolchainIdeInfo,
      @Nullable Long syncTimeMillis) {
    this.key = key;
    this.kind = kind;
    this.buildFile = buildFile;
    this.dependencies = dependencies;
    this.tags = tags;
    this.sources = sources;
    this.cIdeInfo = cIdeInfo;
    this.cToolchainIdeInfo = cToolchainIdeInfo;
    this.javaIdeInfo = javaIdeInfo;
    this.androidIdeInfo = androidIdeInfo;
    this.androidSdkIdeInfo = androidSdkIdeInfo;
    this.androidAarIdeInfo = androidAarIdeInfo;
    this.androidInstrumentationInfo = androidInstrumentationInfo;
    this.pyIdeInfo = pyIdeInfo;
    this.goIdeInfo = goIdeInfo;
    this.jsIdeInfo = jsIdeInfo;
    this.tsIdeInfo = tsIdeInfo;
    this.dartIdeInfo = dartIdeInfo;
    this.testIdeInfo = testIdeInfo;
    this.javaToolchainIdeInfo = javaToolchainIdeInfo;
    this.kotlinToolchainIdeInfo = kotlinToolchainIdeInfo;
    this.syncTimeMillis = syncTimeMillis;
  }

  @Nullable
  public static TargetIdeInfo fromProto(IntellijIdeInfo.TargetIdeInfo proto) {
    return fromProto(proto, /* syncTimeOverride= */ null);
  }

  @Nullable
  public static TargetIdeInfo fromProto(
      IntellijIdeInfo.TargetIdeInfo proto, @Nullable Instant syncTimeOverride) {
    TargetKey key = proto.hasKey() ? TargetKey.fromProto(proto.getKey()) : null;
    Kind kind = Kind.fromProto(proto);
    if (key == null || kind == null) {
      return null;
    }
    ImmutableSet.Builder<ArtifactLocation> sourcesBuilder = ImmutableSet.builder();
    CIdeInfo cIdeInfo = null;
    if (proto.hasCIdeInfo()) {
      cIdeInfo = CIdeInfo.fromProto(proto.getCIdeInfo());
      sourcesBuilder.addAll(cIdeInfo.getSources());
      sourcesBuilder.addAll(cIdeInfo.getHeaders());
      sourcesBuilder.addAll(cIdeInfo.getTextualHeaders());
    }
    JavaIdeInfo javaIdeInfo = null;
    if (proto.hasJavaIdeInfo()) {
      javaIdeInfo = JavaIdeInfo.fromProto(proto.getJavaIdeInfo());
      sourcesBuilder.addAll(
          ProtoWrapper.map(proto.getJavaIdeInfo().getSourcesList(), ArtifactLocation::fromProto));
    }
    PyIdeInfo pyIdeInfo = null;
    if (proto.hasPyIdeInfo()) {
      pyIdeInfo = PyIdeInfo.fromProto(proto.getPyIdeInfo());
      sourcesBuilder.addAll(pyIdeInfo.getSources());
    }
    GoIdeInfo goIdeInfo = null;
    if (proto.hasGoIdeInfo()) {
      goIdeInfo = GoIdeInfo.fromProto(proto.getGoIdeInfo(), key.getLabel(), kind);
      sourcesBuilder.addAll(goIdeInfo.getSources());
    }
    JsIdeInfo jsIdeInfo = null;
    if (proto.hasJsIdeInfo()) {
      jsIdeInfo = JsIdeInfo.fromProto(proto.getJsIdeInfo());
      sourcesBuilder.addAll(jsIdeInfo.getSources());
    }
    TsIdeInfo tsIdeInfo = null;
    if (proto.hasTsIdeInfo()) {
      tsIdeInfo = TsIdeInfo.fromProto(proto.getTsIdeInfo());
      sourcesBuilder.addAll(tsIdeInfo.getSources());
    }
    DartIdeInfo dartIdeInfo = null;
    if (proto.hasDartIdeInfo()) {
      dartIdeInfo = DartIdeInfo.fromProto(proto.getDartIdeInfo());
      sourcesBuilder.addAll(dartIdeInfo.getSources());
    }
    Long syncTime =
        syncTimeOverride != null
            ? Long.valueOf(syncTimeOverride.toEpochMilli())
            : proto.getSyncTimeMillis() == 0 ? null : proto.getSyncTimeMillis();
    return new TargetIdeInfo(
        key,
        kind,
        proto.hasBuildFileArtifactLocation()
            ? ArtifactLocation.fromProto(proto.getBuildFileArtifactLocation())
            : null,
        ProtoWrapper.map(proto.getDepsList(), Dependency::fromProto),
        ProtoWrapper.internStrings(proto.getTagsList()),
        sourcesBuilder.build(),
        cIdeInfo,
        proto.hasCToolchainIdeInfo()
            ? CToolchainIdeInfo.fromProto(proto.getCToolchainIdeInfo())
            : null,
        javaIdeInfo,
        proto.hasAndroidIdeInfo() ? AndroidIdeInfo.fromProto(proto.getAndroidIdeInfo()) : null,
        proto.hasAndroidSdkIdeInfo()
            ? AndroidSdkIdeInfo.fromProto(proto.getAndroidSdkIdeInfo())
            : null,
        proto.hasAndroidAarIdeInfo()
            ? AndroidAarIdeInfo.fromProto(proto.getAndroidAarIdeInfo())
            : null,
        proto.hasAndroidInstrumentationInfo()
            ? AndroidInstrumentationInfo.fromProto(proto.getAndroidInstrumentationInfo())
            : null,
        pyIdeInfo,
        goIdeInfo,
        jsIdeInfo,
        tsIdeInfo,
        dartIdeInfo,
        proto.hasTestInfo() ? TestIdeInfo.fromProto(proto.getTestInfo()) : null,
        proto.hasJavaToolchainIdeInfo()
            ? JavaToolchainIdeInfo.fromProto(proto.getJavaToolchainIdeInfo())
            : null,
        proto.hasKtToolchainIdeInfo()
            ? KotlinToolchainIdeInfo.fromProto(proto.getKtToolchainIdeInfo())
            : null,
        syncTime);
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
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setCIdeInfo, cIdeInfo);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setCToolchainIdeInfo, cToolchainIdeInfo);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setJavaIdeInfo, javaIdeInfo);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setAndroidIdeInfo, androidIdeInfo);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setAndroidSdkIdeInfo, androidSdkIdeInfo);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setAndroidAarIdeInfo, androidAarIdeInfo);
    ProtoWrapper.unwrapAndSetIfNotNull(
        builder::setAndroidInstrumentationInfo, androidInstrumentationInfo);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setPyIdeInfo, pyIdeInfo);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setGoIdeInfo, goIdeInfo);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setJsIdeInfo, jsIdeInfo);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setTsIdeInfo, tsIdeInfo);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setDartIdeInfo, dartIdeInfo);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setTestInfo, testIdeInfo);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setJavaToolchainIdeInfo, javaToolchainIdeInfo);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setKtToolchainIdeInfo, kotlinToolchainIdeInfo);
    ProtoWrapper.setIfNotNull(builder::setSyncTimeMillis, syncTimeMillis);
    return builder.build();
  }

  /**
   * Updates this target's {@link #syncTimeMillis}. Returns this same {@link TargetIdeInfo} instance
   * if the sync time is unchanged.
   */
  public TargetIdeInfo updateSyncTime(Instant syncTime) {
    long syncTimeMillis = syncTime.toEpochMilli();
    if (Objects.equals(syncTimeMillis, this.syncTimeMillis)) {
      return this;
    }
    return new TargetIdeInfo(
        key,
        kind,
        buildFile,
        dependencies,
        tags,
        sources,
        cIdeInfo,
        cToolchainIdeInfo,
        javaIdeInfo,
        androidIdeInfo,
        androidSdkIdeInfo,
        androidAarIdeInfo,
        androidInstrumentationInfo,
        pyIdeInfo,
        goIdeInfo,
        jsIdeInfo,
        tsIdeInfo,
        dartIdeInfo,
        testIdeInfo,
        javaToolchainIdeInfo,
        kotlinToolchainIdeInfo,
        syncTimeMillis);
  }

  public TargetKey getKey() {
    return key;
  }

  public Kind getKind() {
    return kind;
  }

  @Nullable
  public ArtifactLocation getBuildFile() {
    return buildFile;
  }

  public ImmutableList<Dependency> getDependencies() {
    return dependencies;
  }

  public ImmutableList<String> getTags() {
    return tags;
  }

  public ImmutableSet<ArtifactLocation> getSources() {
    return sources;
  }

  @Nullable
  public CIdeInfo getcIdeInfo() {
    return cIdeInfo;
  }

  @Nullable
  public CToolchainIdeInfo getcToolchainIdeInfo() {
    return cToolchainIdeInfo;
  }

  @Nullable
  public JavaIdeInfo getJavaIdeInfo() {
    return javaIdeInfo;
  }

  @Nullable
  public AndroidIdeInfo getAndroidIdeInfo() {
    return androidIdeInfo;
  }

  @Nullable
  public AndroidSdkIdeInfo getAndroidSdkIdeInfo() {
    return androidSdkIdeInfo;
  }

  @Nullable
  public AndroidAarIdeInfo getAndroidAarIdeInfo() {
    return androidAarIdeInfo;
  }

  @Nullable
  public AndroidInstrumentationInfo getAndroidInstrumentationInfo() {
    return androidInstrumentationInfo;
  }

  @Nullable
  public PyIdeInfo getPyIdeInfo() {
    return pyIdeInfo;
  }

  @Nullable
  public GoIdeInfo getGoIdeInfo() {
    return goIdeInfo;
  }

  @Nullable
  public JsIdeInfo getJsIdeInfo() {
    return jsIdeInfo;
  }

  @Nullable
  public TsIdeInfo getTsIdeInfo() {
    return tsIdeInfo;
  }

  @Nullable
  public DartIdeInfo getDartIdeInfo() {
    return dartIdeInfo;
  }

  @Nullable
  public TestIdeInfo getTestIdeInfo() {
    return testIdeInfo;
  }

  @Nullable
  public JavaToolchainIdeInfo getJavaToolchainIdeInfo() {
    return javaToolchainIdeInfo;
  }

  @Nullable
  public KotlinToolchainIdeInfo getKotlinToolchainIdeInfo() {
    return kotlinToolchainIdeInfo;
  }

  @Nullable
  public Instant getSyncTime() {
    return syncTimeMillis != null ? Instant.ofEpochMilli(syncTimeMillis) : null;
  }

  public TargetInfo toTargetInfo() {
    return TargetInfo.builder(getKey().getLabel(), getKind().getKindString())
        .setTestSize(getTestIdeInfo() != null ? getTestIdeInfo().getTestSize() : null)
        .setTestClass(getJavaIdeInfo() != null ? getJavaIdeInfo().getTestClass() : null)
        .setSyncTime(getSyncTime())
        .setSources(ImmutableList.copyOf(getSources()))
        .build();
  }

  @Override
  public String toString() {
    return getKey().toString();
  }

  /** Returns whether this rule is one of the kinds. */
  public boolean kindIsOneOf(Kind... kinds) {
    return kindIsOneOf(Arrays.asList(kinds));
  }

  /** Returns whether this rule is one of the kinds. */
  public boolean kindIsOneOf(Collection<Kind> kinds) {
    return kinds.contains(getKind());
  }

  public boolean isPlainTarget() {
    return getKey().isPlainTarget();
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
    private final ImmutableSet.Builder<ArtifactLocation> sources = ImmutableSet.builder();
    private CIdeInfo cIdeInfo;
    private CToolchainIdeInfo cToolchainIdeInfo;
    private JavaIdeInfo javaIdeInfo;
    private AndroidIdeInfo androidIdeInfo;
    private AndroidAarIdeInfo androidAarIdeInfo;
    private AndroidInstrumentationInfo androidInstrumentationInfo;
    private PyIdeInfo pyIdeInfo;
    private GoIdeInfo goIdeInfo;
    private JsIdeInfo jsIdeInfo;
    private TsIdeInfo tsIdeInfo;
    private DartIdeInfo dartIdeInfo;
    private TestIdeInfo testIdeInfo;
    private JavaToolchainIdeInfo javaToolchainIdeInfo;
    private KotlinToolchainIdeInfo kotlinToolchainIdeInfo;
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
    public Builder addSource(ArtifactLocation source) {
      this.sources.add(source);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addSource(ArtifactLocation.Builder source) {
      return addSource(source.build());
    }

    @CanIgnoreReturnValue
    public Builder setJavaInfo(JavaIdeInfo.Builder builder) {
      javaIdeInfo = builder.build();
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setCInfo(CIdeInfo.Builder cInfoBuilder) {
      this.cIdeInfo = cInfoBuilder.build();
      this.sources.addAll(cIdeInfo.getSources());
      this.sources.addAll(cIdeInfo.getHeaders());
      this.sources.addAll(cIdeInfo.getTextualHeaders());
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setCToolchainInfo(CToolchainIdeInfo.Builder info) {
      this.cToolchainIdeInfo = info.build();
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setAndroidInfo(AndroidIdeInfo.Builder androidInfo) {
      this.androidIdeInfo = androidInfo.build();
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setAndroidAarInfo(AndroidAarIdeInfo aarInfo) {
      this.androidAarIdeInfo = aarInfo;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setAndroidInstrumentationInfo(AndroidInstrumentationInfo instrumentationInfo) {
      this.androidInstrumentationInfo = instrumentationInfo;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setPyInfo(PyIdeInfo.Builder pyInfo) {
      this.pyIdeInfo = pyInfo.build();
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setGoInfo(GoIdeInfo.Builder goInfo) {
      this.goIdeInfo = goInfo.build();
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setJsInfo(JsIdeInfo.Builder jsInfo) {
      this.jsIdeInfo = jsInfo.build();
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setTsInfo(TsIdeInfo.Builder tsInfo) {
      this.tsIdeInfo = tsInfo.build();
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setDartInfo(DartIdeInfo.Builder dartInfo) {
      this.dartIdeInfo = dartInfo.build();
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setTestInfo(TestIdeInfo.Builder testInfo) {
      this.testIdeInfo = testInfo.build();
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setJavaToolchainIdeInfo(JavaToolchainIdeInfo.Builder javaToolchainIdeInfo) {
      this.javaToolchainIdeInfo = javaToolchainIdeInfo.build();
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setKotlinToolchainIdeInfo(KotlinToolchainIdeInfo.Builder toolchain) {
      this.kotlinToolchainIdeInfo = toolchain.build();
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
    public Builder addRuntimeDep(String s) {
      return addRuntimeDep(Label.create(s));
    }

    @CanIgnoreReturnValue
    public Builder addRuntimeDep(Label label) {
      this.dependencies.add(
          new Dependency(TargetKey.forPlainTarget(label), DependencyType.RUNTIME));
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
          sources.build(),
          cIdeInfo,
          cToolchainIdeInfo,
          javaIdeInfo,
          androidIdeInfo,
          null,
          androidAarIdeInfo,
          androidInstrumentationInfo,
          pyIdeInfo,
          goIdeInfo,
          jsIdeInfo,
          tsIdeInfo,
          dartIdeInfo,
          testIdeInfo,
          javaToolchainIdeInfo,
          kotlinToolchainIdeInfo,
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
        && Objects.equals(sources, that.sources)
        && Objects.equals(cIdeInfo, that.cIdeInfo)
        && Objects.equals(cToolchainIdeInfo, that.cToolchainIdeInfo)
        && Objects.equals(javaIdeInfo, that.javaIdeInfo)
        && Objects.equals(androidIdeInfo, that.androidIdeInfo)
        && Objects.equals(androidSdkIdeInfo, that.androidSdkIdeInfo)
        && Objects.equals(androidAarIdeInfo, that.androidAarIdeInfo)
        && Objects.equals(androidInstrumentationInfo, that.androidInstrumentationInfo)
        && Objects.equals(pyIdeInfo, that.pyIdeInfo)
        && Objects.equals(goIdeInfo, that.goIdeInfo)
        && Objects.equals(jsIdeInfo, that.jsIdeInfo)
        && Objects.equals(tsIdeInfo, that.tsIdeInfo)
        && Objects.equals(dartIdeInfo, that.dartIdeInfo)
        && Objects.equals(testIdeInfo, that.testIdeInfo)
        && Objects.equals(javaToolchainIdeInfo, that.javaToolchainIdeInfo)
        && Objects.equals(kotlinToolchainIdeInfo, that.kotlinToolchainIdeInfo)
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
        sources,
        cIdeInfo,
        cToolchainIdeInfo,
        javaIdeInfo,
        androidIdeInfo,
        androidSdkIdeInfo,
        androidAarIdeInfo,
        androidInstrumentationInfo,
        pyIdeInfo,
        goIdeInfo,
        jsIdeInfo,
        tsIdeInfo,
        dartIdeInfo,
        testIdeInfo,
        javaToolchainIdeInfo,
        kotlinToolchainIdeInfo,
        syncTimeMillis);
  }
}
