/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.asdriver.tests;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestFileSystem {
  private final Path root;
  private final Path home;
  private final Path androidHome;

  public TestFileSystem(Path root) throws IOException {
    this.root = root;
    home = root.resolve("home");
    androidHome = home.resolve(".android");
    Files.createDirectories(androidHome);
  }

  public Path getRoot() {
    return root;
  }

  public Path getHome() {
    return home;
  }

  public Path getAndroidHome() {
    return androidHome;
  }

  /**
   * We need to manually {@link #recursiveDelete(File)} the root because {@link com.android.utils.PathUtils#deleteRecursivelyIfExists(Path)}
   * throws a {@link java.nio.file.DirectoryNotEmptyException} when attempting to delete directories that are (ostensibly?) in use by
   * Gradle. This exception causes tests to fail during the cleanup stage.
   *
   * Instead, we manually crawl from the root and delete files without asserting success - on a best effort basis.
   *
   * The following code was referenced from {@link org.junit.rules.TemporaryFolder}.
   *
   * TODO(b/239343337): Figure out the cause of the error, and whether or not this is related to Gradle. One possible insight might be the
   * note within {@link com.android.utils.PathUtils#deleteRecursivelyIfExists(Path)}, where it states that a similar issue is present on
   * Windows.
   */
  public void delete() {
    if (root != null) {
      recursiveDelete(root.toFile());
    }
  }

  private void recursiveDelete(File file) {
    File[] files = file.listFiles();
    if (files != null) {
      for (File each : files) {
        recursiveDelete(each);
      }
    }
    file.delete();
  }
}
