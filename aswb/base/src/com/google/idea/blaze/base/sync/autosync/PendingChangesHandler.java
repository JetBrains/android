/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.sync.autosync;

import com.google.common.collect.ImmutableSet;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks pending changes, and kicks off a task when no new changes have arrived in a given period
 * of time.
 *
 * <p>Use case: batching many changes arriving in a short period of time, then running a single task
 * over the full set of changes.
 */
abstract class PendingChangesHandler<V> {

  private static final Duration RETRY_DELAY = Duration.ofSeconds(10);

  private final Set<V> pendingItems = Collections.synchronizedSet(new HashSet<>());

  private final Timer timer = new Timer("pendingChangesTimer", /* isDaemon= */ true);
  private final Duration delayDuration;
  private final AtomicBoolean isTaskPending = new AtomicBoolean(false);

  private volatile Instant lastChangeTime;
  private volatile boolean ignoreChanges;

  /**
   * @param delayDuration when no new changes have arrived for approximately this period of time the
   *     batched task is executed
   */
  PendingChangesHandler(Duration delayDuration) {
    this.delayDuration = delayDuration;
  }

  /**
   * Called when no new changes have arrived for a given period of time. Returns false if the task
   * cannot currently be run. In this case, the handler retries later.
   */
  abstract boolean runTask(ImmutableSet<V> changes);

  void queueChange(V item) {
    if (ignoreChanges) {
      return;
    }
    pendingItems.add(item);
    lastChangeTime = Instant.now();
    // to minimize synchronization overhead, we don't explicitly cancel any existing task on each
    // change, but delay this until the pending task would otherwise run.
    if (isTaskPending.compareAndSet(false, true)) {
      queueTask(delayDuration);
    }
  }

  /** Clears the list of pending changes. Any task */
  void clearQueue() {
    pendingItems.clear();
  }

  void clearQueueAndIgnoreChangesForDuration(Duration time) {
    ignoreChanges = true;
    clearQueue();
    timer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            ignoreChanges = false;
          }
        },
        /* delay= */ time.toMillis());
  }

  private void queueTask(Duration delay) {
    timer.schedule(newTask(), delay.toMillis());
  }

  private TimerTask newTask() {
    return new TimerTask() {
      @Override
      public void run() {
        timerComplete();
      }
    };
  }

  /**
   * Run task if there have been no more changes since it was first requested, otherwise queue up
   * another task.
   */
  private void timerComplete() {
    Duration timeSinceLastEvent = Duration.between(lastChangeTime, Instant.now());
    Duration timeToWait = delayDuration.minus(timeSinceLastEvent);
    if (!timeToWait.isNegative()) {
      // kick off another task and abort this one
      queueTask(timeToWait);
      return;
    }
    ImmutableSet<V> items = retrieveAndClearPendingItems();
    if (items.isEmpty()) {
      return;
    }
    if (runTask(items)) {
      isTaskPending.set(false);
    } else {
      pendingItems.addAll(items);
      queueTask(RETRY_DELAY);
    }
  }

  private ImmutableSet<V> retrieveAndClearPendingItems() {
    synchronized (pendingItems) {
      ImmutableSet<V> copy = ImmutableSet.copyOf(pendingItems);
      pendingItems.clear();
      return copy;
    }
  }
}
