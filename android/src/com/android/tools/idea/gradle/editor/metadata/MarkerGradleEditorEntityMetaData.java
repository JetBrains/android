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
package com.android.tools.idea.gradle.editor.metadata;

import org.jetbrains.annotations.NotNull;

/**
 * {@link GradleEditorEntityMetaData} implementation which is not supposed to have any arguments but serves just as a marker,
 * e.g. <code>'injected'</code> or <code>'outgoing'</code>.
 */
public class MarkerGradleEditorEntityMetaData implements GradleEditorEntityMetaData {

  @NotNull private final String myType;

  public MarkerGradleEditorEntityMetaData(@NotNull String type) {
    myType = type;
  }

  @NotNull
  @Override
  public String getType() {
    return myType;
  }

  @Override
  public int hashCode() {
    return myType.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MarkerGradleEditorEntityMetaData that = (MarkerGradleEditorEntityMetaData)o;

    return myType.equals(that.myType);
  }

  @Override
  public String toString() {
    return myType;
  }
}
