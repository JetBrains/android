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
import com.google.idea.blaze.qsync.deps.OutputGroup;
import com.google.idea.blaze.qsync.project.QuerySyncLanguage;
import com.google.idea.common.experiments.BoolExperiment;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A service that tracks what files in the project can be analyzed and what is the status of their
 * dependencies.
 */
public interface DependencyTracker {
  BoolExperiment gatherJdeps =
    new BoolExperiment("qsync.gather.jdeps", false);

  /**
   * Builds the external dependencies of the given target(s), putting the resultant libraries in the
   * shared library directory so that they are picked up by the IDE.
   */
  boolean buildDependenciesForTargets(BlazeContext context, DependencyBuildRequest request)
      throws IOException, BuildException;

  /** Request to {@link #buildDependenciesForTargets(BlazeContext, DependencyBuildRequest)}. */
  class DependencyBuildRequest {
    public enum RequestType {
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
      FILE_PREVIEWS
    };

    final RequestType requestType;
    final ImmutableSet<Label> targets;

    private DependencyBuildRequest(RequestType type, ImmutableSet<Label> targets) {
      this.requestType = type;
      this.targets = targets;
    }

    public static DependencyBuildRequest multiTarget(Collection<Label> targets) {
      return new DependencyBuildRequest(RequestType.MULTIPLE_TARGETS, ImmutableSet.copyOf(targets));
    }

    public static DependencyBuildRequest wholeProject() {
      return new DependencyBuildRequest(RequestType.WHOLE_PROJECT, ImmutableSet.of());
    }

    public static DependencyBuildRequest filePreviews(Collection<Label> targets) {
      return new DependencyBuildRequest(RequestType.FILE_PREVIEWS, ImmutableSet.copyOf(targets));
    }

    public Collection<OutputGroup> getOutputGroups(Collection<QuerySyncLanguage> languages) {
      return getOutputGroups(languages, requestType);
    }

    public static Collection<OutputGroup> getOutputGroups(Collection<QuerySyncLanguage> languages, RequestType type) {
      var outputGroups = languages.stream()
        .mapMulti(DependencyBuildRequest::languageToOutputGroups)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(OutputGroup.class)));

      if (type.equals(RequestType.FILE_PREVIEWS)) {
        outputGroups.add(OutputGroup.TRANSITIVE_RUNTIME_JARS);
      }

      return outputGroups;
    }

    private static void languageToOutputGroups(QuerySyncLanguage language, Consumer<OutputGroup> consumer) {
      switch (language) {
        case JVM -> {
          consumer.accept(OutputGroup.JARS);
          consumer.accept(OutputGroup.AARS);
          consumer.accept(OutputGroup.GENSRCS);
          consumer.accept(OutputGroup.ARTIFACT_INFO_FILE);
          if (gatherJdeps.getValue()) {
            consumer.accept(OutputGroup.JDEPS);
          }
        }
        case CC -> {
          consumer.accept(OutputGroup.CC_HEADERS);
          consumer.accept(OutputGroup.CC_INFO_FILE);
        }
      }
    }
  }

  DependencyBuilder getBuilder();
}
