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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.structure.model.PsPath.TexType.FOR_COMPARE_TO;

public class TestPath extends PsPath {
  @NotNull
  public static final PsPath EMPTY_PATH = new PsPath(null) {
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

  @NotNull private final String myComparisonValue;
  @NotNull private final String myOtherValue;

  public TestPath(@NotNull String path) {
    this(path, path);
  }

  public TestPath(@NotNull String path, @NotNull PsPath parentPath) {
    this(path, path, parentPath);
  }

  public TestPath(@NotNull String comparisonValue, @NotNull String otherValue) {
    this(comparisonValue, otherValue, null);
  }

  private TestPath(@NotNull String comparisonValue, @NotNull String otherValue, @Nullable PsPath parentPath) {
    super(parentPath);
    myComparisonValue = comparisonValue;
    myOtherValue = otherValue;
  }

  @NotNull
  @Override
  public String toText(@NotNull TexType type) {
      return type == FOR_COMPARE_TO ? myComparisonValue : myOtherValue;
    }

  @Nullable
  @Override
  public String getHyperlinkDestination(@NotNull PsContext context) {
    return null;
  }

  @NotNull
  @Override
  public String getHtml(@NotNull PsContext context) {
    return myOtherValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    TestPath path = (TestPath)o;
    return Objects.equal(myComparisonValue, path.myComparisonValue) &&
           Objects.equal(myOtherValue, path.myOtherValue);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), myComparisonValue, myOtherValue);
  }
}
