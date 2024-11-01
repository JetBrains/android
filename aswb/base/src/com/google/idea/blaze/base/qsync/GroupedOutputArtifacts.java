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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.blaze.qsync.deps.OutputGroup;
import java.util.Set;

/** Helper class for managing output artifacts grouped by {@link OutputGroup}. */
public class GroupedOutputArtifacts {

  public static final ImmutableListMultimap<OutputGroup, OutputArtifact> EMPTY =
      ImmutableListMultimap.of();

  private GroupedOutputArtifacts() {}

  @VisibleForTesting
  public static ImmutableListMultimap.Builder<OutputGroup, OutputArtifact> builder() {
    return new ImmutableListMultimap.Builder<>();
  }

  public static ImmutableListMultimap<OutputGroup, OutputArtifact> create(
      BlazeBuildOutputs buildOutputs, Set<OutputGroup> outputGroups) {
    ImmutableListMultimap.Builder<OutputGroup, OutputArtifact> builder = builder();
    for (OutputGroup group : outputGroups) {
      ImmutableList<OutputArtifact> artifacts =
        buildOutputs.getOutputGroupArtifacts(group.outputGroupName()::equals);
      builder.putAll(group, artifacts);
    }
    return builder.build();
  }
}
