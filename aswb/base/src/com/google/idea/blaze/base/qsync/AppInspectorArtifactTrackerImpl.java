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
package com.google.idea.blaze.base.qsync;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.artifacts.ArtifactDirectoryUpdate;
import com.google.idea.blaze.qsync.project.ProjectProto;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

/**
 * A local cache of built app inspector artifacts.
 */
public class AppInspectorArtifactTrackerImpl implements AppInspectorArtifactTracker {
  private static final Logger logger = Logger.getLogger(AppInspectorArtifactTrackerImpl.class.getName());

  private final Path workspaceRoot;
  private final BuildArtifactCache artifactCache;
  private final Path inspectorsDir;

  /**
   * An in-memory storage holding the details of all previously requested application inspectors.
   *
   * <p>Being in-memory means that the inspectors directory is cleared on each ASwB restart. This is expected. Application inspectors are
   * for now re-built on each request and one additional copying does not really matter.
   */
  private final Map<Label, Map<Path, ProjectProto.ProjectArtifact>> knownInspectors = new HashMap<>();

  public AppInspectorArtifactTrackerImpl(Path workspaceRoot, BuildArtifactCache artifactCache, Path inspectorsDir) {
    this.workspaceRoot = workspaceRoot;
    this.artifactCache = artifactCache;
    this.inspectorsDir = inspectorsDir;
  }

  @Override
  public synchronized ImmutableSet<Path> update(
    Label appInspectorTarget,
    AppInspectorInfo appInspectorInfo,
    Context<?> context
  ) throws BuildException {
    final var artifactsCachedFuture = artifactCache.addAll(appInspectorInfo.getJars(), context);
    final var appInspectorArtifactLayout = buildAppInspectorArtifactLayout(appInspectorTarget, appInspectorInfo);
    knownInspectors.put(appInspectorTarget, appInspectorArtifactLayout);

    final var artifactDirectoryContents = buildArtifactDirectoryContents(knownInspectors);
    waitForArtifacts(artifactsCachedFuture);
    updateArtifactDirectory(artifactDirectoryContents);

    return resolveArtifactLayoutPaths(appInspectorTarget, appInspectorArtifactLayout.keySet());
  }

  private static void waitForArtifacts(ListenableFuture<?> artifactsCachedFuture) throws BuildException {
    try {
      artifactsCachedFuture.get();
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BuildException(e);
    }
    catch (ExecutionException e) {
      throw new BuildException(e);
    }
  }

  private ImmutableSet<Path> resolveArtifactLayoutPaths(
    Label appInspectorTarget,
    Set<Path> appInspectorArtifactPaths
  ) {
    return appInspectorArtifactPaths.stream()
      .map(path -> inspectorsDir.resolve(appInspectorTarget.toFilePath()).resolve(path))
      .collect(toImmutableSet());
  }

  private void updateArtifactDirectory(ProjectProto.ArtifactDirectoryContents artifactDirectoryContents) throws BuildException {
    try {
      new ArtifactDirectoryUpdate(
        artifactCache, workspaceRoot, inspectorsDir, artifactDirectoryContents, null, false)
        .update();
    }
    catch (IOException e) {
      throw new BuildException(e);
    }
  }

  /**
   * Builds {@link ProjectProto.ArtifactDirectoryContents} from a map from application inspector label -> artifact path -> project artifact.
   */
  private static ProjectProto.ArtifactDirectoryContents buildArtifactDirectoryContents(
    Map<Label, Map<Path, ProjectProto.ProjectArtifact>> knownInspectors
  ) {
    final var artifactDirectoryContents = ProjectProto.ArtifactDirectoryContents.newBuilder();
    for (final var entry : knownInspectors.entrySet()) {
      final var inspectorLabel = entry.getKey();
      for (final var inspectorArtifactPathAndDigest : entry.getValue().entrySet()) {
        final var appInspectorArtifactPath = inspectorArtifactPathAndDigest.getKey();
        final var appInspectorArtifact = inspectorArtifactPathAndDigest.getValue();
        final var artifactLocalPath = inspectorLabel.toFilePath().resolve(appInspectorArtifactPath);
        artifactDirectoryContents.putContents(artifactLocalPath.toString(), appInspectorArtifact);
      }
    }
    return artifactDirectoryContents.build();
  }

  private static ImmutableMap<Path, ProjectProto.ProjectArtifact> buildAppInspectorArtifactLayout(
    Label appInspectorTarget,
    AppInspectorInfo appInspectorInfo
  ) {
    final var resultBuilder = ImmutableMap.<Path, ProjectProto.ProjectArtifact>builder();
    for (OutputArtifact jar : appInspectorInfo.getJars()) {
      resultBuilder.put(
        jar.getArtifactPath(),
        ProjectProto.ProjectArtifact.newBuilder()
          .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest(jar.getDigest()))
          .setTarget(appInspectorTarget.toString())
          .setTransform(ProjectProto.ProjectArtifact.ArtifactTransform.COPY)
          .build());
    }
    return resultBuilder.build();
  }
}
