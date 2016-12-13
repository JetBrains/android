/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.datastore;

import com.google.protobuf3jarjar.ByteString;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.*;

public class LegacyAllocationTrackingServiceTest {

  private static final String CLASS_NAME = LegacyAllocationTrackingServiceTest.class.getName();
  private static final String METHOD_NAME = "TestMethod";
  private static final String FILE_NAME = "LegacyAllocationTrackingServiceTest.java";
  private static final int LINE_NUMBER = 100;
  private static final int CLASS_ID = 1;
  private static final int THREAD_ID = 10;
  private static final int SIZE = 101;

  private static final int TEST_PROCESS_ID = 1234;
  private static final long TEST_TIME_NS = System.nanoTime();
  private static final byte[] TEST_DATA = ByteString.copyFrom("TestBytes", Charset.defaultCharset()).toByteArray();
  private boolean myAllocationTrackingState = false;
  private boolean myAllocationTrackingDumpHit = false;
  private boolean myOutputTrackingData = false;

  @Before
  public void setUp() {
    myAllocationTrackingState = false;
    myAllocationTrackingDumpHit = false;
    myOutputTrackingData = false;
  }

  @Test
  public void testSetAllocationTrackingEnabledFailed() {
    LegacyAllocationTrackingService trackingService = new LegacyAllocationTrackingService(this::getTracker);
    myAllocationTrackingState = false;
    boolean value = trackingService.trackAllocations(TEST_PROCESS_ID, TEST_TIME_NS, true, null);
    assertFalse(value);
  }

  @Test
  public void testSetAllocationTrackingEnabledPassed() {
    LegacyAllocationTrackingService trackingService = new LegacyAllocationTrackingService(this::getTracker);
    myAllocationTrackingState = true;
    boolean value = trackingService.trackAllocations(TEST_PROCESS_ID, TEST_TIME_NS, true, null);
    assertTrue(value);
  }

  @Test
  public void testNullTrackerResponse() {
    LegacyAllocationTrackingService trackingService = new LegacyAllocationTrackingService(this::getTrackerNull);
    boolean value = trackingService.trackAllocations(TEST_PROCESS_ID, TEST_TIME_NS, true, null);
    assertFalse(value);
  }

  @Test
  public void testEnabledTwice() {
    LegacyAllocationTrackingService trackingService = new LegacyAllocationTrackingService(this::getTracker);
    myAllocationTrackingState = true;
    boolean value = trackingService.trackAllocations(TEST_PROCESS_ID, TEST_TIME_NS, true, null);
    assertTrue(value);
    value = trackingService.trackAllocations(TEST_PROCESS_ID, TEST_TIME_NS, true, null);
    assertFalse(value);
  }

  @Test
  public void testTrackerDumpNullValues() {
    LegacyAllocationTrackingService trackingService = new LegacyAllocationTrackingService(this::getTracker);
    myAllocationTrackingState = true;
    boolean value = trackingService.trackAllocations(TEST_PROCESS_ID, TEST_TIME_NS, true, null);
    assertTrue(value);
    value = trackingService.trackAllocations(TEST_PROCESS_ID, TEST_TIME_NS, false, null);
    assertTrue(value);
    assertTrue(myAllocationTrackingDumpHit);
  }

  @Test
  public void testTrackerDumpValidValues() {
    LegacyAllocationTrackingService trackingService = new LegacyAllocationTrackingService(this::getTracker);
    myAllocationTrackingState = true;
    myOutputTrackingData = true;
    boolean value = trackingService.trackAllocations(TEST_PROCESS_ID, TEST_TIME_NS, true, null);
    assertTrue(value);
    value = trackingService.trackAllocations(TEST_PROCESS_ID, TEST_TIME_NS, false, (classes, stacks, events) -> {
      assertEquals(classes.size(), 1);
      assertEquals(classes.get(0).getClassName(), CLASS_NAME);
      assertEquals(stacks.size(), 1);
      assertEquals(stacks.get(0).getStackFramesCount(), 1);
      assertEquals(stacks.get(0).getStackFrames(0).getClassName(), CLASS_NAME);
      assertEquals(stacks.get(0).getStackFrames(0).getMethodName(), METHOD_NAME);
      assertEquals(stacks.get(0).getStackFrames(0).getFileName(), FILE_NAME);
      assertEquals(stacks.get(0).getStackFrames(0).getLineNumber(), LINE_NUMBER);
      assertEquals(events.size(), 1);
      assertEquals(events.get(0).getAllocatedClassId(), CLASS_ID);
      assertEquals(events.get(0).getSize(), SIZE);
      assertEquals(events.get(0).getThreadId(), THREAD_ID);
      String id = events.get(0).getAllocationStackId().toString(Charset.defaultCharset());
      assertEquals(id, "TestBytes");
    });
    assertTrue(value);
    assertEquals(myAllocationTrackingDumpHit, true);
  }

  private LegacyAllocationTracker getTrackerNull() {
    return null;
  }

  private LegacyAllocationTracker getTracker() {
    return new LegacyAllocationTracker() {
      @Override
      public boolean setAllocationTrackingEnabled(int processId, boolean enabled) {
        return myAllocationTrackingState;
      }

      @Override
      public void getAllocationTrackingDump(int processId, @NotNull ExecutorService executorService, @NotNull Consumer<byte[]> consumer) {
        myAllocationTrackingDumpHit = true;
        assertEquals(processId, TEST_PROCESS_ID);
        if (myOutputTrackingData) {
          consumer.consume(TEST_DATA);
        }
      }

      @NotNull
      @Override
      public LegacyAllocationConverter parseDump(@NotNull byte[] dumpData) {
        assertEquals(dumpData, TEST_DATA);
        LegacyAllocationConverter converter = new LegacyAllocationConverter();
        converter.addClassName(CLASS_NAME);
        converter.addAllocation(new LegacyAllocationConverter.Allocation(CLASS_ID, SIZE, THREAD_ID, TEST_DATA));
        List<StackTraceElement> stackTraceList = new ArrayList();
        stackTraceList.add(new StackTraceElement(CLASS_NAME, METHOD_NAME, FILE_NAME, LINE_NUMBER));
        converter.addCallStack(stackTraceList);
        return converter;
      }
    };
  }
}
