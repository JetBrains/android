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
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Provides pending external dependencies for project targets.
 *
 * <p>The pending external dependencies is defined as:
 *
 * <ul>
 *   <li>The set of transitive dependencies of the target, based on the {@code query} output. This
 *       includes all in-project dependencies, and direct out-of-project dependencies of those (i.e.
 *       we do not track transitive dependencies of non-project targets).
 *   <li>MINUS in-project dependencies (i.e just the non-project targets from above)
 *   <li>MINUS any non-project targets that have already been built.
 * </ul>
 *
 * Note: the above applies to {@link DependencyTrackingBehavior#EXTERNAL_DEPENDENCIES} targets only;
 * for others, the pending external deps are just the project target itself.
 *
 * <p>Note: this class is generic only for ease of testing. In production, the generic parameter is
 * always {@link com.google.idea.blaze.common.Label}.
 */
public class PendingExternalDeps<T> {
  private final ExternalTransitiveClosure<T> externalDeps;
  private final ImmutableSet<T> builtDeps;
  private final Function<T, Set<DependencyTrackingBehavior>> depTrackingBehaviourFn;
  private final ConcurrentHashMap<T, ImmutableSet<T>> pendingExternalDeps =
      new ConcurrentHashMap<>();

  public PendingExternalDeps(
      ExternalTransitiveClosure<T> externalDeps,
      ImmutableSet<T> builtDeps,
      Function<T, Set<DependencyTrackingBehavior>> depTrackingBehaviourFn) {
    this.builtDeps = builtDeps;
    this.externalDeps = externalDeps;
    this.depTrackingBehaviourFn = depTrackingBehaviourFn;
  }

  /**
   * Returns the set of targets that would need to be built in order to enable analysis for a
   * project target.
   *
   * @param ofTarget A project target.
   * @return The set of targets that need to be built. For a {@link
   *     DependencyTrackingBehavior#EXTERNAL_DEPENDENCIES} target this will be the set of external
   *     dependencies; for a {@link DependencyTrackingBehavior#SELF} target this will be the target
   *     itself.
   */
  public ImmutableSet<T> get(T ofTarget) {
    return pendingExternalDeps.computeIfAbsent(ofTarget, this::calculate);
  }

  private ImmutableSet<T> calculate(T ofTarget) {
    Set<DependencyTrackingBehavior> depTracking = depTrackingBehaviourFn.apply(ofTarget);
    Set<T> deps = ImmutableSet.of();
    for (DependencyTrackingBehavior behavior : depTracking) {
      switch (behavior) {
        case EXTERNAL_DEPENDENCIES:
          deps = Sets.union(deps, Sets.difference(externalDeps.get(ofTarget), builtDeps));
          break;
        case SELF:
          // For C/C++, we don't need to build external deps, but we do need to extract
          // compilation information for the target itself.
          if (!builtDeps.contains(ofTarget)) {
            deps = Sets.union(deps, ImmutableSet.of(ofTarget));
          }
          break;
      }
    }
    return ImmutableSet.copyOf(deps);
  }
}
