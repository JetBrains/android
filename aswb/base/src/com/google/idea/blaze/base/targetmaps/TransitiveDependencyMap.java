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
package com.google.idea.blaze.base.targetmaps;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Stream;

/** Handy class to find all transitive dependencies of a given target */
public class TransitiveDependencyMap {
  private final Project project;

  public static TransitiveDependencyMap getInstance(Project project) {
    return project.getService(TransitiveDependencyMap.class);
  }

  public TransitiveDependencyMap(Project project) {
    this.project = project;
  }

  /**
   * Returns true if {@code possibleDependent} transitively depends on {@code possibleDependency}
   * according to the project's target map.
   */
  public boolean hasTransitiveDependency(
      TargetKey possibleDependent, TargetKey possibleDependency) {

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return false;
    }

    return getTransitiveDependenciesStream(possibleDependent, blazeProjectData.getTargetMap())
        .anyMatch(possibleDependency::equals);
  }

  /**
   * Returns the set of targets in {@code possibleDependencies} that {@code possibleDependent}
   * depends on.
   *
   * <p>The returned set will not include {@code possibleDependent} even if it is included in {@code
   * possibleDependencies}.
   */
  public ImmutableSet<TargetKey> filterPossibleTransitiveDeps(
      TargetKey possibleDependent, Collection<TargetKey> possibleDependencies) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return ImmutableSet.of();
    }
    ImmutableSet<TargetKey> possibleDepsSet = ImmutableSet.copyOf(possibleDependencies);
    return getTransitiveDependenciesStream(possibleDependent, blazeProjectData.getTargetMap())
        .filter(possibleDepsSet::contains)
        .distinct()
        .limit(possibleDepsSet.size())
        .collect(ImmutableSet.toImmutableSet());
  }

  public ImmutableCollection<TargetKey> getTransitiveDependencies(TargetKey targetKey) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return ImmutableSet.of();
    }
    // TODO: see if we need caching.
    return getTransitiveDependencies(targetKey, blazeProjectData.getTargetMap());
  }

  public static ImmutableCollection<TargetKey> getTransitiveDependencies(
      TargetKey targetKey, TargetMap targetMap) {
    return getTransitiveDependencies(ImmutableList.of(targetKey), targetMap);
  }

  public static ImmutableCollection<TargetKey> getTransitiveDependencies(
      Collection<TargetKey> targetKeys, TargetMap targetMap) {
    return getTransitiveDependenciesStream(targetKeys, targetMap)
        .collect(ImmutableSet.toImmutableSet());
  }

  public static Stream<TargetKey> getTransitiveDependenciesStream(
      TargetKey key, TargetMap targetMap) {
    return getTransitiveDependenciesStream(ImmutableList.of(key), targetMap);
  }

  /**
   * Returns a stream which traverses the transitive dependencies of the given collection of Blaze
   * targets.
   */
  public static Stream<TargetKey> getTransitiveDependenciesStream(
      Collection<TargetKey> keys, TargetMap targetMap) {
    return Streams.stream(new TransitiveDependencyIterator(keys, targetMap));
  }

  /**
   * An iterator which performs a breadth-first traversal over the transitive dependencies of a
   * collection of Blaze targets. Targets in the top-level collection will only be included in the
   * traversal if they appear as a transitive dependency of another top-level target. For example,
   * suppose that
   *
   * <p>Target A depends on targets B, C. Target B depends on target D. Target E depends on targets
   * A, C, F.
   *
   * <p>Then a TransitiveDependenciesIterator constructed from [A, E] will yield the values [B, C,
   * A, F, D] in that order.
   */
  private static class TransitiveDependencyIterator implements Iterator<TargetKey> {
    private final LinkedHashSet<TargetKey> toVisit;
    private final Set<TargetKey> visited;
    private final TargetMap targetMap;

    public TransitiveDependencyIterator(Collection<TargetKey> keys, TargetMap targetMap) {
      toVisit = new LinkedHashSet<>();
      visited = new HashSet<>();
      this.targetMap = targetMap;

      keys.stream().distinct().flatMap(this::getDependenciesOf).forEach(toVisit::add);
    }

    private Stream<TargetKey> getDependenciesOf(TargetKey key) {
      TargetIdeInfo target = targetMap.get(key);
      if (target == null) {
        return Stream.empty();
      }

      return target.getDependencies().stream()
          .map(Dependency::getTargetKey)
          .map(TargetKey::getLabel)
          .map(TargetKey::forPlainTarget);
    }

    @Override
    public boolean hasNext() {
      return !toVisit.isEmpty();
    }

    @Override
    public TargetKey next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      TargetKey nextKey = toVisit.iterator().next();
      toVisit.remove(nextKey);
      visited.add(nextKey);

      // Queue up any dependencies from this target that
      // we haven't processed yet so that we can visit them
      // in subsequent calls to next()
      getDependenciesOf(nextKey)
          .filter(dependency -> !visited.contains(dependency))
          .forEach(toVisit::add);

      return nextKey;
    }
  }
}
