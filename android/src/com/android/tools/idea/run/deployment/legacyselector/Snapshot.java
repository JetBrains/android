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
package com.android.tools.idea.run.deployment.legacyselector;

import java.nio.file.Path;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A <a href="https://developer.android.com/studio/run/emulator#snapshots">snapshot</a> is a persisted image of the entire state of a
 * virtual device. Loading a snapshot into the emulator is quicker than a cold boot.
 */
final class Snapshot implements Comparable<Snapshot> {
  @NotNull
  private final Path myDirectory;

  @NotNull
  private final String myName;

  Snapshot(@NotNull Path directory) {
    this(directory, directory.getFileName().toString());
  }

  Snapshot(@NotNull Path directory, @NotNull String name) {
    myDirectory = directory;
    myName = name;
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
