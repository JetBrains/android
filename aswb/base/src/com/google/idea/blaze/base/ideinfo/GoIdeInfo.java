/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/** Ide info specific to go rules. */
public final class GoIdeInfo implements ProtoWrapper<IntellijIdeInfo.GoIdeInfo> {
  private final ImmutableList<ArtifactLocation> sources;
  @Nullable private final String importPath;
  private final ImmutableList<Label> libraryLabels; // only valid for tests

  private GoIdeInfo(
      ImmutableList<ArtifactLocation> sources,
      @Nullable String importPath,
      ImmutableList<Label> libraryLabels) {
    this.sources = sources;
    this.importPath = importPath;
    this.libraryLabels = libraryLabels;
  }

  public static GoIdeInfo fromProto(
      IntellijIdeInfo.GoIdeInfo proto, Label targetLabel, Kind targetKind) {
    return new GoIdeInfo(
        ProtoWrapper.map(proto.getSourcesList(), ArtifactLocation::fromProto),
        ImportPathReplacer.fixImportPath(
            Strings.emptyToNull(proto.getImportPath()), targetLabel, targetKind),
        extractLibraryLabels(targetKind, proto.getLibraryLabelsList()));
  }

  private static ImmutableList<Label> extractLibraryLabels(Kind kind, List<String> libraryLabels) {
    if (!kind.hasLanguage(LanguageClass.GO)
        || kind.getRuleType() != RuleType.TEST
        || libraryLabels.isEmpty()) {
      return ImmutableList.of();
    }
    return libraryLabels.stream().map(Label::create).collect(ImmutableList.toImmutableList());
  }

  @Override
  public IntellijIdeInfo.GoIdeInfo toProto() {
    IntellijIdeInfo.GoIdeInfo.Builder builder =
        IntellijIdeInfo.GoIdeInfo.newBuilder().addAllSources(ProtoWrapper.mapToProtos(sources));
    ProtoWrapper.setIfNotNull(builder::setImportPath, importPath);
    builder.addAllLibraryLabels(ProtoWrapper.map(libraryLabels, Label::toProto));
    return builder.build();
  }

  public ImmutableList<ArtifactLocation> getSources() {
    return sources;
  }

  @Nullable
  public String getImportPath() {
    return importPath;
  }

  public ImmutableList<Label> getLibraryLabels() {
    return libraryLabels;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for go rule info */
  public static class Builder {
    private final ImmutableList.Builder<ArtifactLocation> sources = ImmutableList.builder();
    @Nullable private String importPath = null;
    private final ImmutableList.Builder<Label> libraryLabels = ImmutableList.builder();

    @CanIgnoreReturnValue
    public Builder addSource(ArtifactLocation source) {
      this.sources.add(source);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setImportPath(String importPath) {
      this.importPath = importPath;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addLibraryLabel(String libraryLabel) {
      this.libraryLabels.add(Label.create(libraryLabel));
      return this;
    }

    public GoIdeInfo build() {
      return new GoIdeInfo(sources.build(), importPath, libraryLabels.build());
    }
  }

  @Override
  public String toString() {
    return "GoIdeInfo{"
        + "\n"
        + "  sources="
        + getSources()
        + "\n"
        + "  importPath="
        + getImportPath()
        + "\n"
        + "  libraryLabels="
        + getLibraryLabels()
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
    GoIdeInfo goIdeInfo = (GoIdeInfo) o;
    return Objects.equals(sources, goIdeInfo.sources)
        && Objects.equals(importPath, goIdeInfo.importPath)
        && Objects.equals(libraryLabels, goIdeInfo.libraryLabels);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sources, importPath, libraryLabels);
  }
}
