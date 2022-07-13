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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestFileSystem {
  private final Path root;
  private final Path home;
  private final Path androidHome;

  public TestFileSystem(Path root) throws IOException {
    // We canonicalize the path at least for macOS; JUnit's TemporaryFolder gets created by default
    // in "/var", which is a symlink to "/private/var", but Gradle only ever refers to that path by
    // its canonical form when building, so we have to match it.
    this.root = root.toFile().getCanonicalFile().toPath();
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
}
