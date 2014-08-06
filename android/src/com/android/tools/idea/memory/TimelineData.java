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
package com.android.tools.idea.memory;

import com.android.annotations.concurrency.GuardedBy;

import java.awt.*;
import java.util.List;

/**
 * A group of streams of data sampled over time. This object is thread safe as it can be read/modified from any thread.
 * It uses itself as the mutex object so it is possible to synchronize on it if modifications from other threads want
 * to be prevented.
 */
class TimelineData {
  private final Stream[] myStreams;
  private final String myUnit;
  @GuardedBy("this")
  private final List<Sample> mySamples;
  @GuardedBy("this")
  private long myStart;
  @GuardedBy("this")
  private float myMaxTotal;
  @GuardedBy("this")
  private String myTitle;

  TimelineData(Stream[] streams, int capacity, String unit) {
    myStreams = streams;
    myUnit = unit;
    mySamples = new CircularArrayList<Sample>(capacity);
    myStart = System.currentTimeMillis();
  }

  public Stream getStream(int j) {
    return myStreams[j];
  }

  synchronized public long getStartTime() {
    return myStart;
  }

  public int getStreamCount() {
    return myStreams.length;
  }

  synchronized public float getMaxTotal() {
    return myMaxTotal;
  }

  synchronized public void add(long time, float... values) {
    assert values.length == myStreams.length;
    float total = 0.0f;
    for (float value : values) {
      total += value;
    }
    myMaxTotal = Math.max(myMaxTotal, total);
    mySamples.add(new Sample((time - myStart) / 1000.0f, values));
  }

  synchronized public void clear() {
    mySamples.clear();
    myMaxTotal = 0.0f;
    myStart = System.currentTimeMillis();
  }

  public int size() {
    return mySamples.size();
  }

  public Sample get(int index) {
    return mySamples.get(index);
  }

  public String getTitle() {
    return myTitle;
  }

  public void setTitle(String title) {
    myTitle = title;
  }

  public String getUnit() {
    return myUnit;
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

    Sample(float time, float[] values) {
      this.time = time;
      this.values = values;
    }

    /**
     * Linearly interpolates the receiver with the given sample at the indicated moment in time.
     *
     * @param other the sample to interpolate with.
     * @param time  the time at which to interpolate.
     * @return the interpolated Sample.
     */
    Sample interpolate(Sample other, float time) {
      if (this == other) {
        return this;
      }
      float f = (time - this.time) / (other.time - this.time);
      float[] values = new float[this.values.length];
      for (int i = 0; i < this.values.length; i++) {
        values[i] = this.values[i] + f * (other.values[i] - this.values[i]);
      }
      return new Sample(time, values);
    }
  }

  public static class Stream {
    public final String name;
    public final Color color;

    public Stream(String name, Color color) {
      this.name = name;
      this.color = color;
    }
  }
}
