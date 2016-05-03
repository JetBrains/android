/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.adtui;

import com.android.annotations.NonNull;

import java.util.AbstractList;

/**
 * A circular list that is backed by an array. Once the list reaches the allocated size, it will
 * start reusing the oldest entry.
 */
class CircularArrayList<T> extends AbstractList<T> {

  @NonNull
  private final T[] mData;

  private int mSize;

  private int mStart;

  CircularArrayList(int alloc) {
    mData = (T[])new Object[alloc];
    mSize = 0;
    mStart = 0;
  }

  @Override
  public T get(int i) {
    if (i < 0 || i >= mSize) {
      throw new IndexOutOfBoundsException();
    }
    return mData[(mStart + i) % mSize];
  }

  @Override
  public boolean add(T t) {
    if (mSize == mData.length) {
      mData[mStart] = t;
      mStart = (mStart + 1) % mSize;
    }
    else {
      mData[mSize] = t;
      mSize++;
    }
    return true;
  }

  @Override
  public int size() {
    return mSize;
  }

  @Override
  public void clear() {
    mStart = 0;
    mSize = 0;
  }
}
