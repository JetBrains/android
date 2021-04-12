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
package com.android.tools.idea.logcat;


import static com.intellij.util.Alarm.ThreadToUse.SWING_THREAD;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.EdtRule;
import java.util.concurrent.CountDownLatch;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class SafeAlarmTest {

  @Rule public EdtRule myEdtRule = new EdtRule();
  @Rule public MockitoRule myMockitoRule = MockitoJUnit.rule();

  @Mock Runnable myMockRequest;

  @Test
  public void addRequestIfNotEmpty_afterDispose_doesNotCrash() {
    Disposable disposable = () -> {};
    ViewListener.SafeAlarm safeAlarm = new ViewListener.SafeAlarm(disposable, SWING_THREAD);

    Disposer.dispose(disposable);
    safeAlarm.addRequestIfNotEmpty(() -> {}, 0);
  }

  @Test
  public void addRequestIfNotEmpty() throws Exception {
    Disposable disposable = () -> {};
    ViewListener.SafeAlarm safeAlarm = new ViewListener.SafeAlarm(disposable, SWING_THREAD);

    safeAlarm.addRequestIfNotEmpty(myMockRequest, 500);
    safeAlarm.addRequestIfNotEmpty(myMockRequest, 500);

    CountDownLatch latch = new CountDownLatch(1);
    safeAlarm.addRequest(latch::countDown, 500);
    latch.await();
    verify(myMockRequest, times(1)).run();
    Disposer.dispose(disposable);
  }
}