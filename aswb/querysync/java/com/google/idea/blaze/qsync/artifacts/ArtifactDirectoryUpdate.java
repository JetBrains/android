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
package com.google.idea.blaze.qsync.artifacts;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.Math.min;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectoryContents;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact;
import com.google.protobuf.ExtensionRegistryLite;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Performs a single directory update based on a {@link ArtifactDirectoryContents} proto.
 *
 * <p>Ensures that the directory contents exactly match the proto spec, deleting any entries not
 * listed in there.
 */
public class ArtifactDirectoryUpdate {
  private final static Logger LOG = Logger.getLogger(ArtifactDirectoryUpdate.class.getSimpleName());

  private final BuildArtifactCache artifactCache;
  private final Path workspaceRoot;
  private final Path root;
  private final ArtifactDirectoryContents contents;
  private final Set<Path> updatedPaths;
  private final FileTransform stripGeneratedSourcesTransform;
  private final boolean buildGeneratedSrcJars;

  public ArtifactDirectoryUpdate(
      BuildArtifactCache artifactCache,
      Path workspaceRoot,
      Path root,
      ArtifactDirectoryContents contents,
      FileTransform stripGeneratedSourcesTransform,
      boolean buildGeneratedSrcJars) {
    this.artifactCache = artifactCache;
    this.workspaceRoot = workspaceRoot;
    this.root = root;
    this.contents = contents;
    updatedPaths = Sets.newHashSet();
    this.stripGeneratedSourcesTransform = stripGeneratedSourcesTransform;
    this.buildGeneratedSrcJars = buildGeneratedSrcJars;
  }

  public static Path getContentsFile(Path artifactDir) {
    return artifactDir.resolveSibling(artifactDir.getFileName() + ".contents");
  }

  public ImmutableSet<Label> update() throws IOException {
    Files.createDirectories(root);
    Path contentsProtoPath = getContentsFile(root);

    // Any exceptions that occur when updating individual entries are caught and added here.
    // If any entry fails, we will throw an exception at the end with all such failures added as
    // suppressed exceptions. This ensures we update as much of the store as we can and should give
    // better behaviour in the event of problems.
    List<Exception> exceptions = Lists.newArrayList();

    ArtifactDirectoryContents existingContents;
    if (Files.exists(contentsProtoPath)) {
      try (InputStream in = Files.newInputStream(contentsProtoPath)) {
        existingContents =
            ArtifactDirectoryContents.parseFrom(in, ExtensionRegistryLite.getEmptyRegistry());
      }
      // we delete this now so that if something fails mid way through the below, then we should
      // recover next time by re-creating the entire contents of the dir.
      Files.delete(contentsProtoPath);
    } else {
      existingContents = ArtifactDirectoryContents.getDefaultInstance();
    }

    ImmutableSet.Builder<Label> incompleteTargets = ImmutableSet.builder();

    for (Map.Entry<String, ProjectProto.ProjectArtifact> destAndArtifact :
        contents.getContentsMap().entrySet()) {
      try {
        ProjectArtifact artifact = destAndArtifact.getValue();
        if (!updateOneFile(
            root.resolve(Path.of(destAndArtifact.getKey())),
            existingContents.getContentsMap().get(destAndArtifact.getKey()),
            artifact)) {
          incompleteTargets.add(Label.of(artifact.getTarget()));
        }
      } catch (BuildException | IOException e) {
        exceptions.add(e);
      }
    }

    // we don't rely on the existing contents proto here so that we clean up properly if something
    // else has put things in the dir.
    try {
      deleteUnnecessaryFiles();
    } catch (IOException e) {
      exceptions.add(e);
    }

    if (contents.getContentsCount() == 0) {
      // The directory is empty. Delete it.
      Files.deleteIfExists(contentsProtoPath);
      Files.deleteIfExists(root);
      return ImmutableSet.of();
    } else {
      try (OutputStream out = Files.newOutputStream(contentsProtoPath, StandardOpenOption.CREATE)) {
        contents.writeTo(out);
      } catch (IOException e) {
        exceptions.add(e);
      }
      if (!exceptions.isEmpty()) {
        IOException e = new IOException("Directory update for " + root + " failed");
        exceptions.forEach(e::addSuppressed);
        throw e;
      }
      return incompleteTargets.build();
    }
  }

  /**
   * Returns the set of paths that were modified by this update. This can be used to notify the IDE
   * of modified files so it can update it's internal caches.
   *
   * <p>This method must be called after {@link #update()}.
   */
  public ImmutableSet<Path> getUpdatedPaths() {
    return ImmutableSet.copyOf(updatedPaths);
  }

  private boolean needsUpdate(
      @Nullable ProjectProto.ProjectArtifact existing, ProjectProto.ProjectArtifact updated) {
    if (existing == null) {
      return true;
    }
    if (!updated.equals(existing)) {
      return true;
    }
    if (!updated.getWorkspaceRelativePath().isEmpty()) {
      // the existing and updated are identical, but since it's a workspace file it may have
      // changed since we last used it.
      // TODO(mathewi) we could condider using a readonly snapshot path here in the case that the
      //   VCS supports it to avoid updating files from the workspace that have not actually changed
      return true;
    }
    return false;
  }

  /**
   * Updates a single file.
   *
   * @return {@code false} if the artifact was not present (e.g. expired from the cache).
   */
  private boolean updateOneFile(
      Path dest,
      @Nullable ProjectProto.ProjectArtifact existing,
      ProjectProto.ProjectArtifact srcArtifact)
      throws BuildException, IOException {
    if (needsUpdate(existing, srcArtifact)) {
      if (Files.exists(dest)) {
        MoreFiles.deleteRecursively(dest, RecursiveDeleteOption.ALLOW_INSECURE);
      }
    }
    if (!Files.exists(dest)) {
      Files.createDirectories(dest.getParent());
      Optional<CachedArtifact> src = getCachedArtifact(srcArtifact);
      if (src.isEmpty()) {
        return false;
      }
      switch (srcArtifact.getTransform()) {
        case COPY:
          updatedPaths.addAll(FileTransform.COPY.copyWithTransform(src.get(), dest));
          break;
        case UNZIP:
          updatedPaths.addAll(FileTransform.UNZIP.copyWithTransform(src.get(), dest));
          break;
        case STRIP_SUPPORTED_GENERATED_SOURCES:
          if (buildGeneratedSrcJars) {
            updatedPaths.addAll(stripGeneratedSourcesTransform.copyWithTransform(src.get(), dest));
          } else {
            updatedPaths.addAll(FileTransform.COPY.copyWithTransform(src.get(), dest));
          }
          break;
        default:
          throw new IllegalArgumentException(
              "Invalid transform " + srcArtifact.getTransform() + " in " + srcArtifact);
      }
    }
    return true;
  }

  private Optional<CachedArtifact> getCachedArtifact(ProjectProto.ProjectArtifact artifact)
      throws BuildException {
    if (artifact.hasBuildArtifact()) {
      // TODO(mathewi) It would probably be better to parallelize this so get better performance
      //   in the case that not all artifacts are ready in the cache.
      Optional<ListenableFuture<CachedArtifact>> artifactFuture =
          artifactCache.get(artifact.getBuildArtifact().getDigest());
      if (artifactFuture.isEmpty()) {
        return Optional.empty();
      }
      try {
        return Optional.of(Uninterruptibles.getUninterruptibly(artifactFuture.get()));
      } catch (ExecutionException e) {
        throw new BuildException("Failed to fetch artifact " + artifact, e);
      }
    } else if (!artifact.getWorkspaceRelativePath().isEmpty()) {
      // TODO(mathewi) using the workspace root here means this could fail if the file is no longer
      //    present in the workspace. We should support using a readonly workspace snapshot if the
      //    VCS supports that, and probably also fail gracefully (emit a warning?) in this case.
      Path srcFile = workspaceRoot.resolve(artifact.getWorkspaceRelativePath());
      if (!Files.exists(srcFile)) {
        return Optional.empty();
      }
      return Optional.of(new CachedArtifact(srcFile));
    } else {
      throw new IllegalArgumentException("Invalid artifact: " + artifact);
    }
  }

  private void deleteUnnecessaryFiles() throws IOException {
    final List<Path> toDelete;
    if (!Files.exists(root)) {
      return;
    }
    try (final var fileStream = Files.walk(root)) {
      final var dot = Path.of("."); // Path.of("abc").startsWith(Path.of("")) does not work but with "./abc" and "./" it does.
      final var wanted = contents.getContentsMap().keySet().stream().map(dot::resolve);
      final var present = fileStream.map(root::relativize).filter(it -> !root.equals(it)).map(dot::resolve);
      toDelete = computeFilesToDelete(present, wanted);
    }
    for (Path p : Lists.reverse(toDelete)) {
      Files.delete(root.resolve(p));
    }
  }

  /**
   * Returns the list of currently present files/directories that are neither parents nor children of wanted files/directories.
   */
  @VisibleForTesting
  public static List<Path> computeFilesToDelete(Stream<Path> presentStream, Stream<Path> wantedStream) {
    final var present = presentStream.sorted(ArtifactDirectoryUpdate::comparePathsByNames).collect(toImmutableList());
    final var wanted = wantedStream.sorted(ArtifactDirectoryUpdate::comparePathsByNames).collect(toImmutableList());
    final var toDelete = new ArrayList<Path>();
    int wantedIndex = 0;
    int presentIndex = 0;

    while (presentIndex < present.size()) {
      final var currentWanted = wantedIndex < wanted.size() ? wanted.get(wantedIndex) : (Path)null;
      final var currentPresent = present.get(presentIndex);
      final var cr = currentWanted != null ? comparePathsByNames(currentWanted, currentPresent) : 1;
      if (cr < 0) {
        if (currentPresent.startsWith(currentWanted)) {
          presentIndex++;
        } else {
          wantedIndex++;
        }
      }
      else {
        if (currentWanted == null || !currentWanted.startsWith(currentPresent)) {
          toDelete.add(currentPresent);
        }
        presentIndex++;
      }
    }
    return toDelete;
  }

  private static int comparePathsByNames(Path p1, Path p2) {
    final var nc = min(p1.getNameCount(), p2.getNameCount());
    for (int i = 0; i < nc; i++) {
      final var c = p1.getName(i).compareTo(p2.getName(i));
      if (c != 0) {
        return c;
      }
    }
    return Integer.compare(p1.getNameCount(), p2.getNameCount());
  }
}
