/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.query;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Encapsulates a set of build packages, and includes utilities to find the containing or parent
 * package of a file or package.
 */
public class PackageSet {

  public static final PackageSet EMPTY = new PackageSet(ImmutableSet.of());

  private final ImmutableSet<Path> packages;

  public PackageSet(Set<Path> packages) {
    this.packages = ImmutableSet.copyOf(packages);
  }

  public static PackageSet of(Path... packages) {
    return new PackageSet(ImmutableSet.copyOf(packages));
  }

  public boolean contains(Path packagePath) {
    return packages.contains(packagePath);
  }

  public boolean isEmpty() {
    return packages.isEmpty();
  }

  public int size() {
    return packages.size();
  }

  @VisibleForTesting
  public ImmutableSet<Path> asPathSet() {
    return packages;
  }

  /** Create a derived package set with the given packages removed from it. */
  public PackageSet deletePackages(PackageSet deletedPackages) {
    return new PackageSet(Sets.difference(packages, deletedPackages.packages));
  }

  /** Create a derived package set with the given packages added to it. */
  public PackageSet addPackages(PackageSet addedPackages) {
    return new PackageSet(Sets.union(packages, addedPackages.packages));
  }

  /**
   * Returns the parent package of a given build package.
   *
   * <p>The parent package is not necessarily the same as the parent path: it may be an indirect
   * parent if there are paths that are not build packages (e.g. contain no BUILD file).
   */
  public Optional<Path> getParentPackage(Path buildPackage) {
    return findIncludingPackage(buildPackage.getParent());
  }

  /**
   * Find the package from within this set that is the best match of the given path.
   *
   * <p>If {@code path} is a package in this set, return it. If {@code path} has a parent in this
   * set, return the closest such parent. Otherwise, returns empty.
   */
  public Optional<Path> findIncludingPackage(Path path) {
    while (path != null) {
      if (packages.contains(path)) {
        return Optional.of(path);
      }
      path = path.getParent();
    }
    return Optional.empty();
  }

  public PackageSet getSubpackages(Path root) {
    return new PackageSet(
        packages.stream().filter(p -> p.startsWith(root)).collect(toImmutableSet()));
  }

  /** Builder for {@link PackageSet}. */
  public static class Builder {
    private final ImmutableSet.Builder<Path> set;

    public Builder() {
      set = ImmutableSet.builder();
    }

    @CanIgnoreReturnValue
    public Builder add(Path p) {
      set.add(p);
      return this;
    }

    public PackageSet build() {
      return new PackageSet(set.build());
    }
  }
}
