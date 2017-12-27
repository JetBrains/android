/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.util;

import com.android.tools.idea.ddms.EdtExecutor;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.concurrent.*;

public class FutureUtils {
  private static AtomicNotNullLazyValue<Alarm> myAlarm = new AtomicNotNullLazyValue<Alarm>() {
    @NotNull
    @Override
    protected Alarm compute() {
      return new Alarm(Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());
    }
  };

  public static <V> ListenableFuture<V> delayedValue(V value, int delayMillis) {
    SettableFuture<V> result = SettableFuture.create();
    myAlarm.getValue().addRequest(() -> result.set(value), delayMillis);
    return result;
  }

  public static <V> ListenableFuture<V> delayedOperation(Callable<V> callable, int delayMillis) {
    SettableFuture<V> result = SettableFuture.create();
    myAlarm.getValue().addRequest(() -> {
      try {
        result.set(callable.call());
      }
      catch (Throwable t) {
        result.setException(t);
      }
    }, delayMillis);
    return result;
  }

  public static <V> ListenableFuture<V> delayedError(Throwable t, int delayMillis) {
    SettableFuture<V> result = SettableFuture.create();
    myAlarm.getValue().addRequest(() -> result.setException(t), delayMillis);
    return result;
  }

  /**
   * Waits on the dispatch thread for a {@link Future} to complete.
   * Calling this method instead of {@link Future#get} is required for
   * {@link Future} that have callbacks executing on the
   * {@link EdtExecutor#INSTANCE}.
   */
  public static <V> V pumpEventsAndWaitForFuture(Future<V> future, long timeout, TimeUnit unit)
    throws ExecutionException, InterruptedException, TimeoutException {

    assert Toolkit.getDefaultToolkit().getSystemEventQueue() instanceof IdeEventQueue;
    assert EventQueue.isDispatchThread();

    long nano = unit.toNanos(timeout);
    long startNano = System.nanoTime();
    long endNano = startNano + nano;

    while (System.nanoTime() <= endNano) {
      IdeEventQueue.getInstance().flushQueue();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        try {
          future.get(50, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException | ExecutionException | TimeoutException | CancellationException e) {
          // Ignore exceptions since we will retry (or rethrow) later on
        }
      }, ModalityState.any());

      if (future.isDone()) {
        return future.get();
      }
    }

    throw new TimeoutException();
  }
}
