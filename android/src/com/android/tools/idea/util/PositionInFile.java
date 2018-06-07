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
package com.android.tools.idea.util;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class PositionInFile {
  @NotNull public final VirtualFile file;

  public final int line;
  public final int column;

  public PositionInFile(@NotNull VirtualFile file) {
    this(file, -1, -1);
  }

  public PositionInFile(@NotNull VirtualFile file, int line, int column) {
    this.file = file;
    this.line = line;
    this.column = column;
  }

  @Override
  public String toString() {
    return "{file=" + file + ", line=" + line + ", column=" + column + '}';
  }
}
