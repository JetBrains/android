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
package com.android.tools.idea.monitor;

import org.jetbrains.annotations.NotNull;

import java.util.AbstractList;

/**
 * A circular list that is backed by an array. Once the list reaches the allocated size, it will start reusing the oldest entry.
 */
class CircularArrayList<T> extends AbstractList<T> {

  @NotNull
  private final T[] myData;

  private int mySize;
  private int myStart;

  CircularArrayList(int alloc) {
    myData = (T[])new Object[alloc];
    mySize = 0;
    myStart = 0;
  }

  @Override
  public T get(int i) {
    if (i >= mySize) {
      throw new IndexOutOfBoundsException();
    }
    return myData[(myStart + i) % mySize];
  }

  @Override
  public boolean add(T t) {
    if (mySize == myData.length) {
      myData[myStart] = t;
      myStart = (myStart + 1) % mySize;
    }
    else {
      myData[mySize] = t;
      mySize++;
    }
    return true;
  }

  @Override
  public int size() {
    return mySize;
  }

  @Override
  public void clear() {
    myStart = 0;
    mySize = 0;
  }
}
