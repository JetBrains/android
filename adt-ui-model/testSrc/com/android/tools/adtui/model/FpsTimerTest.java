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
package com.android.tools.adtui.model;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FpsTimerTest {

  @Test
  public void test() throws InterruptedException {
    FpsTimer timer = new FpsTimer();
    // Assert handler is invoked through adding a versatile latch to count down to zero.
    CountDownLatch latch = new CountDownLatch(1);
    timer.setHandler(elapsed -> latch.countDown());
    timer.start();
    assertTrue(timer.isRunning());
    latch.await();
    timer.stop();
    assertFalse(timer.isRunning());
  }
}
