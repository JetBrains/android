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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

@RunWith(JUnit4.class)
public class TimelineDataTest {

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
    assertEquals(2, mData.getStreamCount());
  }

  @Test
  public void testGetStartTime() throws Exception {
    assertTrue(mCreationTime <= mData.getStartTime());
    assertTrue(mData.getStartTime() <= System.currentTimeMillis());
    Thread.sleep(10);
    long now = System.currentTimeMillis();
    mData.clear();
    assertTrue(now <= mData.getStartTime());
  }

  @Test
  public void testAddStreams() {
    assertEquals(2, mData.getStreamCount());
    mData.addStream("dynamicStream0");
    assertEquals(3, mData.getStreamCount());
    mData.addStreams(new ArrayList<>(Arrays.asList("duplicateStream", "dynamicStream2")));
    assertEquals(5, mData.getStreamCount());
    try {
      mData.addStreams(new ArrayList<>(Arrays.asList("duplicateStream", "dynamicStream3")));
      fail();
    }
    catch (AssertionError expected) {
    }
  }

  @Test
  public void testRemoveStreams() {
    assertEquals(2, mData.getStreamCount());
    mData.removeStream("Stream 1");
    assertEquals(1, mData.getStreamCount());
    mData.addStreams(new ArrayList<>(Arrays.asList("dynamicStream1", "dynamicStream2")));
    assertEquals(3, mData.getStreamCount());
    mData.removeStreams(new ArrayList<>(Arrays.asList("dynamicStream1", "dynamicStream2")));
    assertEquals(1, mData.getStreamCount());
  }

  @Test
  public void testAdd() throws Exception {
    assertEquals(0, mData.size());
    long start = mData.getStartTime();

    mData.add(start, 0, 1.0f, 2.0f);
    assertEquals(1, mData.size());
    assertEquals(0.0f, mData.get(0).time, 0.0001f);
    assertEquals(1.0f, mData.get(0).values[0], DELTA);
    assertEquals(2.0f, mData.get(0).values[1], DELTA);

    mData.add(start + 1000, 0, 3.0f, 4.0f);
    assertEquals(2, mData.size());
    assertEquals(1.0f, mData.get(1).time, 0.0001f);
    assertEquals(3.0f, mData.get(1).values[0], DELTA);
    assertEquals(4.0f, mData.get(1).values[1], DELTA);

    mData.add(start + 2000, 0, 5.0f, 6.0f);
    assertEquals(2, mData.size());
    assertEquals(2.0f, mData.get(1).time, 0.0001);
    assertEquals(5.0f, mData.get(1).values[0], DELTA);
    assertEquals(6.0f, mData.get(1).values[1], DELTA);

    mData.clear();
    assertEquals(0, mData.size());
  }

  @Test
  public void testAddFromAreaWorksForSimpleCase() {
    mData = new TimelineData(1, 10, AREA_TRANSFORM);
    assertEquals(0, mData.size());
    mData.add(mData.getStartTime() + 1000, TYPE_DATA, 500.0f);
    assertEquals(1, mData.size());
    TimelineData.Sample sample = mData.get(0);
    assertEquals(1.0f, sample.time, DELTA);
    assertEquals(1000.0f, sample.values[0], DELTA);
  }

  @Test
  public void testAddFromAreaWorksWithMultipleStreams() {
    mData = new TimelineData(3, 10, AREA_TRANSFORM);
    assertEquals(0, mData.size());
    mData.add(mData.getStartTime() + 1000, TYPE_DATA, 500.0f, 300.0f, 100.0f);
    assertEquals(1, mData.size());
    TimelineData.Sample sample = mData.get(0);
    assertEquals(1f, sample.time, DELTA);
    assertEquals(1000.0f, sample.values[0], DELTA);
    assertEquals(600.0f, sample.values[1], DELTA);
    assertEquals(200.0f, sample.values[2], DELTA);
  }

  @Test
  public void testAreaIsZeroForConsistentSpeed() {
    mData = new TimelineData(1, 10, AREA_TRANSFORM);
    assertEquals(0, mData.size());
    long start = mData.getStartTime();

    mData.add(start + 1000, TYPE_DATA, 500.0f);
    assertEquals(1, mData.size());
    TimelineData.Sample sample = mData.get(0);
    assertEquals(1f, sample.time, DELTA);
    assertEquals(1000f, sample.values[0], DELTA);

    mData.add(start + 2000, TYPE_DATA, 500.0f);
    assertEquals(3, mData.size());

    sample = mData.get(1);
    assertEquals(1f, sample.time, DELTA);
    assertEquals(0.0f, sample.values[0], DELTA);
    sample = mData.get(2);
    assertEquals(2.0f, sample.time, DELTA);
    assertEquals(0.0f, sample.values[0], DELTA);
  }

  @Test
  public void testAddFromAreaWorksCanHandleDecelerations() {
    mData = new TimelineData(2, 10, AREA_TRANSFORM);
    assertEquals(0, mData.size());
    long start = mData.getStartTime();

    mData.add(start + 1000, TYPE_DATA, 100.0f, 50.0f);
    assertEquals(1, mData.size());
    TimelineData.Sample sample0 = mData.get(0);
    assertEquals(1f, sample0.time, DELTA);
    assertEquals(200f, sample0.values[0], DELTA);
    assertEquals(100f, sample0.values[1], DELTA);

    mData.add(start + 2000, TYPE_DATA, 190.0f, 100.0f);
    assertEquals(3, mData.size());
    TimelineData.Sample sample1 = mData.get(1);
    assertEquals(1.9f, sample1.time, DELTA);
    assertEquals(0.0f, sample1.values[0], DELTA);
    assertEquals(10.0f, sample1.values[1], DELTA);

    TimelineData.Sample sample2 = mData.get(2);
    assertEquals(2.0f, sample2.time, DELTA);
    assertEquals(0.0f, sample2.values[0], DELTA);
    assertEquals(0.0f, sample2.values[1], DELTA);
  }

  @Test
  public void testAddFromAreaWorksForAddingStreamsDynamically() {
    mData = new TimelineData(1, 10, AREA_TRANSFORM);
    assertEquals(0, mData.size());
    long start = mData.getStartTime();

    mData.add(start + 1000, TYPE_DATA, 100.0f);
    TimelineData.Sample sample0 = mData.get(0);
    assertEquals(1f, sample0.time, DELTA);
    assertEquals(200f, sample0.values[0], DELTA);

    mData.addStream("Stream 1");
    mData.add(start + 2000, TYPE_DATA, 200.0f, 200.0f);
    TimelineData.Sample sample1 = mData.get(1);
    assertEquals(2f, sample1.time, DELTA);
    assertEquals(0f, sample1.values[0], DELTA);
    assertEquals(400f, sample1.values[1], DELTA);
  }

  @Test
  public void testAddFromAreaWorksForRemovingStreamsDynamically() {
    mData = new TimelineData(2, 10, AREA_TRANSFORM);
    assertEquals(0, mData.size());
    long start = mData.getStartTime();

    mData.add(start + 1000, TYPE_DATA, 50.0f, 100.0f);
    TimelineData.Sample sample0 = mData.get(0);
    assertEquals(1f, sample0.time, DELTA);
    assertEquals(100f, sample0.values[0], DELTA);
    assertEquals(200f, sample0.values[1], DELTA);

    mData.removeStream("Stream 0");
    mData.add(start + 2000, TYPE_DATA, 300.0f);
    TimelineData.Sample sample1 = mData.get(1);
    assertEquals(2f, sample1.time, DELTA);
    assertEquals(200f, sample1.values[0], DELTA);
  }
}
