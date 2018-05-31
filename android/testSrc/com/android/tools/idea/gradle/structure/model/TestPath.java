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
package com.android.tools.idea.gradle.structure.model;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.structure.model.PsPath.TexType.FOR_COMPARE_TO;

public class TestPath implements PsPath {
  @NotNull
  public static final PsPath EMPTY_PATH = new PsPath() {
    @Override
    @NotNull
    public String toText(@NotNull TexType type) {
      return "";
    }

    @Nullable
    @Override
    public String getHyperlinkDestination(@NotNull PsContext context) {
      return null;
    }

    @NotNull
    @Override
    public String getHtml(@NotNull PsContext context) {
      return null;
    }

    @NotNull
    @Override
    public List<PsPath> getParents() {
      return ImmutableList.of();
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return 1;
    }

    @Override
    public String toString() {
      return "<Empty Path>";
    }
  };

  @NotNull private final PsPath myParent;
  @NotNull private final String myComparisonValue;
  @NotNull private final String myOtherValue;
  @Nullable private final String myHyperlinkDestination;

  public TestPath(@NotNull String path) {
    this(path, path);
  }

  public TestPath(@NotNull String path, @NotNull PsPath parentPath) {
    this(path, path, parentPath, null);
  }

  public TestPath(@NotNull String comparisonValue, @NotNull String otherValue) {
    this(comparisonValue, otherValue, null, null);
  }

  public TestPath(@NotNull String comparisonValue, @NotNull String otherValue, @Nullable PsPath parentPath, @Nullable String hyperlinkDestination) {
    myParent = parentPath;
    myComparisonValue = comparisonValue;
    myOtherValue = otherValue;
    myHyperlinkDestination = hyperlinkDestination;
  }

  @NotNull
  @Override
  public String toText(@NotNull TexType type) {
      return type == FOR_COMPARE_TO ? myComparisonValue : myOtherValue;
    }

  @Nullable
  @Override
  public String getHyperlinkDestination(@NotNull PsContext context) {
    return myHyperlinkDestination;
  }

  @NotNull
  @Override
  public String getHtml(@NotNull PsContext context) {
    return myOtherValue;
  }

  @NotNull
  @Override
  public List<PsPath> getParents() {
    return myParent != null ? ImmutableList.of(myParent) : ImmutableList.of();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TestPath path = (TestPath)o;
    return Objects.equal(myParent, path.myParent) &&
           Objects.equal(myComparisonValue, path.myComparisonValue) &&
           Objects.equal(myOtherValue, path.myOtherValue);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myParent, myComparisonValue, myOtherValue);
  }
}
