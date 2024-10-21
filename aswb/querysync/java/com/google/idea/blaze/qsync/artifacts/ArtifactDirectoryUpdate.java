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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Supplier;
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
import com.google.idea.blaze.qsync.query.PackageSet;
import com.google.protobuf.ExtensionRegistryLite;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/**
 * Performs a single directory update based on a {@link ArtifactDirectoryContents} proto.
 *
 * <p>Ensures that the directory contents exactly match the proto spec, deleting any entries not
 * listed in there.
 */
public class ArtifactDirectoryUpdate {

  private final BuildArtifactCache artifactCache;
  private final Path workspaceRoot;
  private final Path root;
  private final ArtifactDirectoryContents contents;
  private final Set<Path> updatedPaths;
  private final FileTransform stripGeneratedSourcesTransform;
  private final Supplier<Boolean> buildGeneratedSrcJars;

  public ArtifactDirectoryUpdate(
      BuildArtifactCache artifactCache,
      Path workspaceRoot,
      Path root,
      ArtifactDirectoryContents contents,
      FileTransform stripGeneratedSourcesTransform,
      Supplier<Boolean> buildGeneratedSrcJars) {
    this.artifactCache = artifactCache;
    this.workspaceRoot = workspaceRoot;
    this.root = root;
    this.contents = contents;
    updatedPaths = Sets.newHashSet();
    this.stripGeneratedSourcesTransform = stripGeneratedSourcesTransform;
    this.buildGeneratedSrcJars = buildGeneratedSrcJars;
  }

  public ArtifactDirectoryUpdate(
      BuildArtifactCache artifactCache,
      Path workspaceRoot,
      Path root,
      ArtifactDirectoryContents contents,
      FileTransform stripGeneratedSourcesTransform,
      Boolean buildGeneratedSrcJarsVal) {
    this(
        artifactCache,
        workspaceRoot,
        root,
        contents,
        stripGeneratedSourcesTransform,
        () -> buildGeneratedSrcJarsVal);
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
          if (buildGeneratedSrcJars.get()) {
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
    PackageSet wanted =
        new PackageSet(
            contents.getContentsMap().keySet().stream().map(Path::of).collect(toImmutableSet()));
    Set<Path> toDelete = Sets.newHashSet();
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (dir.equals(root)) {
              return FileVisitResult.CONTINUE;
            }
            Path rel = root.relativize(dir);
            if (wanted.contains(rel)) {
              return FileVisitResult.SKIP_SUBTREE;
            } else if (wanted.getSubpackages(rel).isEmpty()) {
              toDelete.add(dir);
              return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            Path rel = root.relativize(file);
            if (!wanted.contains(rel)) {
              toDelete.add(file);
            }
            return FileVisitResult.CONTINUE;
          }
        });
    for (Path p : toDelete) {
      MoreFiles.deleteRecursively(p, RecursiveDeleteOption.ALLOW_INSECURE);
    }
  }
}
