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
package com.android.tools.idea.navigator.nodes.ndk.includes.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.intellij.util.containers.ContainerUtil;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.util.Collection;
import java.util.stream.Collectors;

import static com.android.tools.idea.navigator.nodes.ndk.includes.model.IncludeValue.SortOrderKey.SHADOWING_INCLUDE_EXPRESSION;

/**
 * This is a group of includes in order to be presented as if they are #included.
 * If multiple include paths expose the same file, for example:
 *
 * path1/stdio.h
 * path2/stdio.h
 *
 * Then the view is supposed to present this in a way that is intuitive to the user.
 * For example, show just path1/stdio.h and maybe place path2/stdio.h into an include_next subfolder.
 */
final public class ShadowingIncludeValue extends IncludeValue {

  @NotNull
  public final ImmutableList<SimpleIncludeValue> myIncludes;

  @NotNull
  public final ImmutableSet<String> myExcludes;

  public ShadowingIncludeValue(@NotNull Collection<SimpleIncludeValue> includes, @NotNull Collection<String> excludes) {
    myIncludes = ImmutableList.copyOf(includes);
    myExcludes = ImmutableSet.copyOf(excludes);
  }

  @NotNull
  @Override
  public String getSortKey() {
    return SHADOWING_INCLUDE_EXPRESSION.myKey;
  }

  @NotNull
  @Unmodifiable
  public Collection<File> getIncludePathsInOrder() {
    return ContainerUtil.map(myIncludes, value -> value.getIncludeFolder());
  }

  @Override
  final public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof ShadowingIncludeValue)) {
      return false;
    }
    ShadowingIncludeValue that = (ShadowingIncludeValue) obj;
    return Objects.equals(this.myIncludes, that.myIncludes) && Objects.equals(this.myExcludes, that.myExcludes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myIncludes, myExcludes);
  }
}
