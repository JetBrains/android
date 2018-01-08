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
 * An entry in a {@code HTreeChart}. A node has associated data and a range as well, which will
 * be visualized by the parent tree as rectangular bars.
 *
 * @param <N> Type of the node subclass, provided to allow using get methods without casting
 */
public abstract class HNode<N extends HNode<N>> {

  public abstract int getChildCount();

  @NotNull
  public abstract N getChildAt(int index);

  @Nullable
  public abstract N getParent();

  public abstract long getStart();

  public abstract long getEnd();

  public abstract int getDepth();

  public final long getDuration() {
    return getEnd() - getStart();
  }

  @Nullable
  public final N getFirstChild() {
    return getChildCount() == 0 ? null : getChildAt(0);
  }

  @Nullable
  public final N getLastChild() {
    return getChildCount() == 0 ? null : getChildAt(getChildCount() - 1);
  }
}
