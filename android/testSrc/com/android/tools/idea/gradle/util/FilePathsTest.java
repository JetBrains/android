/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.util;

import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link FilePaths}.
 */
public class FilePathsTest {
  @Test
  public void pathToIdeaUrlWithRegularFile() {
    File path = new File("Users/foo/myFolder/file.txt");
    String url = FilePaths.pathToIdeaUrl(path);
    assertEquals("file://Users/foo/myFolder/file.txt", url);
  }

  @Test
  public void pathToIdeaUrlWithJarFile() {
    File path = new File("Users/foo/myFolder/file.jar");
    String url = FilePaths.pathToIdeaUrl(path);
    assertEquals("jar://Users/foo/myFolder/file.jar!/", url);
  }

  @Test
  public void pathToIdeaUrlWithZipFile() {
    File path = new File("Users/foo/myFolder/file.zip");
    String url = FilePaths.pathToIdeaUrl(path);
    assertEquals("jar://Users/foo/myFolder/file.zip!/", url);
  }

  @Test
  public void computeRootPathsForFilesMergesDuplicateRoots() {
    List<String> roots = FilePaths.computeRootPathsForFiles(Stream.of("/a/x.c", "/a/x.c"));
    assertThat(roots).containsExactly("/a");

    roots = FilePaths.computeRootPathsForFiles(Stream.of("/a/x.c", "/a/x.h"));
    assertThat(roots).containsExactly("/a");

    roots = FilePaths.computeRootPathsForFiles(Stream.of("/a/b/x.c", "/a/b/x.h"));
    assertThat(roots).containsExactly("/a/b");

    roots = FilePaths.computeRootPathsForFiles(Stream.of("c:\\a\\x.c", "c:\\a\\x.h"));
    assertThat(roots).containsExactly("c:\\a");
  }

  @Test
  public void computeRootPathsForFilesMergesWithParents() {
    List<String> roots = FilePaths.computeRootPathsForFiles(Stream.of("/a/b/x.c", "/a/y.c"));
    assertThat(roots).containsExactly("/a");

    roots = FilePaths.computeRootPathsForFiles(Stream.of("/a/b/c/x.c", "/a/x.h"));
    assertThat(roots).containsExactly("/a");

    roots = FilePaths.computeRootPathsForFiles(Stream.of("/a/b/c/x.c", "/a/b/y.c", "/a/z.c"));
    assertThat(roots).containsExactly("/a");

    roots = FilePaths.computeRootPathsForFiles(Stream.of("/a/b/c/x.c", "/b/c/y.c", "/a/b/z.c"));
    assertThat(roots).containsExactly("/a/b", "/b/c");

    roots = FilePaths.computeRootPathsForFiles(Stream.of("/a/b/c/x.c", "/b/c/y.c", "/a/b/z.c", "/b/u.c"));
    assertThat(roots).containsExactly("/a/b", "/b");

    roots = FilePaths.computeRootPathsForFiles(Stream.of("/a/b/c/x.c", "/a/x.h", "c:\\a\\b\\x.c", "c:\\a\\x.h"));
    assertThat(roots).containsExactly("/a", "c:\\a");
  }

  @Test
  public void computeRootPathsForFilesRelativePath() {
    List<String> roots = FilePaths.computeRootPathsForFiles(Stream.of("a/x.c", "b/y.c"));
    assertThat(roots).containsExactly("a", "b");
  }
}