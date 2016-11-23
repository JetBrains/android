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

package com.android.tools.adtui.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class HNode<T> {

  private long mStart;
  private long mEnd;
  @Nullable private T mData;
  @NotNull private List<HNode<T>> mNodes;
  private int mDepth;

  public HNode() {
    this(null, 0, 0);
  }

  public HNode(@Nullable T data, long start, long end) {
    mNodes = new ArrayList<>();
    mData = data;
    mStart = start;
    mEnd = end;
  }

  @NotNull
  public List<HNode<T>> getChildren() {
    return mNodes;
  }

  public void addHNode(HNode<T> node) {
    mNodes.add(node);
  }

  @Nullable
  public HNode<T> getLastChild() {
    if (mNodes.isEmpty()) {
      return null;
    }
    return mNodes.get(mNodes.size() - 1);
  }

  @Nullable
  public HNode<T> getFirstChild() {
    if (mNodes.isEmpty()) {
      return null;
    }
    return mNodes.get(0);
  }

  public long getEnd() {
    return mEnd;
  }

  public void setEnd(long end) {
    mEnd = end;
  }

  public long getStart() {
    return mStart;
  }

  public void setStart(long start) {
    mStart = start;
  }

  public T getData() {
    return mData;
  }

  public void setData(T data) {
    mData = data;
  }

  public int getDepth() {
    return mDepth;
  }

  public void setDepth(int depth) {
    mDepth = depth;
  }

  public long duration() {
    return mEnd - mStart;
  }
}
