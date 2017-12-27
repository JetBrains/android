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
package com.android.tools.idea.sdk.progress;

import com.android.repository.api.ProgressRunner;
import com.android.tools.idea.util.FutureUtils;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.android.AndroidTestCase;

import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for {@link StudioProgressRunner}
 */
public class StudioProgressRunnerTest extends AndroidTestCase {
  public void testSyncWithProgressNonUi() throws Exception {
    StudioProgressRunner runner = new StudioProgressRunner(true, false, "test", null);
    AtomicBoolean invoked = new AtomicBoolean(false);
    ProgressRunner.ProgressRunnable runnable = (indicator, runner1) -> {
      assertFalse(ApplicationManager.getApplication().isDispatchThread());
      try {
        Thread.sleep(100);
      }
      catch (InterruptedException e) {
        fail();
      }
      invoked.set(true);
    };

    runner.runSyncWithProgress(runnable);
    assertTrue(invoked.get());
  }

  public void testAsyncWithProgressNonUi() throws Exception {
    StudioProgressRunner runner = new StudioProgressRunner(false, false, "test", null);
    Semaphore lock = new Semaphore(1);
    lock.acquire();
    ProgressRunner.ProgressRunnable runnable = (indicator, runner1) -> {
      assertFalse(ApplicationManager.getApplication().isDispatchThread());
      try {
        lock.acquire();
      }
      catch (InterruptedException e) {
        fail();
      }
    };

    runner.runAsyncWithProgress(runnable, true);
    lock.release();
  }

  public void testSyncFromNonUi() throws Exception {
    Future f = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      StudioProgressRunner runner = new StudioProgressRunner(true, false, "test", null);
      AtomicBoolean invoked = new AtomicBoolean(false);
      ProgressRunner.ProgressRunnable runnable = (indicator, runner1) -> {
        assertFalse(ApplicationManager.getApplication().isDispatchThread());
        try {
          Thread.sleep(100);
        }
        catch (InterruptedException e) {
          fail();
        }
        invoked.set(true);
      };

      runner.runSyncWithProgress(runnable);
      assertTrue(invoked.get());
    });
    FutureUtils.pumpEventsAndWaitForFuture(f, 10, TimeUnit.SECONDS);
  }
}
