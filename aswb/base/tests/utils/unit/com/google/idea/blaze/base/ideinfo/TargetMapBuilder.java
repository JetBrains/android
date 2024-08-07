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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Collection;
import java.util.List;

/** Builds a target map. */
public class TargetMapBuilder {
  private final List<TargetIdeInfo> targets = Lists.newArrayList();

  public static TargetMapBuilder builder() {
    return new TargetMapBuilder();
  }

  @CanIgnoreReturnValue
  public TargetMapBuilder addTarget(TargetIdeInfo target) {
    targets.add(target);
    return this;
  }

  public TargetMapBuilder addTarget(TargetIdeInfo.Builder target) {
    return addTarget(target.build());
  }

  @CanIgnoreReturnValue
  public TargetMapBuilder addTargets(Collection<? extends TargetIdeInfo> targets) {
    this.targets.addAll(targets);
    return this;
  }

  public TargetMap build() {
    ImmutableMap.Builder<TargetKey, TargetIdeInfo> targetMap = ImmutableMap.builder();
    for (TargetIdeInfo target : targets) {
      TargetKey key = target.getKey();
      targetMap.put(key, target);
    }
    return new TargetMap(targetMap.build());
  }
}
