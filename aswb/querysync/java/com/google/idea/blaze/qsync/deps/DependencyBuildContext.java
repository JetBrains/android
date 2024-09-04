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
package com.google.idea.blaze.qsync.deps;

import com.google.auto.value.AutoValue;
import com.google.idea.blaze.common.vcs.VcsState;
import java.time.Instant;
import java.util.Optional;

/**
 * Basic information about a dependency build. This is used to track where built artifacts
 * originated from.
 */
@AutoValue
public abstract class DependencyBuildContext {

  public static final DependencyBuildContext NONE = create("", Instant.EPOCH, Optional.empty());

  /** The bazel build ID. */
  public abstract String buildId();

  /**
   * The time that the build was started at. Used to disambiguate between conflicting artifacts, by
   * taking the most recent (i.e. when multiple sequential builds have output the same artifact).
   */
  public abstract Instant startTime();

  /**
   * The state of the VCS from the build that produced this output. May be absent if the bazel
   * instance or VCS state do not support this.
   */
  public abstract Optional<VcsState> vcsState();

  public static DependencyBuildContext create(
      String buildId, Instant startTime, Optional<VcsState> vcsState) {
    return new AutoValue_DependencyBuildContext(buildId, startTime, vcsState);
  }
}
