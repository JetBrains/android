/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.inspectors.common.api.stacktrace;

import static com.android.tools.inspectors.common.api.stacktrace.ThreadId.DISPLAY_FORMAT;
import static com.android.tools.inspectors.common.api.stacktrace.ThreadId.INVALID_THREAD_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ThreadIdTest {
  @SuppressWarnings("EqualsWithItself")
  @Test
  public void threadEqualityTest() {
    ThreadId threadId0 = new ThreadId(0);
    ThreadId threadId1 = new ThreadId(0);
    ThreadId threadId2 = new ThreadId(5);

    assertTrue(threadId0.equals(threadId0));
    assertTrue(threadId1.equals(threadId1));
    assertTrue(threadId2.equals(threadId2));
    assertTrue(INVALID_THREAD_ID.equals(INVALID_THREAD_ID));

    assertEquals(threadId0, threadId1);
    assertNotEquals(threadId0, threadId2);
    assertNotEquals(threadId0, INVALID_THREAD_ID);
    assertNotEquals(threadId2, INVALID_THREAD_ID);
  }

  @Test
  public void displayNameTest() {
    assertEquals(String.format(DISPLAY_FORMAT, "Unknown"), INVALID_THREAD_ID.toString());
    assertEquals(String.format(DISPLAY_FORMAT, "1"), new ThreadId(1).toString());
    assertEquals(String.format(DISPLAY_FORMAT, "TestThread"), new ThreadId("TestThread").toString());
  }
}
