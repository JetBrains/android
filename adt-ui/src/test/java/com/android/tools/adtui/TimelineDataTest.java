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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;

import static com.google.common.truth.Truth.assertThat;

public class TimelineDataTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  private static final int TYPE_DATA = 0;

  private static final TimelineData.AreaTransform AREA_TRANSFORM = new TimelineData.AreaTransform();

  /**
   * Maximum difference between double values in order to consider them equals.
   */
  private static final float DELTA = 0.000001f;

  private TimelineData mData;

  private long mCreationTime;

  @Before
  public void setUp() throws Exception {
    mCreationTime = System.currentTimeMillis();
    mData = new TimelineData(2, 2);
  }

  @Test
  public void testStreamGetters() throws Exception {
    assertThat(mData.getStreamCount()).isEqualTo(2);
  }

  @Test
  public void testGetStartTime() throws Exception {
    assertThat(mData.getStartTime()).isAtLeast(mCreationTime);
    assertThat(mData.getStartTime()).isAtMost(System.currentTimeMillis());
    Thread.sleep(10);
    long now = System.currentTimeMillis();
    mData.clear();
    assertThat(mData.getStartTime()).isAtLeast(now);
  }

  @Test
  public void testAddStreams() {
    assertThat(mData.getStreamCount()).isEqualTo(2);
    mData.addStream("dynamicStream0");
    assertThat(mData.getStreamCount()).isEqualTo(3);
    mData.addStreams(new ArrayList<>(Arrays.asList("duplicateStream", "dynamicStream2")));
    assertThat(mData.getStreamCount()).isEqualTo(5);
    thrown.expect(AssertionError.class);
    mData.addStreams(new ArrayList<>(Arrays.asList("duplicateStream", "dynamicStream3")));
  }

  @Test
  public void testRemoveStreams() {
    assertThat(mData.getStreamCount()).isEqualTo(2);
    mData.removeStream("Stream 1");
    assertThat(mData.getStreamCount()).isEqualTo(1);
    mData.addStreams(new ArrayList<>(Arrays.asList("dynamicStream1", "dynamicStream2")));
    assertThat(mData.getStreamCount()).isEqualTo(3);
    mData.removeStreams(new ArrayList<>(Arrays.asList("dynamicStream1", "dynamicStream2")));
    assertThat(mData.getStreamCount()).isEqualTo(1);
  }

  @Test
  public void testAdd() throws Exception {
    assertThat(mData.size()).isEqualTo(0);
    long start = mData.getStartTime();

    mData.add(start, 0, 1.0f, 2.0f);
    assertThat(mData.size()).isEqualTo(1);
    assertThat(mData.get(0).time).isWithin(0.0001f).of(0.0f);
    assertThat(mData.get(0).values[0]).isWithin(DELTA).of(1.0f);
    assertThat(mData.get(0).values[1]).isWithin(DELTA).of(2.0f);

    mData.add(start + 1000, 0, 3.0f, 4.0f);
    assertThat(mData.size()).isEqualTo(2);
    assertThat(mData.get(1).time).isWithin(0.0001f).of(1.0f);
    assertThat(mData.get(1).values[0]).isWithin(DELTA).of(3.0f);
    assertThat(mData.get(1).values[1]).isWithin(DELTA).of(4.0f);

    mData.add(start + 2000, 0, 5.0f, 6.0f);
    assertThat(mData.size()).isEqualTo(2);
    assertThat(mData.get(1).time).isWithin((float)0.0001).of(2.0f);
    assertThat(mData.get(1).values[0]).isWithin(DELTA).of(5.0f);
    assertThat(mData.get(1).values[1]).isWithin(DELTA).of(6.0f);

    mData.clear();
    assertThat(mData.size()).isEqualTo(0);
  }

  @Test
  public void testAddFromAreaWorksForSimpleCase() {
    mData = new TimelineData(1, 10, AREA_TRANSFORM);
    assertThat(mData.size()).isEqualTo(0);
    mData.add(mData.getStartTime() + 1000, TYPE_DATA, 500.0f);
    assertThat(mData.size()).isEqualTo(1);
    TimelineData.Sample sample = mData.get(0);
    assertThat(sample.time).isWithin(DELTA).of(1.0f);
    assertThat(sample.values[0]).isWithin(DELTA).of(1000.0f);
  }

  @Test
  public void testAddFromAreaWorksWithMultipleStreams() {
    mData = new TimelineData(3, 10, AREA_TRANSFORM);
    assertThat(mData.size()).isEqualTo(0);
    mData.add(mData.getStartTime() + 1000, TYPE_DATA, 500.0f, 300.0f, 100.0f);
    assertThat(mData.size()).isEqualTo(1);
    TimelineData.Sample sample = mData.get(0);
    assertThat(sample.time).isWithin(DELTA).of(1f);
    assertThat(sample.values[0]).isWithin(DELTA).of(1000.0f);
    assertThat(sample.values[1]).isWithin(DELTA).of(600.0f);
    assertThat(sample.values[2]).isWithin(DELTA).of(200.0f);
  }

  @Test
  public void testAreaIsZeroForConsistentSpeed() {
    mData = new TimelineData(1, 10, AREA_TRANSFORM);
    assertThat(mData.size()).isEqualTo(0);
    long start = mData.getStartTime();

    mData.add(start + 1000, TYPE_DATA, 500.0f);
    assertThat(mData.size()).isEqualTo(1);
    TimelineData.Sample sample = mData.get(0);
    assertThat(sample.time).isWithin(DELTA).of(1f);
    assertThat(sample.values[0]).isWithin(DELTA).of(1000f);

    mData.add(start + 2000, TYPE_DATA, 500.0f);
    assertThat(mData.size()).isEqualTo(3);

    sample = mData.get(1);
    assertThat(sample.time).isWithin(DELTA).of(1f);
    assertThat(sample.values[0]).isWithin(DELTA).of(0.0f);
    sample = mData.get(2);
    assertThat(sample.time).isWithin(DELTA).of(2.0f);
    assertThat(sample.values[0]).isWithin(DELTA).of(0.0f);
  }

  @Test
  public void testAddFromAreaWorksCanHandleDecelerations() {
    mData = new TimelineData(2, 10, AREA_TRANSFORM);
    assertThat(mData.size()).isEqualTo(0);
    long start = mData.getStartTime();

    mData.add(start + 1000, TYPE_DATA, 100.0f, 50.0f);
    assertThat(mData.size()).isEqualTo(1);
    TimelineData.Sample sample0 = mData.get(0);
    assertThat(sample0.time).isWithin(DELTA).of(1f);
    assertThat(sample0.values[0]).isWithin(DELTA).of(200f);
    assertThat(sample0.values[1]).isWithin(DELTA).of(100f);

    mData.add(start + 2000, TYPE_DATA, 190.0f, 100.0f);
    assertThat(mData.size()).isEqualTo(3);
    TimelineData.Sample sample1 = mData.get(1);
    assertThat(sample1.time).isWithin(DELTA).of(1.9f);
    assertThat(sample1.values[0]).isWithin(DELTA).of(0.0f);
    assertThat(sample1.values[1]).isWithin(DELTA).of(10.0f);

    TimelineData.Sample sample2 = mData.get(2);
    assertThat(sample2.time).isWithin(DELTA).of(2.0f);
    assertThat(sample2.values[0]).isWithin(DELTA).of(0.0f);
    assertThat(sample2.values[1]).isWithin(DELTA).of(0.0f);
  }

  @Test
  public void testAddFromAreaWorksForAddingStreamsDynamically() {
    mData = new TimelineData(1, 10, AREA_TRANSFORM);
    assertThat(mData.size()).isEqualTo(0);
    long start = mData.getStartTime();

    mData.add(start + 1000, TYPE_DATA, 100.0f);
    TimelineData.Sample sample0 = mData.get(0);
    assertThat(sample0.time).isWithin(DELTA).of(1f);
    assertThat(sample0.values[0]).isWithin(DELTA).of(200f);

    mData.addStream("Stream 1");
    mData.add(start + 2000, TYPE_DATA, 200.0f, 200.0f);
    TimelineData.Sample sample1 = mData.get(1);
    assertThat(sample1.time).isWithin(DELTA).of(2f);
    assertThat(sample1.values[0]).isWithin(DELTA).of(0f);
    assertThat(sample1.values[1]).isWithin(DELTA).of(400f);
  }

  @Test
  public void testAddFromAreaWorksForRemovingStreamsDynamically() {
    mData = new TimelineData(2, 10, AREA_TRANSFORM);
    assertThat(mData.size()).isEqualTo(0);
    long start = mData.getStartTime();

    mData.add(start + 1000, TYPE_DATA, 50.0f, 100.0f);
    TimelineData.Sample sample0 = mData.get(0);
    assertThat(sample0.time).isWithin(DELTA).of(1f);
    assertThat(sample0.values[0]).isWithin(DELTA).of(100f);
    assertThat(sample0.values[1]).isWithin(DELTA).of(200f);

    mData.removeStream("Stream 0");
    mData.add(start + 2000, TYPE_DATA, 300.0f);
    TimelineData.Sample sample1 = mData.get(1);
    assertThat(sample1.time).isWithin(DELTA).of(2f);
    assertThat(sample1.values[0]).isWithin(DELTA).of(200f);
  }
}
