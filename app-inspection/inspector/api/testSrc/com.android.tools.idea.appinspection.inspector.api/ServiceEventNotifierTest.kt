/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspector.api

import com.google.common.util.concurrent.MoreExecutors
import org.junit.Test
import java.util.concurrent.CountDownLatch

class ServiceEventNotifierTest {
  @Test
  fun onCrashedEvent() {
    val notifier = AppInspectorClient.ServiceEventNotifier()
    val crashedLatch = CountDownLatch(1)
    notifier.addListener(object : AppInspectorClient.ServiceEventListener {
      override fun onCrashEvent(message: String) {
        crashedLatch.countDown()
      }
    }, MoreExecutors.directExecutor())

    notifier.notifyCrash("CRASH")
    crashedLatch.await()
  }

  @Test
  fun onDisposedEvent() {
    val notifier = AppInspectorClient.ServiceEventNotifier()
    val disposedLatch = CountDownLatch(1)
    notifier.addListener(object : AppInspectorClient.ServiceEventListener {
      override fun onDispose() {
        disposedLatch.countDown()
      }
    }, MoreExecutors.directExecutor())

    notifier.notifyDispose()
    disposedLatch.await()
  }

  @Test
  fun addListenerAfterCrash() {
    val notifier = AppInspectorClient.ServiceEventNotifier()
    notifier.notifyCrash("CRASH")
    val crashedLatch = CountDownLatch(1)
    notifier.addListener(object : AppInspectorClient.ServiceEventListener {
      override fun onCrashEvent(message: String) {
        crashedLatch.countDown()
      }
    }, MoreExecutors.directExecutor())
    crashedLatch.await()
  }

  @Test
  fun addListenerAfterDispose() {
    val notifier = AppInspectorClient.ServiceEventNotifier()
    notifier.notifyDispose()
    val disposedLatch = CountDownLatch(1)
    notifier.addListener(object : AppInspectorClient.ServiceEventListener {
      override fun onDispose() {
        disposedLatch.countDown()
      }
    }, MoreExecutors.directExecutor())
    disposedLatch.await()
  }
}