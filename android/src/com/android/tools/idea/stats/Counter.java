/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.stats;

public class Counter extends KeyString implements Comparable<Counter> {
  private final int myCount;

  Counter(String key, int count) {
    super(key, null);
    myCount = count;
  }

  @Override
  public String getValue() {
    return Integer.toString(myCount);
  }

  @Override
  public int compareTo(Counter rhs) {
    int v = getKey().compareTo(rhs.getKey());
    if (v == 0) {
      v = myCount - rhs.myCount;
    }
    return v;
  }

  @Override
  public boolean equals(Object rhs) {
    if (this == rhs) return true;
    if (rhs == null || getClass() != rhs.getClass()) return false;

    Counter counter = (Counter)rhs;

    if (myCount != counter.myCount) return false;
    if (!getKey().equals(counter.getKey())) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = getKey().hashCode();
    result = 31 * result + myCount;
    return result;
  }

  @Override
  public String toString() {
    return "Counter{" +
           "myKey='" + getKey() + '\'' +
           ", myCount=" + myCount +
           '}';
  }
}
