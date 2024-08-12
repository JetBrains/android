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
package com.google.idea.blaze.common;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AtomicFileWriterTest {

  @Rule public TemporaryFolder tmpDir = new TemporaryFolder();

  @Test
  public void directory_created() throws IOException {
    Path dest = tmpDir.getRoot().toPath().resolve("dir/file");
    try (AtomicFileWriter unused = AtomicFileWriter.create(dest)) {}
    assertThat(Files.exists(dest.getParent())).isTrue();
    assertThat(Files.isDirectory(dest.getParent())).isTrue();
  }

  @Test
  public void no_files_remain() throws IOException {
    Path dest = tmpDir.getRoot().toPath().resolve("dir/file");
    try (AtomicFileWriter unused = AtomicFileWriter.create(dest)) {}
    assertThat(Files.list(dest.getParent())).isEmpty();
  }

  @Test
  public void new_file_created() throws IOException {
    Path dest = tmpDir.getRoot().toPath().resolve("dir/file");

    try (AtomicFileWriter writer = AtomicFileWriter.create(dest)) {
      writer.getOutputStream().write("new content".getBytes(StandardCharsets.UTF_8));
      writer.onWriteComplete();
    }
    assertThat(Files.readString(dest, StandardCharsets.UTF_8)).isEqualTo("new content");
    assertThat(Files.list(dest.getParent())).containsExactly(dest);
  }

  @Test
  public void existing_file_replaced() throws IOException {
    Path dest = tmpDir.getRoot().toPath().resolve("dir/file");
    Files.createDirectories(dest.getParent());
    Files.write(dest, "existing".getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);

    try (AtomicFileWriter writer = AtomicFileWriter.create(dest)) {
      writer.getOutputStream().write("new content".getBytes(StandardCharsets.UTF_8));
      writer.onWriteComplete();
    }
    assertThat(Files.readString(dest, StandardCharsets.UTF_8)).isEqualTo("new content");
    assertThat(Files.list(dest.getParent())).containsExactly(dest);
  }

  @Test
  public void partial_write_file_not_created() throws IOException {
    Path dest = tmpDir.getRoot().toPath().resolve("dir/file");

    try (AtomicFileWriter writer = AtomicFileWriter.create(dest)) {
      writer.getOutputStream().write("new content".getBytes(StandardCharsets.UTF_8));
      // no call to onWriteComplete to simulate incomplete write
    }
    assertThat(Files.list(dest.getParent())).isEmpty();
  }

  @Test
  public void partial_write_file_unmodified() throws IOException {
    Path dest = tmpDir.getRoot().toPath().resolve("dir/file");
    Files.createDirectories(dest.getParent());
    Files.write(dest, "existing".getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);

    try (AtomicFileWriter writer = AtomicFileWriter.create(dest)) {
      writer.getOutputStream().write("new content".getBytes(StandardCharsets.UTF_8));
      // no call to onWriteComplete to simulate incomplete write
    }
    assertThat(Files.readString(dest, StandardCharsets.UTF_8)).isEqualTo("existing");
    assertThat(Files.list(dest.getParent())).containsExactly(dest);
  }

  @Test
  public void gzip_output() throws IOException {
    Path dest = tmpDir.getRoot().toPath().resolve("dir/file.gz");
    try (AtomicFileWriter writer = AtomicFileWriter.create(dest)) {
      try (GZIPOutputStream zip = new GZIPOutputStream(writer.getOutputStream())) {
        zip.write("new content".getBytes(StandardCharsets.UTF_8));
      }
      writer.onWriteComplete();
    }
    assertThat(
            new String(
                ByteStreams.toByteArray(new GZIPInputStream(Files.newInputStream(dest))),
                StandardCharsets.UTF_8))
        .isEqualTo("new content");
  }
}
