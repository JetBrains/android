/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.semantics;

import com.google.common.base.Objects;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SurfaceSyntaxDescription {
  @NotNull public final String name;
  @Nullable public final Integer arity; // null implies property

  public SurfaceSyntaxDescription(@NotNull String name, @Nullable Integer arity) {
    this.name = name;
    this.arity = arity;
  }

  @Override
  public String toString() {
    return arity == null ? name : name + "/" + arity;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(new Object[] {name, arity});
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof SurfaceSyntaxDescription) {
      SurfaceSyntaxDescription ssd = (SurfaceSyntaxDescription)obj;
      return name.equals(ssd.name) && Objects.equal(arity, ssd.arity);
    }
    else {
      return false;
    }
  }
}
