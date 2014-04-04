/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple utility that reruns task in another thread and reports the result back.
 * <p/>
 * The goal is to run validation in a non-UI thread. Some validation results may be dropped
 * as user input may change before validation completes.
 */
public abstract class AsyncValidator<V> implements Disposable {
  private final ExecutorService myExecutor = Executors.newSingleThreadExecutor();
  private final AtomicBoolean myIsScheduled = new AtomicBoolean(false);
  private final AtomicReference<V> resultToReport = new AtomicReference<V>(null);

  public AsyncValidator(@NotNull Disposable parent) {
    Disposer.register(parent, this);
  }

  /**
   * Informs the validator that data had updated and validation status needs to be recomputed.
   * <p/>
   * Can be called on any thread.
   */
  public final void invalidate() {
    if (myIsScheduled.compareAndSet(false, true)) {
      resultToReport.set(null);
      if (myExecutor.isShutdown()) {
        throw new IllegalStateException("Validator was already disposed");
      }
      myExecutor.execute(new Runnable() {
        @Override
        public void run() {
          while (myIsScheduled.get()) {
            resultToReport.set(null);
            myIsScheduled.set(false);
            resultToReport.set(validate());
            if (!myIsScheduled.get()) {
              SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                  report();
                }
              });
            }
          }
        }
      });
    }
  }

  private void report() {
    V result = resultToReport.get();
    if (result != null) {
      showValidationResult(result);
    }
  }

  /**
   * Invoked on UI thread to show "stable" validation result in the UI.
   */
  protected abstract void showValidationResult(V result);

  /**
   * Invoked on a validation thread to perform long-running operation.
   */
  @NotNull
  protected abstract V validate();

  @Override
  public final void dispose() {
    myExecutor.shutdownNow();
  }
}
