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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closer;
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectoryContents;
import com.google.idea.blaze.qsync.project.ProjectProto.BuildArtifact;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact.ArtifactTransform;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ArtifactDirectoryUpdateTest {

  @Rule public TemporaryFolder tmpDir = new TemporaryFolder();

  Path root;
  Path workspaceRoot;
  Path cacheDir;
  MockArtifactCache cache;

  @Before
  public void initDirs() throws Exception {
    root = tmpDir.getRoot().toPath().resolve("artifact_dir");
    workspaceRoot = tmpDir.getRoot().toPath().resolve("workspace");
    createDirectory(workspaceRoot);
    cacheDir = tmpDir.getRoot().toPath().resolve("cache");
    cache = new MockArtifactCache(cacheDir);
  }

  @Test
  public void copy_build_artifact_into_empty_dir() throws IOException {
    ArtifactDirectoryUpdate update =
        new ArtifactDirectoryUpdate(
            cache,
            workspaceRoot,
            root,
            ArtifactDirectoryContents.newBuilder()
                .putContents(
                    "somefile.txt",
                    ProjectArtifact.newBuilder()
                        .setTransform(ArtifactTransform.COPY)
                        .setBuildArtifact(BuildArtifact.newBuilder().setDigest("abcde"))
                        .build())
                .build(),
            FileTransform.COPY,
            false);
    update.update();

    assertThat(readContents()).containsExactly(Path.of("somefile.txt"));
    assertThat(Files.readAllLines(root.resolve("somefile.txt"))).containsExactly("abcde");
  }

  @Test
  public void copy_source_artifact_into_empty_dir() throws IOException {
    Path workspacePath = workspaceRoot.resolve("workspace/path/to/file.txt");
    Files.createDirectories(workspacePath.getParent());
    Files.write(workspacePath, ImmutableList.of("workspace file contents"));
    ArtifactDirectoryUpdate update =
        new ArtifactDirectoryUpdate(
            cache,
            workspaceRoot,
            root,
            ArtifactDirectoryContents.newBuilder()
                .putContents(
                    "anotherfile.txt",
                    ProjectArtifact.newBuilder()
                        .setTransform(ArtifactTransform.COPY)
                        .setWorkspaceRelativePath("workspace/path/to/file.txt")
                        .build())
                .build(),
            FileTransform.COPY,
            false);
    update.update();

    assertThat(readContents()).containsExactly(Path.of("anotherfile.txt"));
    assertThat(Files.readAllLines(root.resolve("anotherfile.txt")))
        .containsExactly("workspace file contents");
    assertThat(update.getUpdatedPaths()).containsExactly(root.resolve("anotherfile.txt"));
  }

  @Test
  public void unzip_into_empty_dir() throws IOException {
    writeZipFile(
        cacheDir.resolve("zipdigest"),
        ImmutableMap.of(
            "zip/path/file1.txt", "file1 contents",
            "zip/path/file2.txt", "file2 contents"));
    ArtifactDirectoryUpdate update =
        new ArtifactDirectoryUpdate(
            cache,
            workspaceRoot,
            root,
            ArtifactDirectoryContents.newBuilder()
                .putContents(
                    "unzipped",
                    ProjectArtifact.newBuilder()
                        .setTransform(ArtifactTransform.UNZIP)
                        .setBuildArtifact(BuildArtifact.newBuilder().setDigest("zipdigest"))
                        .build())
                .build(),
            FileTransform.COPY,
            false);
    update.update();

    assertThat(readContents())
        .containsExactly(
            Path.of("unzipped/zip/path/file1.txt"), Path.of("unzipped/zip/path/file2.txt"));
    assertThat(Files.readAllLines(root.resolve("unzipped/zip/path/file1.txt")))
        .containsExactly("file1 contents");
    assertThat(Files.readAllLines(root.resolve("unzipped/zip/path/file2.txt")))
        .containsExactly("file2 contents");
    assertThat(update.getUpdatedPaths())
        .containsExactly(
            root.resolve("unzipped/zip/path/file1.txt"),
            root.resolve("unzipped/zip/path/file2.txt"));
  }

  @Test
  public void empty_proto_existing_dir_contents_deleted() throws IOException {
    createFiles("rootArtifact", "subdir/artifact");
    createDirectory(root.resolve("emptyDir"));

    ArtifactDirectoryUpdate update =
        new ArtifactDirectoryUpdate(
            cache,
            workspaceRoot,
            root,
            ArtifactDirectoryContents.getDefaultInstance(),
            FileTransform.COPY,
            false);
    update.update();

    assertThat(Files.exists(root)).isFalse();
    assertThat(update.getUpdatedPaths()).isEmpty();
  }

  @Test
  public void empty_proto_existing_deleted() throws IOException {
    // first, populate the dir
    ArtifactDirectoryUpdate update =
        new ArtifactDirectoryUpdate(
            cache,
            workspaceRoot,
            root,
            ArtifactDirectoryContents.newBuilder()
                .putContents(
                    "somefile.txt",
                    ProjectArtifact.newBuilder()
                        .setTransform(ArtifactTransform.COPY)
                        .setBuildArtifact(BuildArtifact.newBuilder().setDigest("abcde"))
                        .build())
                .build(),
            FileTransform.COPY,
            false);
    update.update();

    Path contentsProtoPath = root.resolveSibling(root.getFileName() + ".contents");

    assertThat(Files.exists(contentsProtoPath)).isTrue();

    update =
        new ArtifactDirectoryUpdate(
            cache,
            workspaceRoot,
            root,
            ArtifactDirectoryContents.getDefaultInstance(),
            FileTransform.COPY,
            false);
    update.update();

    assertThat(Files.exists(root)).isFalse();
    assertThat(Files.exists(contentsProtoPath)).isFalse();
  }

  @Test
  public void unzip_replaces_existing_files() throws IOException {
    createFiles("dir/file1.txt", "dir/subdir/file2.txt");
    writeZipFile(
        cacheDir.resolve("zipdigest"),
        ImmutableMap.of(
            "file3.txt", "file3 contents",
            "subdir2/file4.txt", "file4 contents"));
    ArtifactDirectoryUpdate update =
        new ArtifactDirectoryUpdate(
            cache,
            workspaceRoot,
            root,
            ArtifactDirectoryContents.newBuilder()
                .putContents(
                    "dir",
                    ProjectArtifact.newBuilder()
                        .setTransform(ArtifactTransform.UNZIP)
                        .setBuildArtifact(BuildArtifact.newBuilder().setDigest("zipdigest"))
                        .build())
                .build(),
            FileTransform.COPY,
            false);
    update.update();

    assertThat(readContents())
        .containsExactly(Path.of("dir/file3.txt"), Path.of("dir/subdir2/file4.txt"));
    assertThat(update.getUpdatedPaths())
        .containsExactly(root.resolve("dir/file3.txt"), root.resolve("dir/subdir2/file4.txt"));
  }

  @Test
  public void replace_file_with_dir() throws IOException {
    createFiles("dir");
    writeZipFile(cacheDir.resolve("zipdigest"), ImmutableMap.of("file1.txt", "file1 contents"));
    ArtifactDirectoryUpdate update =
        new ArtifactDirectoryUpdate(
            cache,
            workspaceRoot,
            root,
            ArtifactDirectoryContents.newBuilder()
                .putAllContents(
                    ImmutableMap.of(
                        "dir",
                        ProjectArtifact.newBuilder()
                            .setTransform(ArtifactTransform.UNZIP)
                            .setBuildArtifact(BuildArtifact.newBuilder().setDigest("zipdigest"))
                            .build()))
                .build(),
            FileTransform.COPY,
            false);
    update.update();

    assertThat(readContents()).containsExactly(Path.of("dir/file1.txt"));
    assertThat(update.getUpdatedPaths()).containsExactly(root.resolve("dir/file1.txt"));
  }

  @Test
  public void replace_dir_with_file() throws IOException {
    createFiles("dir/file1.txt");
    ArtifactDirectoryUpdate update =
        new ArtifactDirectoryUpdate(
            cache,
            workspaceRoot,
            root,
            ArtifactDirectoryContents.newBuilder()
                .putAllContents(
                    ImmutableMap.of(
                        "dir",
                        ProjectArtifact.newBuilder()
                            .setTransform(ArtifactTransform.COPY)
                            .setBuildArtifact(BuildArtifact.newBuilder().setDigest("abcde"))
                            .build()))
                .build(),
            FileTransform.COPY,
            false);
    update.update();

    assertThat(readContents()).containsExactly(Path.of("dir"));
    assertThat(Files.readAllLines(root.resolve("dir"))).containsExactly("abcde");
    assertThat(update.getUpdatedPaths()).containsExactly(root.resolve("dir"));
  }

  @Test
  public void keep_disjoint_files() throws IOException {
    createFiles("dir/file1.txt", "dir/subdir/file2.txt");
    ArtifactDirectoryUpdate update =
        new ArtifactDirectoryUpdate(
            cache,
            workspaceRoot,
            root,
            ArtifactDirectoryContents.newBuilder()
                .putContents(
                    "dir/file3.txt",
                    ProjectArtifact.newBuilder()
                        .setTransform(ArtifactTransform.COPY)
                        .setBuildArtifact(BuildArtifact.newBuilder().setDigest("abcde"))
                        .build())
                .putContents(
                    "dir/subdir2/file4.txt",
                    ProjectArtifact.newBuilder()
                        .setTransform(ArtifactTransform.COPY)
                        .setBuildArtifact(BuildArtifact.newBuilder().setDigest("abcdf"))
                        .build())
                .build(),
            FileTransform.COPY,
            false);
    update.update();

    assertThat(readContents())
        .containsExactly(Path.of("dir/file3.txt"), Path.of("dir/subdir2/file4.txt"));
    assertThat(update.getUpdatedPaths())
        .containsExactly(root.resolve("dir/file3.txt"), root.resolve("dir/subdir2/file4.txt"));
  }

  @Test
  public void unchanged_files_not_requested_from_cache() throws IOException {
    ArtifactDirectoryUpdate populate =
        new ArtifactDirectoryUpdate(
            cache,
            workspaceRoot,
            root,
            ArtifactDirectoryContents.newBuilder()
                .putContents(
                    "file1.txt",
                    ProjectArtifact.newBuilder()
                        .setTransform(ArtifactTransform.COPY)
                        .setBuildArtifact(BuildArtifact.newBuilder().setDigest("file1digest"))
                        .build())
                .putContents(
                    "file2.txt",
                    ProjectArtifact.newBuilder()
                        .setTransform(ArtifactTransform.COPY)
                        .setBuildArtifact(BuildArtifact.newBuilder().setDigest("file2digest"))
                        .build())
                .build(),
            FileTransform.COPY,
            false);
    populate.update();
    cache.takeRequestedDigests();

    // re-run an equivalent update
    ArtifactDirectoryUpdate update =
        new ArtifactDirectoryUpdate(
            cache,
            workspaceRoot,
            root,
            ArtifactDirectoryContents.newBuilder()
                .putContents(
                    "file1.txt",
                    ProjectArtifact.newBuilder()
                        .setTransform(ArtifactTransform.COPY)
                        .setBuildArtifact(BuildArtifact.newBuilder().setDigest("file1digest"))
                        .build())
                .putContents(
                    "file2.txt",
                    ProjectArtifact.newBuilder()
                        .setTransform(ArtifactTransform.COPY)
                        .setBuildArtifact(BuildArtifact.newBuilder().setDigest("file2digest"))
                        .build())
                .build(),
            FileTransform.COPY,
            false);
    update.update();
    assertThat(update.getUpdatedPaths()).isEmpty();
    assertThat(cache.takeRequestedDigests()).isEmpty();
  }

  @Test
  public void partial_update() throws IOException {
    ArtifactDirectoryUpdate populate =
        new ArtifactDirectoryUpdate(
            cache,
            workspaceRoot,
            root,
            ArtifactDirectoryContents.newBuilder()
                .putContents(
                    "file1.txt",
                    ProjectArtifact.newBuilder()
                        .setTransform(ArtifactTransform.COPY)
                        .setBuildArtifact(BuildArtifact.newBuilder().setDigest("abcd"))
                        .build())
                .putContents(
                    "file2.txt",
                    ProjectArtifact.newBuilder()
                        .setTransform(ArtifactTransform.COPY)
                        .setBuildArtifact(BuildArtifact.newBuilder().setDigest("defg"))
                        .build())
                .build(),
            FileTransform.COPY,
            false);
    populate.update();
    cache.takeRequestedDigests();

    // 1 file is changed, another is identical:
    ArtifactDirectoryUpdate update =
        new ArtifactDirectoryUpdate(
            cache,
            workspaceRoot,
            root,
            ArtifactDirectoryContents.newBuilder()
                .putContents(
                    "file1.txt",
                    ProjectArtifact.newBuilder()
                        .setTransform(ArtifactTransform.COPY)
                        .setBuildArtifact(BuildArtifact.newBuilder().setDigest("abcd"))
                        .build())
                .putContents(
                    "file2.txt",
                    ProjectArtifact.newBuilder()
                        .setTransform(ArtifactTransform.COPY)
                        .setBuildArtifact(BuildArtifact.newBuilder().setDigest("efgh"))
                        .build())
                .build(),
            FileTransform.COPY,
            false);
    update.update();
    assertThat(update.getUpdatedPaths()).containsExactly(root.resolve("file2.txt"));
    assertThat(cache.takeRequestedDigests()).containsExactly("efgh");
    assertThat(Files.readAllLines(root.resolve("file2.txt"))).containsExactly("efgh");
  }

  @Test
  public void update_after_workspace_file_change() throws IOException {
    Files.writeString(
        workspaceRoot.resolve("workspacefile"),
        "workspacefile original",
        StandardOpenOption.CREATE_NEW);
    ArtifactDirectoryUpdate populate =
        new ArtifactDirectoryUpdate(
            cache,
            workspaceRoot,
            root,
            ArtifactDirectoryContents.newBuilder()
                .putContents(
                    "file1.txt",
                    ProjectArtifact.newBuilder()
                        .setTransform(ArtifactTransform.COPY)
                        .setWorkspaceRelativePath("workspacefile")
                        .build())
                .build(),
            FileTransform.COPY,
            false);
    populate.update();

    Files.writeString(
        workspaceRoot.resolve("workspacefile"),
        "workspacefile updated",
        StandardOpenOption.TRUNCATE_EXISTING);
    // The same proto spec, but the workspace file contents have changed:
    ArtifactDirectoryUpdate update =
        new ArtifactDirectoryUpdate(
            cache,
            workspaceRoot,
            root,
            ArtifactDirectoryContents.newBuilder()
                .putContents(
                    "file1.txt",
                    ProjectArtifact.newBuilder()
                        .setTransform(ArtifactTransform.COPY)
                        .setWorkspaceRelativePath("workspacefile")
                        .build())
                .build(),
            FileTransform.COPY,
            false);
    update.update();
    assertThat(update.getUpdatedPaths()).containsExactly(root.resolve("file1.txt"));
    assertThat(Files.readAllLines(root.resolve("file1.txt")))
        .containsExactly("workspacefile updated");
  }

  private ImmutableList<Path> readContents() throws IOException {
    ImmutableList.Builder<Path> contents = ImmutableList.builder();
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            contents.add(root.relativize(file));
            return FileVisitResult.CONTINUE;
          }
          ;
        });
    return contents.build();
  }

  private void createFiles(String... paths) throws IOException {
    for (String path : paths) {
      Path dest = root.resolve(path);
      Files.createDirectories(dest.getParent());
      Files.write(dest, ImmutableList.of(path), UTF_8);
    }
  }

  private void writeZipFile(Path dest, Map<String, String> contents) throws IOException {
    try (Closer c = Closer.create()) {
      FileOutputStream fos = c.register(new FileOutputStream(dest.toFile()));
      ZipOutputStream zipOut = c.register(new ZipOutputStream(fos));

      for (Map.Entry<String, String> entry : contents.entrySet()) {
        ZipEntry zipEntry = new ZipEntry(entry.getKey());
        zipOut.putNextEntry(zipEntry);
        zipOut.write(entry.getValue().getBytes(UTF_8));
      }
    }
  }

}
