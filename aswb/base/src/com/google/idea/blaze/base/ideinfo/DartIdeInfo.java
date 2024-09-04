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

import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Objects;

/** Ide info specific to dart rules. */
public final class DartIdeInfo implements ProtoWrapper<IntellijIdeInfo.DartIdeInfo> {
  private final ImmutableList<ArtifactLocation> sources;

  private DartIdeInfo(ImmutableList<ArtifactLocation> sources) {
    this.sources = sources;
  }

  static DartIdeInfo fromProto(IntellijIdeInfo.DartIdeInfo proto) {
    return new DartIdeInfo(ProtoWrapper.map(proto.getSourcesList(), ArtifactLocation::fromProto));
  }

  @Override
  public IntellijIdeInfo.DartIdeInfo toProto() {
    return IntellijIdeInfo.DartIdeInfo.newBuilder()
        .addAllSources(ProtoWrapper.mapToProtos(sources))
        .build();
  }

  public ImmutableList<ArtifactLocation> getSources() {
    return sources;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for dart rule info */
  public static class Builder {
    private final ImmutableList.Builder<ArtifactLocation> sources = ImmutableList.builder();

    @CanIgnoreReturnValue
    public Builder addSources(Iterable<ArtifactLocation> sources) {
      this.sources.addAll(sources);
      return this;
    }

    public DartIdeInfo build() {
      return new DartIdeInfo(sources.build());
    }
  }

  @Override
  public String toString() {
    return "DartIdeInfo{" + "\n" + "  sources=" + getSources() + "\n" + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DartIdeInfo that = (DartIdeInfo) o;
    return Objects.equals(sources, that.sources);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sources);
  }
}
