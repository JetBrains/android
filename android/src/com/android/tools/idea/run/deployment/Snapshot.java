/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import com.google.common.annotations.VisibleForTesting;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A <a href="https://developer.android.com/studio/run/emulator#snapshots">snapshot</a> is a persisted image of the entire state of a
 * virtual device. Loading a snapshot into the emulator is quicker than a cold boot.
 *
 * <p>A default Quickboot snapshot is created when a developer creates a virtual device in Studio. Quickboot snapshots are not displayed in
 * the drop down button nor list but they are displayed in sublists.
 *
 * <p>The Quickboot snapshot is still a snapshot and loading it is faster than a cold boot. If a virtual device has no snapshots it is cold
 * booted at launch every time.
 */
final class Snapshot implements Comparable<Snapshot> {
  @NotNull
  private final Path myDirectory;

  @NotNull
  private final String myName;

  Snapshot(@NotNull Path directory, @NotNull FileSystem fileSystem) {
    this(directory, directory.equals(defaultBoot(fileSystem)) ? "Quickboot" : directory.toString());
  }

  Snapshot(@NotNull Path directory, @NotNull String name) {
    myDirectory = directory;
    myName = name;
  }

  @NotNull
  static Snapshot quickboot(@NotNull FileSystem fileSystem) {
    return new Snapshot(defaultBoot(fileSystem), "Quickboot");
  }

  @NotNull
  @VisibleForTesting
  static Path defaultBoot(@NotNull FileSystem fileSystem) {
    return fileSystem.getPath("default_boot");
  }

  @NotNull
  Path getDirectory() {
    return myDirectory;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof Snapshot)) {
      return false;
    }

    Snapshot snapshot = (Snapshot)object;
    return myDirectory.equals(snapshot.myDirectory) && myName.equals(snapshot.myName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myDirectory, myName);
  }

  @NotNull
  @Override
  public String toString() {
    return myName;
  }

  @Override
  public int compareTo(@NotNull Snapshot snapshot) {
    return myName.compareTo(snapshot.myName);
  }
}
