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
package com.android.tools.idea.gradle.structure.model;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.structure.model.PsPath.TexType.FOR_COMPARE_TO;

public abstract class PsPath implements Comparable<PsPath> {
  @Nullable private final PsPath myParentPath;

  protected PsPath(@Nullable PsPath path) {
    myParentPath = path;
  }

  @Override
  public int compareTo(PsPath path) {
    return toText(FOR_COMPARE_TO).compareTo(path.toText(FOR_COMPARE_TO));
  }

  @NotNull
  public abstract String toText(@NotNull TexType type);

  @Nullable
  public abstract String getHyperlinkDestination(@NotNull PsContext context);

  @NotNull
  public abstract String getHtml(@NotNull PsContext context);

  /**
   * Returns a path to the parent/content entity if any.
   *
   * For example, a module would be a parent for its dependencies.
   */
  // The method is final and returns a value of a final field to prevent accidental loops in parents.
  @Nullable
  final public PsPath getParent() {
    return myParentPath;
  }

  @Override
  public String toString() {
    return toText(FOR_COMPARE_TO);
  }

  public enum TexType {
    PLAIN_TEXT, FOR_COMPARE_TO
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PsPath path = (PsPath)o;
    return Objects.equal(myParentPath, path.myParentPath);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myParentPath);
  }
}
