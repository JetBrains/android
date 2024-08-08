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
package com.google.idea.blaze.qsync;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Encapsulates build packages that are affected by changes to files in the project view, and logic
 * to calculate that.
 */
@AutoValue
abstract class AffectedPackages {

  public static final AffectedPackages EMPTY = AffectedPackages.builder().build();

  /** Paths of packages that are affected. */
  public abstract ImmutableSet<Path> getModifiedPackages();

  /** Paths of packages that have been deleted. */
  public abstract ImmutableSet<Path> getDeletedPackages();

  /**
   * Changes outside of the project view that are not reflected in {@link #getModifiedPackages()} or
   * {@link #getDeletedPackages()}.
   */
  public abstract boolean isIncomplete();

  /**
   * Modified sources that are outside of any build package. This can be benign (e.g. a README file)
   * or may indicate a problem with the build rules.
   */
  public abstract ImmutableSet<Path> getUnownedSources();

  static Builder builder() {
    return new AutoValue_AffectedPackages.Builder()
        .setIncomplete(false)
        .setUnownedSources(ImmutableSet.of());
  }

  public boolean isEmpty() {
    return getModifiedPackages().isEmpty() && getDeletedPackages().isEmpty();
  }

  @AutoValue.Builder
  abstract static class Builder {

    public abstract ImmutableSet.Builder<Path> modifiedPackagesBuilder();

    public void addAffectedPackage(Path packagePath) {
      modifiedPackagesBuilder().add(packagePath);
    }

    public abstract ImmutableSet.Builder<Path> deletedPackagesBuilder();

    public void addDeletedPackage(Path packagePath) {
      deletedPackagesBuilder().add(packagePath);
    }

    public abstract Builder setUnownedSources(Collection<Path> unownedSources);

    public abstract Builder setIncomplete(boolean value);

    public abstract AffectedPackages build();
  }
}
