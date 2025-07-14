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
package com.android.tools.idea.observable;

import com.google.common.collect.Queues;
import java.util.Queue;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * A utility class which invokes some logic and, if multiple invocation requests come in at the
 * same time, they are batched.
 *
 * To use, simply call {@link #enqueue(Runnable)} any time, with the expectation that it may not
 * run immediately.
 *
 * Doing this will allow us to avoid doing expensive updates on redundant, intermediate changes,
 * e.g. if you have five values in a complex mathematical calculation, and all values change in
 * the same frame, you only want to run the calculation once.
 */
public final class BatchInvoker {
  /**
   * Useful invoke strategy when working on a Swing application.
   */
  public static final Strategy SWING_INVOKE_LATER_STRATEGY = SwingUtilities::invokeLater;

  /**
   * Useful invoke strategy for testing, as logic is fired immediately and you can immediately make
   * assertions on the results.
   */
  public static final Strategy INVOKE_IMMEDIATELY_STRATEGY = Runnable::run;

  /**
   * Ensure we don't end up in a non-stop invocation loop, where one cycle triggers another cycle
   * that triggers the first, etc. Valid update loops usually settle within 2 or 3 steps.
   */
  private static final int MAX_CYCLE_COUNT = 10;


  private static Strategy ourOverrideStrategy = null;


  private final Strategy myStrategy;

  /**
   * A current batch of runnables that are either running or will run soon.
   */
  private final Queue<Runnable> myRunnables = Queues.newArrayDeque();

  /**
   * A deferred batch of runnables, for those that are added by {@link #enqueue(Runnable)} while a
   * batch is already running. These will run immediately after the current batch finishes.
   */
  private final Queue<Runnable> myDeferredRunnables = Queues.newArrayDeque();

  private boolean myUpdateInProgress;

  public BatchInvoker() {
    this(ourOverrideStrategy != null ? ourOverrideStrategy : SWING_INVOKE_LATER_STRATEGY);
  }

  public BatchInvoker(@NotNull Strategy strategy) {
    myStrategy = strategy;
  }

  /**
   * Add a {@link Runnable} to get invoked as soon as possible. It may be a good idea to create
   * your own runnable subclass and override {@link Runnable#equals(Object)}, as that will allow
   * this system to collapse redundant runnables.
   */
  public void enqueue(@NotNull Runnable runnable) {
    if (myUpdateInProgress) {
      if (!myDeferredRunnables.contains(runnable)) {
        myDeferredRunnables.add(runnable);
      }
      return;
    }

    // Prepare to run an update if we're the first update request. Any other requests that are made
    // before the update runs will get lumped in with it.
    boolean shouldInvoke = myRunnables.isEmpty();
    if (!myRunnables.contains(runnable)) {
      myRunnables.add(runnable);
    }

    if (shouldInvoke) {
      enqueueInvoke();
    }
  }

  private void enqueueInvoke() {
    myStrategy.invoke(() -> {
      int cycleCount = 0;
      RuntimeException runnableExceptionChain = null;
      while (true) {
        myUpdateInProgress = true;
        for (Runnable runnable : myRunnables) {
          try {
            runnable.run();
          }
          catch (RuntimeException ex) {
            runnableExceptionChain = addExceptionCause(ex, runnableExceptionChain);
          }
        }
        myRunnables.clear();

        myUpdateInProgress = false;

        if (!myDeferredRunnables.isEmpty()) {
          cycleCount++;
          if (cycleCount > MAX_CYCLE_COUNT) {
            myDeferredRunnables.clear();
            throw new InfiniteCycleException(runnableExceptionChain);
          }

          myRunnables.addAll(myDeferredRunnables);
          myDeferredRunnables.clear();
        }
        else {
          break;
        }
      }

      if (runnableExceptionChain != null) {
        throw runnableExceptionChain;
      }
    });
  }

  /**
   * A strategy on how to handle invoking a batch of runnables.
   *
   * Instead of invoking immediately on {@link #enqueue(Runnable)}, we often want to postpone and
   * batch those invocations. Implementors of this interface provide such deferral logic.
   */
  public interface Strategy {
    void invoke(@NotNull Runnable runnableBatch);
  }

  /**
   * Override the default invocation strategy used if one is not provided in the constructor.
   */
  @TestOnly
  public static void setOverrideStrategy(Strategy overrideStrategy) {
    ourOverrideStrategy = overrideStrategy;
  }

  /**
   * Resets the default invocation strategy.
   */
  @TestOnly
  public static void clearOverrideStrategy() {
    ourOverrideStrategy = null;
  }

  /**
   * Exception thrown by {@link BatchInvoker} if it detects that it's not stopping after a reasonable
   * amount of time.
   */
  public static final class InfiniteCycleException extends RuntimeException {
    public InfiniteCycleException(@Nullable Throwable cause) {
      super("Endless invocation cycle detected.", cause);
    }
  }

  private static RuntimeException addExceptionCause(@NotNull RuntimeException ex, @Nullable RuntimeException cause) {
    if (cause != null) {
      Throwable tail = ex;
      while (tail.getCause() != null) {
        tail = ex.getCause();
      }
      tail.initCause(cause);
    }
    return ex;
  }
}
