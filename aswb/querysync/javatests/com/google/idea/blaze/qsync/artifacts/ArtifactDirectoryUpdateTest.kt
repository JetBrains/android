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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.io.Closer
import com.google.common.truth.Truth
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectoryContents
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact.ArtifactTransform
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ArtifactDirectoryUpdateTest {
  @get:Rule
  var tmpDir: TemporaryFolder = TemporaryFolder()

  lateinit var root: Path
  var workspaceRoot: Path? = null
  var cacheDir: Path? = null
  var cache: MockArtifactCache? = null

  @Before
  @Throws(Exception::class)
  fun initDirs() {
    root = tmpDir.root.toPath().resolve("artifact_dir")
    workspaceRoot = tmpDir.root.toPath().resolve("workspace")
    Files.createDirectory(workspaceRoot)
    cacheDir = tmpDir.root.toPath().resolve("cache")
    cache = MockArtifactCache(cacheDir)
  }

  @Test
  @Throws(IOException::class)
  fun copy_build_artifact_into_empty_dir() {
    val update =
      ArtifactDirectoryUpdate(
        cache,
        root,
        ArtifactDirectoryContents.newBuilder()
          .putContents(
            "somefile.txt",
            ProjectArtifact.newBuilder()
              .setTransform(ArtifactTransform.COPY)
              .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest("abcde"))
              .build()
          )
          .build()
      )
    update.update()

    Truth.assertThat(readContents()).containsExactly(Path.of("somefile.txt"))
    Truth.assertThat(Files.readAllLines(root.resolve("somefile.txt"))).containsExactly("abcde")
  }

  @Test
  @Throws(IOException::class)
  fun unzip_into_empty_dir() {
    writeZipFile(
      cacheDir!!.resolve("zipdigest"),
      ImmutableMap.of(
        "zip/path/file1.txt", "file1 contents",
        "zip/path/file2.txt", "file2 contents"
      )
    )
    val update =
      ArtifactDirectoryUpdate(
        cache,
        root,
        ArtifactDirectoryContents.newBuilder()
          .putContents(
            "unzipped",
            ProjectArtifact.newBuilder()
              .setTransform(ArtifactTransform.UNZIP)
              .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest("zipdigest"))
              .build()
          )
          .build()
      )
    update.update()

    Truth.assertThat(readContents())
      .containsExactly(
        Path.of("unzipped/zip/path/file1.txt"), Path.of("unzipped/zip/path/file2.txt")
      )
    Truth.assertThat(Files.readAllLines(root.resolve("unzipped/zip/path/file1.txt")))
      .containsExactly("file1 contents")
    Truth.assertThat(Files.readAllLines(root.resolve("unzipped/zip/path/file2.txt")))
      .containsExactly("file2 contents")
    Truth.assertThat(update.updatedPaths)
      .containsExactly(
        root.resolve("unzipped/zip/path/file1.txt"),
        root.resolve("unzipped/zip/path/file2.txt")
      )
  }

  @Test
  @Throws(IOException::class)
  fun empty_proto_existing_dir_contents_deleted() {
    createFiles("rootArtifact", "subdir/artifact")
    Files.createDirectory(root.resolve("emptyDir"))

    val update =
      ArtifactDirectoryUpdate(
        cache,
        root,
        ArtifactDirectoryContents.getDefaultInstance()
      )
    update.update()

    Truth.assertThat(Files.exists(root)).isFalse()
    Truth.assertThat(update.updatedPaths).isEmpty()
  }

  @Test
  @Throws(IOException::class)
  fun empty_proto_existing_deleted() {
    // first, populate the dir
    var update =
      ArtifactDirectoryUpdate(
        cache,
        root,
        ArtifactDirectoryContents.newBuilder()
          .putContents(
            "somefile.txt",
            ProjectArtifact.newBuilder()
              .setTransform(ArtifactTransform.COPY)
              .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest("abcde"))
              .build()
          )
          .build()
      )
    update.update()

    val contentsProtoPath = root.resolveSibling(root.fileName.toString() + ".contents")

    Truth.assertThat(Files.exists(contentsProtoPath)).isTrue()

    update =
      ArtifactDirectoryUpdate(
        cache,
        root,
        ArtifactDirectoryContents.getDefaultInstance()
      )
    update.update()

    Truth.assertThat(Files.exists(root)).isFalse()
    Truth.assertThat(Files.exists(contentsProtoPath)).isFalse()
  }

  @Test
  @Throws(IOException::class)
  fun unzip_replaces_existing_files() {
    createFiles("dir/file1.txt", "dir/subdir/file2.txt")
    writeZipFile(
      cacheDir!!.resolve("zipdigest"),
      ImmutableMap.of(
        "file3.txt", "file3 contents",
        "subdir2/file4.txt", "file4 contents"
      )
    )
    val update =
      ArtifactDirectoryUpdate(
        cache,
        root,
        ArtifactDirectoryContents.newBuilder()
          .putContents(
            "dir",
            ProjectArtifact.newBuilder()
              .setTransform(ArtifactTransform.UNZIP)
              .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest("zipdigest"))
              .build()
          )
          .build()
      )
    update.update()

    Truth.assertThat(readContents())
      .containsExactly(Path.of("dir/file3.txt"), Path.of("dir/subdir2/file4.txt"))
    Truth.assertThat(update.updatedPaths)
      .containsExactly(root.resolve("dir/file3.txt"), root.resolve("dir/subdir2/file4.txt"))
  }

  @Test
  @Throws(IOException::class)
  fun replace_file_with_dir() {
    createFiles("dir")
    writeZipFile(
      cacheDir!!.resolve("zipdigest"),
      ImmutableMap.of("file1.txt", "file1 contents")
    )
    val update =
      ArtifactDirectoryUpdate(
        cache,
        root,
        ArtifactDirectoryContents.newBuilder()
          .putAllContents(
            ImmutableMap.of(
              "dir",
              ProjectArtifact.newBuilder()
                .setTransform(ArtifactTransform.UNZIP)
                .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest("zipdigest"))
                .build()
            )
          )
          .build()
      )
    update.update()

    Truth.assertThat(readContents()).containsExactly(Path.of("dir/file1.txt"))
    Truth.assertThat(update.updatedPaths).containsExactly(root.resolve("dir/file1.txt"))
  }

  @Test
  @Throws(IOException::class)
  fun replace_dir_with_file() {
    createFiles("dir/file1.txt")
    val update =
      ArtifactDirectoryUpdate(
        cache,
        root,
        ArtifactDirectoryContents.newBuilder()
          .putAllContents(
            ImmutableMap.of(
              "dir",
              ProjectArtifact.newBuilder()
                .setTransform(ArtifactTransform.COPY)
                .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest("abcde"))
                .build()
            )
          )
          .build()
      )
    update.update()

    Truth.assertThat(readContents()).containsExactly(Path.of("dir"))
    Truth.assertThat(Files.readAllLines(root.resolve("dir"))).containsExactly("abcde")
    Truth.assertThat(update.updatedPaths).containsExactly(root.resolve("dir"))
  }

  @Test
  @Throws(IOException::class)
  fun keep_disjoint_files() {
    createFiles("dir/file1.txt", "dir/subdir/file2.txt")
    val update =
      ArtifactDirectoryUpdate(
        cache,
        root,
        ArtifactDirectoryContents.newBuilder()
          .putContents(
            "dir/file3.txt",
            ProjectArtifact.newBuilder()
              .setTransform(ArtifactTransform.COPY)
              .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest("abcde"))
              .build()
          )
          .putContents(
            "dir/subdir2/file4.txt",
            ProjectArtifact.newBuilder()
              .setTransform(ArtifactTransform.COPY)
              .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest("abcdf"))
              .build()
          )
          .build()
      )
    update.update()

    Truth.assertThat(readContents())
      .containsExactly(Path.of("dir/file3.txt"), Path.of("dir/subdir2/file4.txt"))
    Truth.assertThat(update.updatedPaths)
      .containsExactly(root.resolve("dir/file3.txt"), root.resolve("dir/subdir2/file4.txt"))
  }

  @Test
  @Throws(IOException::class)
  fun unchanged_files_not_requested_from_cache() {
    val populate =
      ArtifactDirectoryUpdate(
        cache,
        root,
        ArtifactDirectoryContents.newBuilder()
          .putContents(
            "file1.txt",
            ProjectArtifact.newBuilder()
              .setTransform(ArtifactTransform.COPY)
              .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest("file1digest"))
              .build()
          )
          .putContents(
            "file2.txt",
            ProjectArtifact.newBuilder()
              .setTransform(ArtifactTransform.COPY)
              .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest("file2digest"))
              .build()
          )
          .build()
      )
    populate.update()
    cache!!.takeRequestedDigests()

    // re-run an equivalent update
    val update =
      ArtifactDirectoryUpdate(
        cache,
        root,
        ArtifactDirectoryContents.newBuilder()
          .putContents(
            "file1.txt",
            ProjectArtifact.newBuilder()
              .setTransform(ArtifactTransform.COPY)
              .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest("file1digest"))
              .build()
          )
          .putContents(
            "file2.txt",
            ProjectArtifact.newBuilder()
              .setTransform(ArtifactTransform.COPY)
              .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest("file2digest"))
              .build()
          )
          .build()
      )
    update.update()
    Truth.assertThat(update.updatedPaths).isEmpty()
    Truth.assertThat(cache!!.takeRequestedDigests()).isEmpty()
  }

  @Test
  @Throws(IOException::class)
  fun partial_update() {
    val populate =
      ArtifactDirectoryUpdate(
        cache,
        root,
        ArtifactDirectoryContents.newBuilder()
          .putContents(
            "file1.txt",
            ProjectArtifact.newBuilder()
              .setTransform(ArtifactTransform.COPY)
              .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest("abcd"))
              .build()
          )
          .putContents(
            "file2.txt",
            ProjectArtifact.newBuilder()
              .setTransform(ArtifactTransform.COPY)
              .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest("defg"))
              .build()
          )
          .build()
      )
    populate.update()
    cache!!.takeRequestedDigests()

    // 1 file is changed, another is identical:
    val update =
      ArtifactDirectoryUpdate(
        cache,
        root,
        ArtifactDirectoryContents.newBuilder()
          .putContents(
            "file1.txt",
            ProjectArtifact.newBuilder()
              .setTransform(ArtifactTransform.COPY)
              .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest("abcd"))
              .build()
          )
          .putContents(
            "file2.txt",
            ProjectArtifact.newBuilder()
              .setTransform(ArtifactTransform.COPY)
              .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest("efgh"))
              .build()
          )
          .build()
      )
    update.update()
    Truth.assertThat(update.updatedPaths).containsExactly(root.resolve("file2.txt"))
    Truth.assertThat(cache!!.takeRequestedDigests()).containsExactly("efgh")
    Truth.assertThat(Files.readAllLines(root.resolve("file2.txt"))).containsExactly("efgh")
  }

  @Test
  fun computeFilesToDelete_filesDeleted() {
    val toDelete = ArtifactDirectoryUpdate.computeFilesToDelete(
      paths("a", "b", "c").stream(),
      paths("a", "b").stream()
    )
    Truth.assertThat(toDelete).isEqualTo(
      paths("c")
    )
  }

  @Test
  fun computeFilesToDelete_filesDeleted_parentsRemain() {
    val toDelete = ArtifactDirectoryUpdate.computeFilesToDelete(
      paths("a", "a/b", "a/c", "a/d", "a/e", "e").stream(),
      paths("a/b", "a/d", "e").stream()
    )
    Truth.assertThat(toDelete).isEqualTo(
      paths("a/c", "a/e")
    )
  }

  @Test
  fun computeFilesToDelete_filesDeleted_childrenRemain() {
    val toDelete = ArtifactDirectoryUpdate.computeFilesToDelete(
      paths(
        "a",
        "a/b",
        "a/c",
        "d",
        "d/e",
        "d/e/f",
        "d/g",
        "d/g/h"
      ).stream(),
      paths("a", "d/e", "i").stream()
    )
    Truth.assertThat(toDelete).isEqualTo(
      paths("d/g", "d/g/h")
    )
  }

  @Throws(IOException::class)
  private fun readContents(): ImmutableList<Path> {
    val contents = ImmutableList.builder<Path>()
    Files.walkFileTree(
      root,
      object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
          contents.add(root.relativize(file))
          return FileVisitResult.CONTINUE
        }
      })
    return contents.build()
  }

  @Throws(IOException::class)
  private fun createFiles(vararg paths: String) {
    for (path in paths) {
      val dest = root.resolve(path)
      Files.createDirectories(dest.parent)
      Files.write(dest, ImmutableList.of(path), StandardCharsets.UTF_8)
    }
  }

  @Throws(IOException::class)
  private fun writeZipFile(dest: Path, contents: Map<String, String>) {
    Closer.create().use { c ->
      val fos = c.register<FileOutputStream>(FileOutputStream(dest.toFile()))
      val zipOut = c.register<ZipOutputStream>(ZipOutputStream(fos))
      for (entry in contents.entries) {
        val zipEntry = ZipEntry(entry.key)
        zipOut.putNextEntry(zipEntry)
        zipOut.write(entry.value.toByteArray(StandardCharsets.UTF_8))
      }
    }
  }

  companion object {
    private fun paths(vararg paths: String): List<Path> {
      val dot = Path.of(".")
      return paths.map { Path.of(it) }.map { dot.resolve(it) }
    }
  }
}
