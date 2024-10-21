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
package com.google.idea.blaze.base.qsync.artifacts;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteSource;
import com.google.common.io.MoreFiles;
import com.google.idea.blaze.base.qsync.BazelDependencyBuilder;
import com.google.idea.blaze.base.qsync.FileRefresher;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.artifacts.ArtifactDirectoryUpdate;
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectories;
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectoryContents;
import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Maintains a set of directories inside the IDE project dir, with the contents of each determined
 * entirely by the proto proto.
 *
 * <p>For each directory given inside {@link ArtifactDirectories}, this class ensures that it's
 * contents exactly match the proto, including deleting any entries not present in the proto. Any
 * other directories inside the IDE project dir are ignored.
 */
public class ProjectArtifactStore {

  private static final Logger logger = Logger.getInstance(ProjectArtifactStore.class);

  private final Path projectDir;
  private final Path workspacePath;
  private final BuildArtifactCache artifactCache;
  private final FileRefresher fileRefresher;
  private final GeneratedSourcesStripper sourcesStripper;
  private final Path projectDirectoriesFile;

  public ProjectArtifactStore(
      Path projectDir,
      Path workspacePath,
      BuildArtifactCache artifactCache,
      FileRefresher fileRefresher,
      GeneratedSourcesStripper sourcesStripper) {
    this.projectDir = projectDir;
    this.workspacePath = workspacePath;
    this.artifactCache = artifactCache;
    this.fileRefresher = fileRefresher;
    this.sourcesStripper = sourcesStripper;
    this.projectDirectoriesFile = projectDir.resolve(".project-artifact-dirs");
  }

  private ImmutableSet<String> readPreviousProjectDirectories() {
    if (!Files.exists(projectDirectoriesFile)) {
      return ImmutableSet.of();
    }
    try {
      return ImmutableSet.copyOf(Files.readAllLines(projectDirectoriesFile));
    } catch (IOException ioe) {
      logger.warn("Cannot read " + projectDirectoriesFile, ioe);
      return ImmutableSet.of();
    }
  }

  private void writeProjectDirectories(Collection<String> paths) throws IOException {
    Files.write(projectDirectoriesFile, paths);
  }

  /**
   * The outcome of an {@link #update} call.
   *
   * @param incompleteTargets The set of targets for which build artifacts were missing.
   */
  public record UpdateResult(ImmutableSet<Label> incompleteTargets) {}

  public UpdateResult update(Context<?> context, QuerySyncProjectSnapshot graph)
      throws BuildException {
    List<IOException> exceptions = Lists.newArrayList();
    ImmutableSet.Builder<Path> updatedPaths = ImmutableSet.builder();
    Map<String, ArtifactDirectoryContents> toUpdate = Maps.newHashMap();
    toUpdate.putAll(graph.project().getArtifactDirectories().getDirectoriesMap());
    // add empty contents for any dirs that are no longer present, to ensure they're cleaned up:
    readPreviousProjectDirectories()
        .forEach(dir -> toUpdate.putIfAbsent(dir, ArtifactDirectoryContents.getDefaultInstance()));

    ImmutableSet.Builder<Label> incompleteTargets = ImmutableSet.builder();
    for (Map.Entry<String, ArtifactDirectoryContents> entry : toUpdate.entrySet()) {
      Path root = projectDir.resolve(entry.getKey());
      ArtifactDirectoryUpdate dirUpdate =
          new ArtifactDirectoryUpdate(
              artifactCache,
              workspacePath,
              root,
              entry.getValue(),
              sourcesStripper,
              BazelDependencyBuilder.buildGeneratedSrcJars::getValue);
      try {
        incompleteTargets.addAll(dirUpdate.update());
      } catch (IOException e) {
        exceptions.add(e);
      }
      updatedPaths.addAll(dirUpdate.getUpdatedPaths());
    }
    try {
      writeProjectDirectories(
          graph.project().getArtifactDirectories().getDirectoriesMap().keySet());
    } catch (IOException e) {
      exceptions.add(e);
    }
    fileRefresher.refreshFiles(context, updatedPaths.build());
    if (!exceptions.isEmpty()) {
      BuildException e = new BuildException("Artifact store update failed.");
      exceptions.stream().forEach(e::addSuppressed);
      throw e;
    }
    return new UpdateResult(incompleteTargets.build());
  }

  public ImmutableMap<String, ByteSource> getBugreportFiles() {
    ImmutableMap.Builder<String, ByteSource> bugreportFiles = ImmutableMap.builder();
    for (String name : readPreviousProjectDirectories()) {
      Path contentsFile = ArtifactDirectoryUpdate.getContentsFile(projectDir.resolve(name));
      if (Files.exists(contentsFile)) {
        bugreportFiles.put(
            contentsFile.getFileName().toString(), MoreFiles.asByteSource(contentsFile));
      }
    }
    return bugreportFiles.build();
  }

  @VisibleForTesting
  public void purgeForTest(Context<?> context) throws BuildException {
    update(context, QuerySyncProjectSnapshot.EMPTY);
  }
}
