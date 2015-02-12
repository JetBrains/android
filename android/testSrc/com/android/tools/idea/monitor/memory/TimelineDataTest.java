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
package com.android.tools.idea.monitor.memory;

import junit.framework.TestCase;

public class TimelineDataTest extends TestCase {

  private TimelineData myData;
  private long myCreationTime;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myCreationTime = System.currentTimeMillis();
    myData = new TimelineData(2, 2);
  }

  public void testStreamGetters() throws Exception {
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
    myData.add(now + 1, 0, 0, 1.0f, 2.0f);
    assertEquals(3.0f, myData.getMaxTotal());
    myData.add(now + 2, 0, 0, 1.0f, 1.0f);
    assertEquals(3.0f, myData.getMaxTotal());
    myData.add(now + 3, 0, 0, 2.0f, 2.0f);
    assertEquals(4.0f, myData.getMaxTotal());
  }

  public void testAdd() throws Exception {
    assertEquals(0, myData.size());
    long start = myData.getStartTime();

    myData.add(start, 0, 0, 1.0f, 2.0f);
    assertEquals(1, myData.size());
    assertEquals(0.0f, myData.get(0).time, 0.0001f);
    assertEquals(1.0f, myData.get(0).values[0]);
    assertEquals(2.0f, myData.get(0).values[1]);

    myData.add(start + 1000, 0, 0, 3.0f, 4.0f);
    assertEquals(2, myData.size());
    assertEquals(1.0f, myData.get(1).time, 0.0001f);
    assertEquals(3.0f, myData.get(1).values[0]);
    assertEquals(4.0f, myData.get(1).values[1]);

    myData.add(start + 2000, 0, 0, 5.0f, 6.0f);
    assertEquals(2, myData.size());
    assertEquals(2.0f, myData.get(1).time, 0.0001);
    assertEquals(5.0f, myData.get(1).values[0]);
    assertEquals(6.0f, myData.get(1).values[1]);

    myData.clear();
    assertEquals(0, myData.size());
  }
}