/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.blaze.qsync.artifacts.ArtifactDirectoryUpdate;
import com.google.idea.blaze.qsync.deps.ArtifactDirectories;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public final class RuntimeArtifactCacheImpl implements RuntimeArtifactCache {
  private static final Logger logger = Logger.getInstance(RuntimeArtifactCacheImpl.class);
  private final Map<Pair<Label, RuntimeArtifactKind>,  Map<Path, ProjectArtifact>> artifactCacheMap = new HashMap<>();
  private final Path runfilesDirectory;
  private static final String SEPARATOR_DIR_NAME = "_";
  private final BuildArtifactCache buildArtifactCache;
  private final Path workspaceRoot;

  public RuntimeArtifactCacheImpl(
    Path runfilesDirectory, BuildArtifactCache buildArtifactCache, Path workspaceRoot)
      throws IOException {
    this.runfilesDirectory = runfilesDirectory;
    this.buildArtifactCache = buildArtifactCache;
    this.workspaceRoot = workspaceRoot;
  }

  private RuntimeArtifactCacheImpl(Project project) throws IOException {
    this(
        Paths.get(checkNotNull(project.getBasePath()))
            .resolve(ArtifactDirectories.RUNFILES.relativePath()),
        project.getService(BuildArtifactCache.class),
        WorkspaceRoot.fromProject(project).path());
  }

  @Override
  public ImmutableList<Path> fetchArtifacts(
      Label target, List<? extends OutputArtifact> artifacts, BlazeContext context, RuntimeArtifactKind artifactKind) {
    var targetKind = Pair.create(target, artifactKind);
    final var artifactsCachedFuture =
        buildArtifactCache.addAll(artifacts.stream().collect(toImmutableList()), context);
    artifactCacheMap.put(targetKind, buildArtifactLayout(target, artifacts));
    final var artifactDirectoryContents = buildArtifactDirectoryContents(artifactCacheMap);
    waitForArtifacts(artifactsCachedFuture);
    updateArtifactDirectory(artifactDirectoryContents);

    return resolveArtifactLayoutPaths(target, artifactKind, artifactCacheMap.get(targetKind).keySet());
  }

  private ImmutableList<Path> resolveArtifactLayoutPaths(Label target, RuntimeArtifactKind artifactKind, Set<Path> artifactPaths) {
    return artifactPaths.stream()
        .map(artifactPath -> runfilesDirectory.resolve(getArtifactLocalPath(target, artifactKind, artifactPath)))
        .collect(toImmutableList());
  }

  private static void waitForArtifacts(ListenableFuture<?> artifactsCachedFuture) {
    try {
      artifactsCachedFuture.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted exception while fetching artifacts", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Exception while fetching artifacts", e);
    }
  }

  private static ImmutableMap<Path, ProjectArtifact> buildArtifactLayout(
      Label target, List<? extends OutputArtifact> artifacts) {
    final var resultBuilder = ImmutableMap.<Path, ProjectProto.ProjectArtifact>builder();
    for (OutputArtifact artifact : artifacts) {
      resultBuilder.put(
          artifact.getArtifactPath(),
          ProjectProto.ProjectArtifact.newBuilder()
              .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest(artifact.getDigest()))
              .setTarget(target.toString())
              .setTransform(ProjectProto.ProjectArtifact.ArtifactTransform.COPY)
              .build());
    }
    return resultBuilder.build();
  }

  private void updateArtifactDirectory(
      ProjectProto.ArtifactDirectoryContents artifactDirectoryContents) {
    try {
      new ArtifactDirectoryUpdate(
        buildArtifactCache,
              workspaceRoot,
              runfilesDirectory,
              artifactDirectoryContents)
          .update();
    } catch (IOException e) {
      throw new IllegalStateException("Exception while updating artifact directory", e);
    }
  }

  /**
   * Builds {@link ProjectProto.ArtifactDirectoryContents} from a map from artifact label -> artifact
   * path -> project artifact.
   */
  private static ProjectProto.ArtifactDirectoryContents buildArtifactDirectoryContents(
      Map<Pair<Label, RuntimeArtifactKind>, Map<Path, ProjectProto.ProjectArtifact>> artifacts) {
    final var artifactDirectoryContents = ProjectProto.ArtifactDirectoryContents.newBuilder();
    for (final var entry : artifacts.entrySet()) {
      final var key = entry.getKey();
      for (final var artifactPathAndDigest : entry.getValue().entrySet()) {
        final var artifactPath = artifactPathAndDigest.getKey();
        final var artifact = artifactPathAndDigest.getValue();
        artifactDirectoryContents.putContents(
            getArtifactLocalPath(key.first, key.second, artifactPath).toString(), artifact);
      }
    }
    return artifactDirectoryContents.build();
  }

  /**
   * Generates the local artifact path from the target and artifact path. Local Artifact path ->
   * Target + SEPARATOR_DIR_NAME + artifactPath.
   */
  public static Path getArtifactLocalPath(Label target, RuntimeArtifactKind artifactKind, Path artifactPath) {
    return target.toFilePath().resolve(Path.of(artifactKind.name())).resolve(Path.of(SEPARATOR_DIR_NAME)).resolve(artifactPath);
  }
}
