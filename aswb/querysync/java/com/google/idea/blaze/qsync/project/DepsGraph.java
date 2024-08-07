/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.project;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Set;

/**
 * A graph of dependencies optimized for use with {@link BuildGraphData}.
 *
 * <p>This is a subset of guava's {@link com.google.common.graph.Graph} support, optimized for the
 * requirements of querysync.
 */
public class DepsGraph<N> {

  private final ImmutableMap<N, ImmutableSet<N>> deps;
  private final ImmutableSetMultimap<N, N> rdeps;

  private DepsGraph(ImmutableMap<N, ImmutableSet<N>> deps, ImmutableSetMultimap<N, N> rdeps) {
    this.deps = deps;
    this.rdeps = rdeps;
  }

  public Set<N> nodes() {
    return Sets.union(deps.keySet(), rdeps.keySet());
  }

  public ImmutableSet<N> deps(N target) {
    ImmutableSet<N> r = deps.get(target);
    if (r == null) {
      r = ImmutableSet.of();
    }
    return r;
  }

  public ImmutableSet<N> rdeps(N target) {
    return rdeps.get(target);
  }

  public static class Builder<N> {

    private final ImmutableMap.Builder<N, ImmutableSet<N>> deps = ImmutableMap.builder();
    private final ImmutableSetMultimap.Builder<N, N> rdeps = ImmutableSetMultimap.builder();

    @CanIgnoreReturnValue
    public Builder add(N target, ImmutableSet<N> deps) {
      this.deps.put(target, deps);
      deps.forEach(dep -> this.rdeps.put(dep, target));
      return this;
    }

    public DepsGraph build() {
      return new DepsGraph(deps.build(), rdeps.build());
    }
  }
}
