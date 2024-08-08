/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.model.primitives.Label;
import java.util.Objects;

/** Kotlin toolchain information. */
public final class KotlinToolchainIdeInfo
    implements ProtoWrapper<IntellijIdeInfo.KotlinToolchainIdeInfo> {
  private final String languageVersion;
  private final ImmutableList<Label> sdkTargets;
  /*
   * default kotlinc flags to use in all compilations, as specified in
   * third_party/bazel_rules/rules_kotlin/toolchains/kotlin_jvm/toolchain.bzl. Note that this cannot
   * collect compiler flags that specific for individual target.
   */
  private final ImmutableList<String> kotlinCompilerCommonFlags;

  private KotlinToolchainIdeInfo(
      String languageVersion,
      ImmutableList<Label> sdkTargets,
      ImmutableList<String> kotlinCompilerCommonFlags) {
    this.languageVersion = languageVersion;
    this.sdkTargets = sdkTargets;
    this.kotlinCompilerCommonFlags = kotlinCompilerCommonFlags;
  }

  static KotlinToolchainIdeInfo fromProto(IntellijIdeInfo.KotlinToolchainIdeInfo proto) {
    return new KotlinToolchainIdeInfo(
        proto.getLanguageVersion(),
        ProtoWrapper.map(proto.getSdkLibraryTargetsList(), Label::fromProto),
        ProtoWrapper.internStrings(proto.getKotlinCompilerCommonFlagsList()));
  }

  @Override
  public IntellijIdeInfo.KotlinToolchainIdeInfo toProto() {
    return IntellijIdeInfo.KotlinToolchainIdeInfo.newBuilder()
        .setLanguageVersion(languageVersion)
        .addAllSdkLibraryTargets(ProtoWrapper.mapToProtos(sdkTargets))
        .addAllKotlinCompilerCommonFlags(kotlinCompilerCommonFlags)
        .build();
  }

  public String getLanguageVersion() {
    return languageVersion;
  }

  public ImmutableList<Label> getSdkTargets() {
    return sdkTargets;
  }

  public ImmutableList<String> getKotlinCompilerCommonFlags() {
    return kotlinCompilerCommonFlags;
  }

  @Override
  public String toString() {
    return "KotlinToolchainIdeInfo{"
        + "\n"
        + "  languageVersion="
        + getLanguageVersion()
        + "\n"
        + "  sdkTargets="
        + getSdkTargets()
        + "\n"
        + "  kotlinCompilerCommonFlags="
        + getKotlinCompilerCommonFlags()
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
    KotlinToolchainIdeInfo that = (KotlinToolchainIdeInfo) o;
    return Objects.equals(languageVersion, that.languageVersion)
        && Objects.equals(sdkTargets, that.sdkTargets)
        && Objects.equals(kotlinCompilerCommonFlags, that.kotlinCompilerCommonFlags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(languageVersion, sdkTargets, kotlinCompilerCommonFlags);
  }

  public static KotlinToolchainIdeInfo.Builder builder() {
    return new KotlinToolchainIdeInfo.Builder();
  }

  /** Builder for kotlin toolchain info */
  public static class Builder {
    String languageVersion;
    ImmutableList<Label> sdkTargets;
    ImmutableList<String> kotlinCompilerCommonFlags;

    @CanIgnoreReturnValue
    public KotlinToolchainIdeInfo.Builder setLanguageVersion(String languageVersion) {
      this.languageVersion = languageVersion;
      return this;
    }

    @CanIgnoreReturnValue
    public KotlinToolchainIdeInfo.Builder setSdkTargets(ImmutableList<Label> sdkTargets) {
      this.sdkTargets = sdkTargets;
      return this;
    }

    @CanIgnoreReturnValue
    public KotlinToolchainIdeInfo.Builder setKotlinCompilerCommonFlags(
        ImmutableList<String> kotlinCompilerCommonFlags) {
      this.kotlinCompilerCommonFlags = kotlinCompilerCommonFlags;
      return this;
    }

    public KotlinToolchainIdeInfo build() {
      return new KotlinToolchainIdeInfo(languageVersion, sdkTargets, kotlinCompilerCommonFlags);
    }
  }
}
