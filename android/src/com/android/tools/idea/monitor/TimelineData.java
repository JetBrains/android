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

import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.GuardedBy;

import java.util.List;

/**
 * A group of streams of data sampled over time. This object is thread safe as it can be read/modified from any thread.
 * It uses itself as the mutex object so it is possible to synchronize on it if modifications from other threads want
 * to be prevented.
 */
public class TimelineData {
  private final int myStreams;
  @GuardedBy("this") private final List<Sample> mySamples;
  @GuardedBy("this") private long myStart;
  @GuardedBy("this") private float myMaxTotal;
  @GuardedBy("this") private long myFrozen;

  public TimelineData(int streams, int capacity) {
    myStreams = streams;
    mySamples = new CircularArrayList<Sample>(capacity);
    clear();
  }

  @VisibleForTesting
  synchronized public long getStartTime() {
    return myStart;
  }

  public int getStreamCount() {
    return myStreams;
  }

  synchronized public float getMaxTotal() {
    return myMaxTotal;
  }

  synchronized public void add(long time, int type, int id, float... values) {
    assert values.length == myStreams;
    float total = 0.0f;
    for (float value : values) {
      total += value;
    }
    myMaxTotal = Math.max(myMaxTotal, total);
    mySamples.add(new Sample((time - myStart) / 1000.0f, type, id, values));
  }

  synchronized public void clear() {
    mySamples.clear();
    myMaxTotal = 0.0f;
    myFrozen = -1;
    myStart = System.currentTimeMillis();
  }

  public int size() {
    return mySamples.size();
  }

  public Sample get(int index) {
    return mySamples.get(index);
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  public float getEndTime() {
    long now = myFrozen == -1 ? System.currentTimeMillis() : myFrozen;
    return (now - myStart) / 1000.f;
  }

  synchronized public void freeze() {
    myFrozen = System.currentTimeMillis();
  }

  /**
   * A sample of all the streams at a given moment in time.
   */
  public static class Sample {

    /**
     * The time of the sample. In seconds since the start of the sampling.
     */
    public final float time;
    public final float[] values;
    public final int type;
    public final int id;

    public Sample(float time, int type, int id, float[] values) {
      this.time = time;
      this.values = values;
      this.type = type;
      this.id = id;
    }
  }
}
