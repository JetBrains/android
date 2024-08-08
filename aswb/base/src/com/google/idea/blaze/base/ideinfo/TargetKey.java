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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import java.util.List;

/** A key that uniquely identifies a target in the target map */
public final class TargetKey
    implements ProtoWrapper<IntellijIdeInfo.TargetKey>, Comparable<TargetKey> {
  private final Label label;
  private final ImmutableList<String> aspectIds;

  private TargetKey(Label label, ImmutableList<String> aspectIds) {
    this.label = label;
    this.aspectIds = aspectIds;
  }

  public static TargetKey fromProto(IntellijIdeInfo.TargetKey proto) {
    return ProjectDataInterner.intern(
        new TargetKey(
            Label.fromProto(proto.getLabel()),
            ProtoWrapper.internStrings(proto.getAspectIdsList())));
  }

  @Override
  public IntellijIdeInfo.TargetKey toProto() {
    return IntellijIdeInfo.TargetKey.newBuilder()
        .setLabel(label.toProto())
        .addAllAspectIds(aspectIds)
        .build();
  }

  public Label getLabel() {
    return label;
  }

  private ImmutableList<String> getAspectIds() {
    return aspectIds;
  }

  /** Returns a key identifying a plain target */
  public static TargetKey forPlainTarget(Label label) {
    return forGeneralTarget(label, ImmutableList.of());
  }

  /** Returns a key identifying a general target */
  public static TargetKey forGeneralTarget(Label label, List<String> aspectIds) {
    return ProjectDataInterner.intern(new TargetKey(label, ProtoWrapper.internStrings(aspectIds)));
  }

  public boolean isPlainTarget() {
    return getAspectIds().isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TargetKey key = (TargetKey) o;
    return Objects.equal(getLabel(), key.getLabel())
        && Objects.equal(getAspectIds(), key.getAspectIds());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getLabel(), getAspectIds());
  }

  @Override
  public String toString() {
    if (getAspectIds().isEmpty()) {
      return getLabel().toString();
    }
    return getLabel().toString() + "#" + Joiner.on('#').join(getAspectIds());
  }

  @Override
  public int compareTo(TargetKey o) {
    return ComparisonChain.start()
        .compare(getLabel(), o.getLabel())
        .compare(getAspectIds(), o.getAspectIds(), Ordering.natural().lexicographical())
        .result();
  }
}
