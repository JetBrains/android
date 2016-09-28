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

import com.android.tools.idea.npw.AsyncValidator;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ConcurrencyUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Test asynchronous validator class.
 */
public final class AsyncValidatorTest extends TestCase {
  private static final int TIMEOUT = 1000; // ms

  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  private static void assertResult(Integer[] val, Integer expected) throws InterruptedException {
    synchronized (val) {
      final long start = System.currentTimeMillis();
      while (val[0] == null) {
        val.wait(TIMEOUT);
        if ((System.currentTimeMillis() - start) > TIMEOUT) {
          fail("Validator hang");
        }
      }
      assertEquals(expected, val[0]);
    }
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // This should happen on some other thread - it will become the AWT event queue thread.
    ThreadPoolExecutor executor = ConcurrencyUtil.newSingleThreadExecutor("async validator test");
    Future<IdeaTestApplication> application = executor.
      submit(new Callable<IdeaTestApplication>() {
        @Override
        public IdeaTestApplication call() throws Exception {
          return IdeaTestApplication.getInstance();
        }
      });
    try {
      application.get(100, TimeUnit.SECONDS); // Wait for the application instantiation
    }
    finally {
      executor.shutdownNow();
    }
  }

  public void testBasicValidation() throws InterruptedException {
    final Integer[] val = { null };
    AsyncValidator<Integer> validator = new AsyncValidator<Integer>(ApplicationManager.getApplication()) {
      @Override
      protected void showValidationResult(Integer result) {
        synchronized (val) {
          val[0] = result;
          val.notifyAll();
        }
      }

      @NotNull
      @Override
      protected Integer validate() {
        return 3;
      }
    };
    validator.invalidate();
    assertResult(val, 3);
  }

  @SuppressWarnings("BusyWait")
  public void testNoSpuriousResults() throws InterruptedException {
    final int EXPECTED_RESULT = 1000;

    final Integer[] output = { null };
    final int[] counter = {0};

    AsyncValidator<Integer> validator = new AsyncValidator<Integer>(ApplicationManager.getApplication()) {
      @Override
      protected void showValidationResult(Integer result) {
        synchronized (output) {
          if (output[0] == null) {
            output[0] = result;
          }
          output.notifyAll();
        }
      }

      @NotNull
      @Override
      protected Integer validate() {
        try {
          Thread.sleep(100);
          return counter[0];
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    };
    for (counter[0] = 0; counter[0] < EXPECTED_RESULT; counter[0]++) {
      validator.invalidate();
      Thread.sleep(2);
    }
    assertResult(output, EXPECTED_RESULT); // Validation happens after loop exit - hence, 100 and not 99 which is in-loop max
  }
}
