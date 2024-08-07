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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Map;

/**
 * Provides the external dependencies of project targets.
 *
 * <p>The functionality provided here is an optimization of the guava {@link
 * com.google.common.graph.Graph} functionality of:
 *
 * <pre>
 *   Sets.intersection(Graphs.transitiveClosure(depsGraph()).successors(target), projectDeps()));
 * </pre>
 *
 * Where {@code projectDeps()} is {@link BuildGraphData#projectDeps()}, i.e. the set of targets
 * considered external to the project.
 *
 * <p>Calculating the entire transitive closure is much too slow for a large project, and we don't
 * need the entire transitive closure, only those that overlap with {@link
 * BuildGraphData#projectDeps()}. We perform this intersection as we build the transitive closure
 * for efficiency reasons, and memo-ize the result.
 */
public class ExternalTransitiveClosure<N> {

  private final DepsGraph<N> graph;
  private final ImmutableSet<N> externalDeps;

  private final Map<N, ImmutableSet<N>> reachableNodes = Maps.newHashMap();

  public ExternalTransitiveClosure(DepsGraph<N> graph, ImmutableSet<N> externalDeps) {
    this.graph = graph;
    this.externalDeps = externalDeps;
  }

  public ImmutableSet<N> get(N node) {
    return reachable(node);
  }

  private ImmutableSet<N> reachable(N node) {
    if (reachableNodes.containsKey(node)) {
      return reachableNodes.get(node);
    }

    ImmutableSet.Builder<N> builder = ImmutableSet.builder();
    if (externalDeps.contains(node)) {
      builder.add(node);
    }
    builder.addAll(Sets.intersection(graph.deps(node), externalDeps));
    graph.deps(node).stream().map(this::reachable).forEach(builder::addAll);
    ImmutableSet<N> r = builder.build();
    reachableNodes.put(node, r);
    return r;
  }
}
