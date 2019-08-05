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

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Snapshot implements Comparable<Snapshot> {
  static final Snapshot DEFAULT = new Snapshot("default_boot");

  @NotNull
  private final String myDisplayName;

  @NotNull
  private final String myDirectoryName;

  Snapshot(@NotNull String directoryName) {
    this(directoryName, directoryName);
  }

  Snapshot(@NotNull String displayName, @NotNull String directoryName) {
    myDisplayName = displayName;
    myDirectoryName = directoryName;
  }

  @NotNull
  String getDisplayName() {
    return myDisplayName;
  }

  @NotNull
  String getDirectoryName() {
    return myDirectoryName;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof Snapshot)) {
      return false;
    }

    Snapshot snapshot = (Snapshot)object;
    return myDisplayName.equals(snapshot.myDisplayName) && myDirectoryName.equals(snapshot.myDirectoryName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myDisplayName, myDirectoryName);
  }

  @NotNull
  @Override
  public String toString() {
    return myDisplayName;
  }

  @Override
  public int compareTo(@NotNull Snapshot snapshot) {
    return myDisplayName.compareTo(snapshot.myDisplayName);
  }
}
