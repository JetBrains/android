/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.editors.strings;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Objects;

public final class StringResourceKey implements Comparable<StringResourceKey> {
  private static final Comparator<StringResourceKey> COMPARATOR = Comparator.comparing(StringResourceKey::getName)
    .thenComparing(key -> key.myDirectory == null ? "" : key.myDirectory.getPath());

  private final String myName;
  private final VirtualFile myDirectory;

  public StringResourceKey(@NotNull String name, @Nullable VirtualFile directory) {
    myName = name;
    myDirectory = directory;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public VirtualFile getDirectory() {
    return myDirectory;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof StringResourceKey)) {
      return false;
    }

    StringResourceKey key = (StringResourceKey)object;
    return myName.equals(key.myName) && Objects.equals(myDirectory, key.myDirectory);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myName, myDirectory);
  }

  @NotNull
  @Override
  public String toString() {
    return myDirectory == null ? myName : myName + " (" + myDirectory + ')';
  }

  @Override
  public int compareTo(@NotNull StringResourceKey key) {
    return COMPARATOR.compare(this, key);
  }
}
