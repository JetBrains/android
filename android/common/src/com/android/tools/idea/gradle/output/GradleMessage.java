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

import com.google.common.base.Objects;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Message produced by Gradle when building an Android project.
 */
public class GradleMessage {
  @NotNull private final Kind myKind;
  @NotNull private final String myText;
  @Nullable private final String mySourcePath;
  private final int myLineNumber;
  private final int myColumn;

  public GradleMessage(@NotNull Kind kind, @NotNull String text) {
    this(kind, text, null, -1, -1);
  }

  public GradleMessage(@NotNull Kind kind, @NotNull String text, @Nullable String sourcePath, int lineNumber, int column) {
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

  public int getLineNumber() {
    return myLineNumber;
  }

  public int getColumn() {
    return myColumn;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    GradleMessage that = (GradleMessage)o;

    return myColumn == that.myColumn &&
           myLineNumber == that.myLineNumber &&
           myKind == that.myKind &&
           Objects.equal(mySourcePath, that.mySourcePath) &&
           Objects.equal(myText, that.myText);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myColumn, myLineNumber, myKind, mySourcePath, myText);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" +
           "kind=" + myKind +
           ", text=" + StringUtil.wrapWithDoubleQuote(myText) +
           ", sourcePath=" + mySourcePath +
           ", lineNumber=" + myLineNumber +
           ", column=" + myColumn +
           ']';
  }

  public enum Kind {
    ERROR, WARNING, INFO, STATISTICS, SIMPLE
  }
}
