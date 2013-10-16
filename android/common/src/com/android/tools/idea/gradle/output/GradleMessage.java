/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Message produced by Gradle when building an Android project.
 */
public class GradleMessage {
  @NotNull private final Kind myKind;
  @NotNull private final String myText;
  @Nullable private final String mySourcePath;
  private final long myLineNumber;
  private final long myColumn;

  public GradleMessage(@NotNull Kind kind, @NotNull String text) {
    this(kind, text, null, -1L, -1L);
  }

  public GradleMessage(@NotNull Kind kind, @NotNull String text, @Nullable String sourcePath, long lineNumber, long column) {
    myKind = kind;
    myText = text;
    mySourcePath = sourcePath;
    myLineNumber = lineNumber;
    myColumn = column;
  }

  @NotNull
  public Kind getKind() {
    return myKind;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @Nullable
  public String getSourcePath() {
    return mySourcePath;
  }

  public long getLineNumber() {
    return myLineNumber;
  }

  public long getColumn() {
    return myColumn;
  }

  public enum Kind {
    ERROR, WARNING, INFO
  }
}
