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
import com.android.annotations.concurrency.GuardedBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * A group of streams of data sampled over time. This object is thread safe as it can be
 * read/modified from any thread. It uses itself as the mutex object so it is possible to
 * synchronize on it if modifications from other threads want to be prevented.
 */
public class TimelineData {

  public static final Logger LOG = Logger.getLogger(TimelineData.class.getName());

  @GuardedBy("this")
  private final SampleTransform mTransform;

  @GuardedBy("this")
  private long mStart;

  // Streams' id and values.
  public final List<Stream> mStreams;

  // Information related to sampling, for example sample time, sample type.
  private final List<SampleInfo> mSampleInfos;

  private final int mCapacity;

  // TODO: The streams parameter may not be needed, improve stream initial set up.
  public TimelineData(int streams, int capacity) {
    this(streams, capacity, new DirectTransform());
  }

  public TimelineData(int streams, int capacity, @NonNull SampleTransform transform) {
    mCapacity = capacity;
    mSampleInfos = new CircularArrayList<SampleInfo>(capacity);
    mTransform = transform;
    mTransform.init(streams);
    mStreams = new ArrayList<Stream>();
    addDefaultStreams(streams);
    clear();
  }

  private void addDefaultStreams(int streams) {
    for (int i = 0; i < streams; i++) {
      addStream("Stream " + i);
    }
  }

  public synchronized long getStartTime() {
    return mStart;
  }

  public int getStreamCount() {
    return mStreams.size();
  }

  public Stream getStream(int index) {
    return mStreams.get(index);
  }

  public SampleInfo getSampleInfo(int index) {
    return mSampleInfos.get(index);
  }

  /**
   * The values to be added to stream depends on the transform implementation, if one was specified when constructed.
   * By default, it changes to a single sample and returned back.
   */
  public synchronized void add(long timeMills, int type, float... values) {
    float timeFromStart = (timeMills - mStart) / 1000.f;
    for (Sample sample : mTransform.transform(this, timeFromStart, type, values)) {
      add(sample);
    }
  }

  public synchronized void addStream(@NonNull String id) {
    for (Stream stream : mStreams) {
      assert !id.equals(stream.getId()) : String.format("Attempt to add duplicate stream of id %1$s", id);
    }
    int startSize = mSampleInfos.size();
    Stream stream = new Stream(id, mCapacity, startSize);
    mStreams.add(stream);
    mTransform.add(mStreams.size() - 1);
  }

  public synchronized void addStreams(@NonNull List<String> ids) {
    for (String id : ids) {
      addStream(id);
    }
  }

  public synchronized void removeStream(@NonNull String id) {
    int removeIndex = getStreamIndex(id);
    if (removeIndex >= 0) {
      mStreams.remove(removeIndex);
      mTransform.remove(removeIndex);
    }
  }

  private int getStreamIndex(@NonNull String id) {
    int indexToReturn = -1;
    for (int i = 0; i < mStreams.size(); i++) {
      if (id.equals(mStreams.get(i).getId())) {
        indexToReturn = i;
        break;
      }
    }
    if (indexToReturn == -1) {
      LOG.warning(String.format("Attempt to remove non-existing stream with id %1$s", id));
    }
    return indexToReturn;
  }

  public synchronized void removeStreams(@NonNull List<String> ids) {
    for (String id : ids) {
      removeStream(id);
    }
  }

  private void add(Sample sample) {
    float[] values = sample.values;
    assert values.length == mStreams.size();
    for (int i = 0; i < mStreams.size(); i++) {
      mStreams.get(i).add(values[i]);
    }
    mSampleInfos.add(new SampleInfo(sample.time, sample.type));
  }

  public synchronized void clear() {
    mSampleInfos.clear();
    mTransform.reset();
    for (Stream stream : mStreams) {
      stream.reset();
    }
    mStart = System.currentTimeMillis();
  }

  public int size() {
    return mSampleInfos.size();
  }

  /**
   * @deprecated TODO: Remove all usages then remove this method.
   */
  @Deprecated
  public Sample get(int index) {
    SampleInfo info = mSampleInfos.get(index);
    float[] values = new float[mStreams.size()];
    for (int i = 0; i < mStreams.size(); i++) {
      values[i] = mStreams.get(i).get(index);
    }
    return new Sample(info.time, info.type, values);
  }

  public synchronized float getEndTime() {
    return size() > 0 ? (System.currentTimeMillis() - mStart) / 1000.f : 0.0f;
  }

  public static class Stream {

    public final String mId;

    public final float[] mCircularValues;

    private int mStartIndex;

    private int mValueSize;

    public Stream(@NonNull String id, int maxValueSize, int startSize) {
      mId = id;
      mCircularValues = new float[maxValueSize];
      mStartIndex = 0;
      mValueSize = startSize;
    }

    public int getValueSize() {
      return mValueSize;
    }

    public void add(float value) {
      if (mValueSize == mCircularValues.length) {
        mCircularValues[mStartIndex] = value;
        mStartIndex = (mStartIndex + 1) % mValueSize;
      }
      else {
        mCircularValues[mValueSize] = value;
        mValueSize++;
      }
    }

    public String getId() {
      return mId;
    }

    public float get(int index) {
      assert index >= 0 && index < mValueSize : String.format("Index %1$d out of value length bound %2$d", index, mValueSize);
      return mCircularValues[(mStartIndex + index) % mValueSize];
    }

    public void reset() {
      mStartIndex = 0;
      mValueSize = 0;
    }
  }

  public static class SampleInfo {

    public final float time;

    public final int type;

    public SampleInfo(float time, int type) {
      this.time = time;
      this.type = type;
    }
  }

  /**
   * A sample of all the streams at a given moment in time.
   *
   * @deprecated TODO: Remove all usages then remove this class.
   */
  @Deprecated
  public static class Sample {

    /**
     * The time of the sample. In seconds since the start of the sampling.
     */
    public final float time;

    public final float[] values;

    public final int type;

    public Sample(float time, int type, @NonNull float[] values) {
      this.time = time;
      this.values = values;
      this.type = type;
    }
  }

  /**
   * Class which handles converting input values into {@link Sample}s.
   */
  public abstract static class SampleTransform {


    /**
     * Handle initialization code, called once with the number of streams supported by this
     * data.
     */
    void init(int streams) {
    }

    /**
     * Reset the state of this transform, called when data is cleared.
     */
    void reset() {
    }

    abstract List<Sample> transform(TimelineData data, float time, int type, float... values);

    void add(int streamIndex) {
    }

    void remove(int streamIndex) {
    }
  }

  /**
   * Convert input values directly into samples.
   */
  public static final class DirectTransform extends SampleTransform {
    @Override
    public List<Sample> transform(TimelineData data, float time, int type, float... values) {
      Sample sample = new Sample(time, type, values);
      return Collections.singletonList(sample);
    }
  }

  /**
   * Given input values of steadily increasing value, calculate samples which represent the speed
   * at which those input values are growing. This is a good way to convert "bytes downloaded so
   * far" into "download speed", for example.
   *
   * Input:  [t=0s, bytes=0], [t=1s, bytes=500], [t=2s, bytes=500]
   * Output: [t=0s, bytesPerS=0], [t=1s, bytesPerS=1000], [t=2s, bytesPerS=0]
   *
   * (meaning the download speed ramps up and then drops to 0 after all data is pulled down)
   */
  public static final class AreaTransform extends SampleTransform {

    private float[] mAreaSums;
    private float[] mLastValues;

    @Override
    void init(int streams) {
      mAreaSums = new float[streams];
      mLastValues = new float[streams];
    }

    @Override
    public void reset() {
      for (int i = 0; i < mAreaSums.length; i++) {
        mAreaSums[i] = 0.f;
        mLastValues[i] = 0.f;
      }
    }

    @Override
    void add(int streamIndex) {
      if (streamIndex < mAreaSums.length) {
        return;
      }
      mAreaSums = Arrays.copyOf(mAreaSums, streamIndex + 1);
      mLastValues = Arrays.copyOf(mLastValues, streamIndex + 1);
    }

    @Override
    void remove(int streamIndex) {
      mAreaSums = removeAndShrinkArray(mAreaSums, streamIndex);
      mLastValues = removeAndShrinkArray(mLastValues, streamIndex);
    }

    private static float[] removeAndShrinkArray(float[] src, int removeIndex) {
      float[] array = Arrays.copyOf(src, src.length - 1);
      System.arraycopy(src, removeIndex + 1, array, removeIndex, array.length - removeIndex);
      return array;
    }

    @Override
    public List<Sample> transform(TimelineData data, float time, int type, float... areaSums) {
      assert mAreaSums.length == areaSums.length;
      float[] area = new float[mAreaSums.length];
      for (int i = 0; i < areaSums.length; i++) {
        area[i] = areaSums[i] - mAreaSums[i];
        mAreaSums[i] = areaSums[i];
      }

      float lastSampleTime = data.size() > 0 ? data.getSampleInfo(data.size() - 1).time : 0.f;
      List<Sample> samples = convertAreasToSamples(time, type, area, lastSampleTime);

      float[] lastValues = samples.get(samples.size() - 1).values;
      System.arraycopy(lastValues, 0, mLastValues, 0, lastValues.length);
      return samples;
    }

    /**
     * Converts stream area values to stream samples. All streams have different starting values from last sample, different areas,
     * and result in different sample shapes. The conversion may break into multiple samples time points to make the shapes' areas are
     * correct. For example, every stream flow is a triangle when not stacked with each other; it need four time points for all streams,
     * one triangle is split into four parts at every time point, each part's shape may be changed while the area size is the same.
     *
     * <p>Because both the sample time and stream values are needed to return, Sample class is kept to be used in the return value
     * until that class is removed. </p>
     *
     * @param time     The current time in seconds from the start timestamp.
     * @param type     The timeline data type.
     * @param area     The area array of all streams.
     * @param lastTime The time in seconds of the latest existing sample.
     */
    private List<Sample> convertAreasToSamples(float time, int type, float[] area, float lastTime) {
      float maxInterval = time - lastTime;
      int streamSize = area.length;
      // Computes how long every stream's value is non-zero and the ending value at last.
      float[] nonZeroIntervalsForStreams = new float[streamSize];
      float[] endValuesForStreams = new float[streamSize];
      for (int i = 0; i < streamSize; i++) {
        if (Math.abs(mLastValues[i]) * maxInterval / 2 < Math.abs(area[i])) {
          nonZeroIntervalsForStreams[i] = maxInterval;
          endValuesForStreams[i] = area[i] * 2 / maxInterval - mLastValues[i];
        }
        else if (area[i] == 0) {
          nonZeroIntervalsForStreams[i] = 0.f;
          endValuesForStreams[i] = 0.f;
        }
        else {
          // startValues[i] should be non-zero to be greater than areas[i].
          nonZeroIntervalsForStreams[i] = area[i] * 2 / mLastValues[i];
          endValuesForStreams[i] = 0.f;
        }
      }

      // Sorts the intervals, every different interval should be a sample.
      float[] ascendingIntervals = Arrays.copyOf(nonZeroIntervalsForStreams, streamSize);
      Arrays.sort(ascendingIntervals);
      List<Sample> sampleList = new ArrayList<Sample>();
      for (float interval : ascendingIntervals) {
        float[] sampleValues = new float[streamSize];
        for (int j = 0; j < streamSize; j++) {
          if (nonZeroIntervalsForStreams[j] < interval || nonZeroIntervalsForStreams[j] == 0.f) {
            sampleValues[j] = 0.f;
          }
          else {
            sampleValues[j] =
              mLastValues[j] - (mLastValues[j] - endValuesForStreams[j]) * interval / nonZeroIntervalsForStreams[j];
          }
        }
        sampleList.add(new Sample(interval + lastTime, type, sampleValues));
        if (interval == maxInterval) {
          break;
        }
      }
      if (ascendingIntervals[streamSize - 1] < maxInterval) {
        // Adds the ending sample that all stream values are zero.
        sampleList.add(new Sample(time, type, new float[streamSize]));
      }
      return sampleList;
    }
  }
}
