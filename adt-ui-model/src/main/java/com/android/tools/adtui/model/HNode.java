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
package com.android.tools.adtui.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An interface that represents a tree where each node has range and data.
 * HNode is used by {@code HTreeChart} to visualize the tree by rectangular bars.
 *
 * @param <T> - type of data.
 */
public interface HNode<T> {
  int getChildCount();

  @NotNull
  HNode<T> getChildAt(int index);

  @Nullable
  HNode<T> getParent();

  long getStart();

  long getEnd();

  @Nullable
  T getData();

  int getDepth();

  default long duration() {
    return getEnd() - getStart();
  }

  @Nullable
  default HNode<T> getFirstChild() {
    return getChildCount() == 0 ? null : getChildAt(0);
  }

  @Nullable
  default HNode<T> getLastChild() {
    return getChildCount() == 0 ? null : getChildAt(getChildCount() - 1);
  }
}
