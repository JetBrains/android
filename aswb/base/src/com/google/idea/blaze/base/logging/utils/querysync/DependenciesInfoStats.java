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
package com.google.idea.blaze.base.logging.utils.querysync;

import com.google.auto.value.AutoValue;
import java.util.Optional;
import javax.annotation.Nullable;

/** The basic dependencies information to track of a query sync project. */
@AutoValue
public abstract class DependenciesInfoStats {
  public abstract Optional<Integer> targetMapSize();

  public abstract Optional<Integer> libraryCount();

  public abstract Optional<Integer> jarCount();

  public static Builder builder() {
    return new AutoValue_DependenciesInfoStats.Builder();
  }

  /** Auto value builder for DependenciesInfoStats. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setTargetMapSize(@Nullable Integer value);

    public abstract Builder setLibraryCount(@Nullable Integer value);

    public abstract Builder setJarCount(@Nullable Integer value);

    public abstract DependenciesInfoStats build();
  }
}
