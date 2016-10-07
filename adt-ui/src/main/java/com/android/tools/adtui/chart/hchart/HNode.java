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

package com.android.tools.adtui.chart.hchart;

import java.util.ArrayList;
import java.util.List;

public class HNode<T> {

  private long mStart;
  private long mEnd;
  private T mData;
  private List<HNode<T>> mNodes;
  private int mDepth;

  public HNode() {
    mNodes = new ArrayList<HNode<T>>();
  }

  public List<HNode<T>> getChildren() {
    return this.mNodes;
  }

  public void addHNode(HNode node) {
    this.mNodes.add(node);
  }

  public HNode<T> getLastChild() {
    if (mNodes.isEmpty()) {
      return null;
    }
    return mNodes.get(mNodes.size() - 1);
  }

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
    this.mEnd = end;
  }

  public long getStart() {
    return mStart;
  }

  public void setStart(long start) {
    this.mStart = start;
  }

  public T getData() {
    return mData;
  }

  public void setData(T data) {
    this.mData = data;
  }

  public void setDepth(int depth) {
    mDepth = depth;
  }

  public int getDepth() {
    return this.mDepth;
  }
}
