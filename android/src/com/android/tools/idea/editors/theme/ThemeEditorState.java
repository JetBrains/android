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

import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import org.jetbrains.annotations.Nullable;

public class ThemeEditorState implements FileEditorState {
  private final @Nullable String myThemeName;
  private final @Nullable String mySubStyleName;
  private final @Nullable Float myProportion;

  public ThemeEditorState(@Nullable String themeName, @Nullable String subStyleName, @Nullable Float proportion) {
    myThemeName = themeName;
    mySubStyleName = subStyleName;
    myProportion = proportion;
  }

  @Nullable
  public String getThemeName() {
    return myThemeName;
  }

  @Nullable
  public String getSubStyleName() {
    return mySubStyleName;
  }

  @Nullable
  public Float getProportion() {
    return myProportion;
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

    if (myProportion != null ? !myProportion.equals(that.myProportion) : that.myProportion != null) return false;
    if (mySubStyleName != null ? !mySubStyleName.equals(that.mySubStyleName) : that.mySubStyleName != null) return false;
    if (myThemeName != null ? !myThemeName.equals(that.myThemeName) : that.myThemeName != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myThemeName != null ? myThemeName.hashCode() : 0;
    result = 31 * result + (mySubStyleName != null ? mySubStyleName.hashCode() : 0);
    result = 31 * result + (myProportion != null ? myProportion.hashCode() : 0);
    return result;
  }
}
