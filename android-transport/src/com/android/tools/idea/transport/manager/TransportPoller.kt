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
package com.android.tools.idea.transport.manager

import com.android.tools.profiler.proto.TransportServiceGrpc
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * A simple periodic poller that loops through and executes all added [PollingTask] instances.
 */
class TransportPoller private constructor(private val client: TransportServiceGrpc.TransportServiceBlockingStub) {
  private val tasks = CopyOnWriteArrayList<PollingTask>()

  fun registerPollingTask(task: PollingTask): Boolean {
    return tasks.add(task)
  }

  fun unregisterPollingTask(task: PollingTask): Boolean {
    return tasks.remove(task)
  }

  private fun poll() {
    tasks.forEach {
      if (it.poll(client)) {
        tasks.remove(it)
      }
    }
  }

  companion object {
    private val executorService: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    private val pollers = ConcurrentHashMap<TransportPoller, ScheduledFuture<*>>()

    fun createPoller(client: TransportServiceGrpc.TransportServiceBlockingStub, pollPeriodNs: Long): TransportPoller {
      val poller = TransportPoller(client)
      val pollerFuture = executorService
        .scheduleWithFixedDelay({ poller.poll() }, 0, pollPeriodNs, TimeUnit.NANOSECONDS)
      pollers[poller] = pollerFuture
      return poller
    }

    fun removePoller(poller: TransportPoller) {
      pollers.remove(poller)?.cancel(false)
    }
  }
}