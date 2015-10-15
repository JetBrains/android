/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import com.google.common.base.Objects;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import org.jetbrains.annotations.Nullable;

public class ThemeEditorState implements FileEditorState {
  private final @Nullable String myModuleName;
  private final @Nullable String myThemeName;
  private final @Nullable String mySubStyleName;
  private final float myProportion;

  public ThemeEditorState(@Nullable String themeName, @Nullable String subStyleName, @Nullable Float proportion, @Nullable String moduleName) {
    myThemeName = themeName;
    mySubStyleName = subStyleName;
    myModuleName = moduleName;
    myProportion = proportion == null ? 0.5f : proportion;
  }

  @Nullable
  public String getThemeName() {
    return myThemeName;
  }

  @Nullable
  public String getSubStyleName() {
    return mySubStyleName;
  }

  public float getProportion() {
    return myProportion;
  }

  @Nullable
  public String getModuleName() {
    return myModuleName;
  }

  @Override
  public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
    return otherState instanceof ThemeEditorState;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ThemeEditorState that = (ThemeEditorState)o;
    return Objects.equal(myProportion, that.myProportion) &&
           Objects.equal(myModuleName, that.myModuleName) &&
           Objects.equal(myThemeName, that.myThemeName) &&
           Objects.equal(mySubStyleName, that.mySubStyleName);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myModuleName, myThemeName, mySubStyleName, myProportion);
  }

  @Override
  public String toString() {
    return "ThemeEditorState{" +
           "myModuleName='" + myModuleName + '\'' +
           ", myThemeName='" + myThemeName + '\'' +
           ", mySubStyleName='" + mySubStyleName + '\'' +
           ", myProportion=" + myProportion +
           '}';
  }
}
