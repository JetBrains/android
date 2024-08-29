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
package com.google.idea.blaze.qsync.project;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.protobuf.ExtensionRegistryLite;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Applies a transform to a project proto instance, yielding a new instance. */
@FunctionalInterface
public interface ProjectProtoTransform {
  @VisibleForTesting
  public static final BoolExperiment enableReadArtifactFromArtifactDirectories =
    new BoolExperiment("qsync.project.read.artifact.directory", true);

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
  ProjectProto.Project apply(ProjectProto.Project proto, BuildGraphData graph, Context<?> context)
      throws BuildException;

  static ProjectProtoTransform compose(Iterable<ProjectProtoTransform> transforms) {
    return (proto, graph, context) -> {
      for (ProjectProtoTransform transform : transforms) {
        proto = transform.apply(proto, graph, context);
      }
      return proto;
    };
  }

  /**
   * Loads ArtifactDirectoryContents from *.contents file and reverse the map to get a map from artifact
   * digest to artifact directory path.
   */
  static Map<String, Path> getExistingContents(Path root) {
    if (!enableReadArtifactFromArtifactDirectories.getValue()) {
      return ImmutableMap.of();
    }
    Path contentsProtoPath = root.resolveSibling(root.getFileName() + ".contents");
    if (Files.exists(contentsProtoPath)) {
      try (InputStream in = Files.newInputStream(contentsProtoPath)) {
        Map<String, Path> builder = new HashMap<>();
        ProjectProto.ArtifactDirectoryContents existingContents =
          ProjectProto.ArtifactDirectoryContents.parseFrom(in, ExtensionRegistryLite.getEmptyRegistry());
        for (Map.Entry<String, ProjectProto.ProjectArtifact> entry : existingContents.getContentsMap().entrySet()) {
          ProjectProto.ProjectArtifact.OriginCase originCase = entry.getValue().getOriginCase();
          switch (originCase) {
            case ORIGIN_NOT_SET:
            case WORKSPACE_RELATIVE_PATH:
              break;
            case BUILD_ARTIFACT:
              Path path = builder.get(entry.getValue().getBuildArtifact().getDigest());
              // Even though digest should be unique but it may not be unique already.
              // Check AddProjectGenSrcs.update line 143 as an example.
              // So we need to choose smaller one as AddProjectGenSrcs.update did in previous build (line 166)
              // to find the expect target.
              if (path == null || root.resolve(entry.getKey()).compareTo(path) < 0) {
                builder.put(entry.getValue().getBuildArtifact().getDigest(), root.resolve(entry.getKey()));
              }
              break;
          }
        }
        return ImmutableMap.copyOf(builder);
      } catch (IOException e) {
        return ImmutableMap.of();
      }
    }
    return ImmutableMap.of();
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
