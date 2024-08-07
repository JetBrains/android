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
package com.google.idea.blaze.base.qsync;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.exception.BuildException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A service that tracks what files in the project can be analyzed and what is the status of their
 * dependencies.
 */
public interface DependencyTracker {

  /**
   * Builds the external dependencies of the given target(s), putting the resultant libraries in the
   * shared library directory so that they are picked up by the IDE.
   */
  boolean buildDependenciesForTargets(BlazeContext context, DependencyBuildRequest request)
      throws IOException, BuildException;

  /** Request to {@link #buildDependenciesForTargets(BlazeContext, DependencyBuildRequest)}. */
  class DependencyBuildRequest {
    enum RequestType {
      /** Build a single target and do not check if its dependencies were built. */
      SINGLE_TARGET,
      /**
       * Build multiple targets and mark all dependencies as built even if they produce no
       * artifacts.
       */
      MULTIPLE_TARGETS,
      /**
       * Build thw whole project and mark all dependencies as built even if they produce no
       * artifacts.
       */
      WHOLE_PROJECT,
    };

    final RequestType requestType;
    final ImmutableSet<Label> targets;

    private DependencyBuildRequest(RequestType type, ImmutableSet<Label> targets) {
      this.requestType = type;
      this.targets = targets;
    }

    public static DependencyBuildRequest singleTarget(Label target) {
      return new DependencyBuildRequest(RequestType.SINGLE_TARGET, ImmutableSet.of(target));
    }

    public static DependencyBuildRequest multiTarget(Set<Label> targets) {
      return new DependencyBuildRequest(RequestType.MULTIPLE_TARGETS, ImmutableSet.copyOf(targets));
    }

    public static DependencyBuildRequest wholeProject() {
      return new DependencyBuildRequest(RequestType.WHOLE_PROJECT, ImmutableSet.of());
    }
  }
}
