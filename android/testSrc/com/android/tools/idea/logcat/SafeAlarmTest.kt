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
package com.android.tools.idea.logcat

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.util.Alarm.ThreadToUse.SWING_THREAD
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.util.concurrent.CountDownLatch

class SafeAlarmTest {
  @get:Rule
  var edtRule = EdtRule()

  @get:Rule
  var mockitoRule: MockitoRule = MockitoJUnit.rule()

  @Mock
  private lateinit var mockRequest: Runnable

  @Test
  fun addRequestIfNotEmpty_afterDispose_doesNotCrash() {
    val disposable = Disposable {}
    val safeAlarm = SafeAlarm(SWING_THREAD, disposable)

    Disposer.dispose(disposable)
    safeAlarm.addRequestIfNotEmpty({}, 0)
  }

  @Test
  fun addRequestIfNotEmpty() {
    val disposable = Disposable {}
    val safeAlarm = SafeAlarm(SWING_THREAD, disposable)

    safeAlarm.addRequestIfNotEmpty(mockRequest, 500)
    safeAlarm.addRequestIfNotEmpty(mockRequest, 500)

    val latch = CountDownLatch(1)
    safeAlarm.addRequest({ latch.countDown() }, 500)
    latch.await()
    Mockito.verify(mockRequest, Mockito.times(1)).run()
    Disposer.dispose(disposable)
  }

  @Test
  fun addRequest_afterDispose_doesNotCrash() {
    val disposable = Disposable {}
    val safeAlarm = SafeAlarm(SWING_THREAD, disposable)

    Disposer.dispose(disposable)
    safeAlarm.addRequest({}, 0)
  }

  @Test
  fun addRequest() {
    val disposable = Disposable {}
    val safeAlarm = SafeAlarm(SWING_THREAD, disposable)

    safeAlarm.addRequest(mockRequest, 500)

    val latch = CountDownLatch(1)
    safeAlarm.addRequest({ latch.countDown() }, 500)
    latch.await()
    Mockito.verify(mockRequest, Mockito.times(1)).run()
    Disposer.dispose(disposable)
  }
}