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

import com.google.common.base.Objects;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.Dependency.DependencyType;

/** Represents a dependency between two targets. */
public final class Dependency implements ProtoWrapper<IntellijIdeInfo.Dependency> {
  private final TargetKey targetKey;
  private final DependencyType dependencyType;

  public Dependency(TargetKey targetKey, DependencyType dependencyType) {
    this.targetKey = targetKey;
    this.dependencyType = dependencyType;
  }

  static Dependency fromProto(IntellijIdeInfo.Dependency proto) {
    return ProjectDataInterner.intern(
        new Dependency(TargetKey.fromProto(proto.getTarget()), proto.getDependencyType()));
  }

  @Override
  public IntellijIdeInfo.Dependency toProto() {
    return IntellijIdeInfo.Dependency.newBuilder()
        .setTarget(targetKey.toProto())
        .setDependencyType(dependencyType)
        .build();
  }

  public TargetKey getTargetKey() {
    return targetKey;
  }

  public IntellijIdeInfo.Dependency.DependencyType getDependencyType() {
    return dependencyType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Dependency that = (Dependency) o;
    return Objects.equal(getTargetKey(), that.getTargetKey())
        && getDependencyType() == that.getDependencyType();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getTargetKey(), getDependencyType());
  }

  @Override
  public String toString() {
    return String.format("Dependency{%s, %s}", targetKey, dependencyType);
  }
}
