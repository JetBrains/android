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

import com.intellij.ui.Gray;
import junit.framework.TestCase;

public class TimelineDataTest extends TestCase {

  private TimelineData myData;
  private TimelineData.Stream myStream1;
  private TimelineData.Stream myStream2;
  private long myCreationTime;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myStream1 = new TimelineData.Stream("stream1", Gray._1);
    myStream2 = new TimelineData.Stream("stream2", Gray._2);
    myCreationTime = System.currentTimeMillis();
    myData = new TimelineData(new TimelineData.Stream[] {myStream1, myStream2}, 2, "unit");
  }

  public void testStreamGetters() throws Exception {
    assertEquals(myStream1, myData.getStream(0));
    assertEquals(myStream2, myData.getStream(1));

    assertEquals(2, myData.getStreamCount());
  }

  public void testGetStartTime() throws Exception {

    assertTrue(myCreationTime <= myData.getStartTime());
    assertTrue(myData.getStartTime() <= System.currentTimeMillis());
    Thread.sleep(10);
    long now = System.currentTimeMillis();
    myData.clear();
    assertTrue(now <= myData.getStartTime());
  }

  public void testGetMaxTotal() throws Exception {
    assertEquals(0.0f, myData.getMaxTotal());
    long now = System.currentTimeMillis();
    myData.add(now + 1, 1.0f, 2.0f);
    assertEquals(3.0f, myData.getMaxTotal());
    myData.add(now + 2, 1.0f, 1.0f);
    assertEquals(3.0f, myData.getMaxTotal());
    myData.add(now + 3, 2.0f, 2.0f);
    assertEquals(4.0f, myData.getMaxTotal());
  }

  public void testAdd() throws Exception {
    assertEquals(0, myData.size());
    long start = myData.getStartTime();

    myData.add(start, 1.0f, 2.0f);
    assertEquals(1, myData.size());
    assertEquals(0.0f, myData.get(0).time, 0.0001f);
    assertEquals(1.0f, myData.get(0).values[0]);
    assertEquals(2.0f, myData.get(0).values[1]);

    myData.add(start + 1000, 3.0f, 4.0f);
    assertEquals(2, myData.size());
    assertEquals(1.0f, myData.get(1).time, 0.0001f);
    assertEquals(3.0f, myData.get(1).values[0]);
    assertEquals(4.0f, myData.get(1).values[1]);

    myData.add(start + 2000, 5.0f, 6.0f);
    assertEquals(2, myData.size());
    assertEquals(2.0f, myData.get(1).time, 0.0001);
    assertEquals(5.0f, myData.get(1).values[0]);
    assertEquals(6.0f, myData.get(1).values[1]);

    myData.clear();
    assertEquals(0, myData.size());
  }

  public void testGetUnit() throws Exception {
    assertEquals("unit", myData.getUnit());
  }

  public void testInterpolation() {
    myData.add(myData.getStartTime() + 10000, 5.0f, 9.0f);
    myData.add(myData.getStartTime() + 12000, 7.0f, 11.0f);
    TimelineData.Sample i = myData.get(0).interpolate(myData.get(1), 11.0f);
    assertEquals(11.0f, i.time, 0.0001f);
    assertEquals(6.0f, i.values[0], 0.0001f);
    assertEquals(10.0f, i.values[1], 0.0001f);
  }
}