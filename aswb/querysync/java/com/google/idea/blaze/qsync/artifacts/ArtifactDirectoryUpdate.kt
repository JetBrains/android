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
package com.google.idea.blaze.qsync.artifacts

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Lists
import com.google.common.io.MoreFiles
import com.google.common.io.RecursiveDeleteOption
import com.google.common.util.concurrent.Uninterruptibles
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.common.artifact.BuildArtifactCache
import com.google.idea.blaze.common.artifact.CachedArtifact
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectoryContents
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectoryContents.Companion.getDefaultInstance
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectoryContents.Companion.readFrom
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact.ArtifactTransform
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.jvm.optionals.getOrNull
import kotlin.math.min
import kotlin.streams.asSequence
import kotlin.time.measureTimedValue

/**
 * Performs a single directory update based on a [ArtifactDirectoryContents] proto.
 *
 *
 * Ensures that the directory contents exactly match the proto spec, deleting any entries not
 * listed in there.
 */
class ArtifactDirectoryUpdate(
  private val name: String,
  private val artifactCache: BuildArtifactCache,
  private val root: Path,
  private val contents: ArtifactDirectoryContents,
) {
  private val _updatedPaths: MutableSet<Path> = hashSetOf()

  @VisibleForTesting
  val updatedPaths: Set<Path> = _updatedPaths

  @Throws(IOException::class)
  fun update(context: Context<*>): Set<Label> {
    Files.createDirectories(root)
    val contentsProtoPath = getContentsFile(root)
    val oldContentsProtoPath = getOldContentsFile(root)

    runCatching { Files.deleteIfExists(oldContentsProtoPath) } // Ignore errors.

    // Any exceptions that occur when updating individual entries are caught and added here.
    // If any entry fails, we will throw an exception at the end with all such failures added as
    // suppressed exceptions. This ensures we update as much of the store as we can and should give
    // better behaviour in the event of problems.
    val exceptions = mutableListOf<Exception>()

    return measureTimedValue {
      val existingContents: ArtifactDirectoryContents =
        if (Files.exists(contentsProtoPath)) {
          try {
            Files.newInputStream(contentsProtoPath).use { input -> readFrom(input) }
          }
          catch (ex: IOException) {
            context.output(PrintOutput.error("Failed to load $contentsProtoPath\nIgnoring and trying to rebuild the directory.\n$ex"))
            getDefaultInstance()
            // Ignore corrupted contents files. In the worst case we will delete artifact files and won't be able to copy them again as they
            // already expired in the cache. This, however,won't prevent syncing/building dependencies as an exception would do.
          }
          catch (ex: RuntimeException) {
            context.output(PrintOutput.error("Failed to load $contentsProtoPath\nIgnoring and trying to rebuild the directory.\n$ex"))
            getDefaultInstance()
          }
            .also {
              // we delete this now so that if something fails mid way through the below, then we should
              // recover next time by re-creating the entire contents of the dir.
              Files.delete(contentsProtoPath)
            }
        }
        else {
          getDefaultInstance()
        }
      val incompleteTargets = mutableSetOf<Label>()

      for (destAndArtifact in contents.contents.entries) {
        try {
          val artifact = destAndArtifact.value
          if (!updateOneFile(
              root.resolve(Path.of(destAndArtifact.key)),
              existingContents.contents[destAndArtifact.key],
              artifact
            )
          ) {
            incompleteTargets.add(artifact.target)
          }
        }
        catch (e: BuildException) {
          exceptions.add(e)
        }
        catch (e: IOException) {
          exceptions.add(e)
        }
      }

      // we don't rely on the existing contents proto here so that we clean up properly if something
      // else has put things in the dir.
      try {
        deleteUnnecessaryFiles()
      }
      catch (e: IOException) {
        exceptions.add(e)
      }

      if (contents.contents.isEmpty()) {
        // The directory is empty. Delete it.
        Files.deleteIfExists(contentsProtoPath)
        Files.deleteIfExists(root)
        emptySet()
      }
      else {
        try {
          Files.newOutputStream(contentsProtoPath, StandardOpenOption.CREATE).use { out ->
            contents.writeTo(out)
          }
        }
        catch (e: IOException) {
          exceptions.add(e)
        }
        incompleteTargets
      }
    }
      .also {
        if (it.duration.inWholeMilliseconds > 500) {
          context.output<PrintOutput?>(PrintOutput.log("Took %s to update %s", it.duration, name))
        }
      }.value
  }

  private fun needsUpdate(
    existing: ProjectArtifact?, updated: ProjectArtifact,
  ): Boolean {
    if (existing == null) {
      return true
    }
    if (updated != existing) {
      return true
    }
    return false
  }

  /**
   * Updates a single file.
   *
   * @return `false` if the artifact was not present (e.g. expired from the cache).
   */
  @Throws(BuildException::class, IOException::class)
  private fun updateOneFile(
    dest: Path,
    existing: ProjectArtifact?,
    srcArtifact: ProjectArtifact,
  ): Boolean {
    if (needsUpdate(existing, srcArtifact)) {
      if (Files.exists(dest)) {
        MoreFiles.deleteRecursively(dest, RecursiveDeleteOption.ALLOW_INSECURE)
      }
    }
    if (!Files.exists(dest)) {
      Files.createDirectories(dest.parent)
      val src = getCachedArtifact(srcArtifact) ?: return false
      when (srcArtifact.transform) {
        ArtifactTransform.COPY ->
          _updatedPaths.addAll(FileTransform.COPY.copyWithTransform(src, dest))

        ArtifactTransform.UNZIP ->
          _updatedPaths.addAll(FileTransform.UNZIP.copyWithTransform(src, dest))
      }
    }
    return true
  }

  @Throws(BuildException::class)
  private fun getCachedArtifact(artifact: ProjectArtifact): CachedArtifact? {
    // TODO(mathewi) It would probably be better to parallelize this so get better performance
    //   in the case that not all artifacts are ready in the cache.
    return artifactCache
      .get(artifact.buildArtifact.digest)
      .getOrNull()
      ?.let {
        runCatching { Uninterruptibles.getUninterruptibly(it) }
          .getOrElse { throw BuildException("Failed to fetch artifact $artifact", it) }
      }
  }

  @Throws(IOException::class)
  private fun deleteUnnecessaryFiles() {
    if (!Files.exists(root)) {
      return
    }
    val toDelete: List<Path> =
      Files.walk(root).use { fileStream ->
        val dot =
          Path.of(".") // Path.of("abc").startsWith(Path.of("")) does not work but with "./abc" and "./" it does.
        val wanted = contents.contents.keys.asSequence().map { dot.resolve(it) }
        val present =
          fileStream.asSequence()
            .map { root.relativize(it) }
            .map { dot.resolve(it) }
            .filter { dot != it }
        computeFilesToDelete(present, wanted)
      }
    for (p in Lists.reverse(toDelete)) {
      Files.delete(root.resolve(p))
    }
  }

  companion object {
    @JvmStatic
    fun getContentsFile(artifactDir: Path): Path = artifactDir.resolveSibling("${artifactDir.fileName}.state")
    private fun getOldContentsFile(artifactDir: Path) = artifactDir.resolveSibling("${artifactDir.fileName}.contents")

    /****
     * Returns the list of currently present files/directories that are neither parents nor children of wanted files/directories.
     */
    @VisibleForTesting
    fun computeFilesToDelete(presentStream: Sequence<Path>, wantedStream: Sequence<Path>): List<Path> {
      val present = presentStream.sortedWith(::comparePathsByNames).toList()
      val wanted = wantedStream.sortedWith(::comparePathsByNames).toList()
      return buildList {
        var wantedIndex = 0;
        var presentIndex = 0;

        while (presentIndex < present.size) {
          val currentWanted = wanted.getOrNull(wantedIndex)
          val currentPresent = present[presentIndex]
          val cr = if (currentWanted != null) comparePathsByNames(currentWanted, currentPresent) else 1
          if (currentWanted != null && cr < 0) {
            if (currentPresent.startsWith(currentWanted)) {
              presentIndex++;
            }
            else {
              wantedIndex++;
            }
          }
          else {
            if (currentWanted == null || !currentWanted.startsWith(currentPresent)) {
              this@buildList.add(currentPresent);
            }
            presentIndex++;
          }
        }
      }
    }

    private fun comparePathsByNames(p1: Path, p2: Path): Int {
      val nc = min(p1.nameCount, p2.nameCount);
      for (i in (0 until nc)) {
        val c = p1.getName(i) compareTo p2.getName(i)
        if (c != 0) {
          return c
        }
      }
      return p1.nameCount compareTo p2.nameCount
    }
  }
}

