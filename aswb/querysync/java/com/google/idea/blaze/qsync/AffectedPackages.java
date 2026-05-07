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

  static Builder builder() {
    return new AutoValue_AffectedPackages.Builder();
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

    public abstract AffectedPackages build();
  }
}
