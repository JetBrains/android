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
package com.google.idea.blaze.qsync;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Lists;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactMetadata;
import com.google.idea.blaze.qsync.deps.ArtifactMetadataProvider;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.ProjectProto.Project;
import java.util.List;

/** Applies a transform to a project proto instance, yielding a new instance. */
@FunctionalInterface
public interface ProjectProtoTransform extends ArtifactMetadataProvider {

  default ImmutableSetMultimap<BuildArtifact, ArtifactMetadata> getRequiredArtifactMetadata(
      TargetBuildInfo forTarget) {
    return ImmutableSetMultimap.of();
  }

  /**
   * Apply the transform.
   *
   * @param proto The existing project proto. This is derived from {@code graph} and may have had
   *     other transforms applied to it.
   * @param graph The graph from which {@code proto} was derived from.
   * @param context Context.
   * @return A project proto instance to replace the existing one. May return {@code proto}
   *     unchanged if this transform doesn't need to change anything.
   */
  Project apply(Project proto, BuildGraphData graph, Context<?> context) throws BuildException;

  static ProjectProtoTransform compose(Iterable<ProjectProtoTransform> transforms) {
    return new ProjectProtoTransform() {
      @Override
      public ImmutableSetMultimap<BuildArtifact, ArtifactMetadata> getRequiredArtifactMetadata(
          TargetBuildInfo targetInfo) {
        ImmutableSetMultimap.Builder<BuildArtifact, ArtifactMetadata> allArtifacts =
            ImmutableSetMultimap.builder();
        for (ProjectProtoTransform transform : transforms) {
          allArtifacts.putAll(transform.getRequiredArtifactMetadata(targetInfo));
        }
        return allArtifacts.build();
      }

      @Override
      public Project apply(Project proto, BuildGraphData graph, Context<?> context)
          throws BuildException {
        for (ProjectProtoTransform transform : transforms) {
          proto = transform.apply(proto, graph, context);
        }
        return proto;
      }
    };
  }

  /**
   * Simple registry for transforms that also supports returning all transforms combined into one.
   */
  class Registry {
    private final List<ProjectProtoTransform> transforms = Lists.newArrayList();

    public void add(ProjectProtoTransform transform) {
      transforms.add(transform);
    }

    public void addAll(ImmutableCollection<ProjectProtoTransform> transforms) {
      this.transforms.addAll(transforms);
    }

    public ProjectProtoTransform getComposedTransform() {
      return compose(ImmutableList.copyOf(transforms));
    }
  }
}
