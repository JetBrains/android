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
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import static com.google.common.truth.Truth.assertThat;

/**
 * Test asynchronous validator class.
 */
public final class AsyncValidatorTest {
  @Before
  public void setUp() throws Exception {
    // This should happen on some other thread - it will become the AWT event queue thread.
    ThreadPoolExecutor executor = ConcurrencyUtil.newSingleThreadExecutor("async validator test");
    Future<IdeaTestApplication> application = executor.
      submit((Callable<IdeaTestApplication>)IdeaTestApplication::getInstance);
    application.get(); // Wait for the application instantiation
  }

  @Test
  public void testBasicValidation() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);

    final Integer[] val = { null };
    AsyncValidator<Integer> validator = new AsyncValidator<Integer>(ApplicationManager.getApplication()) {
      @Override
      protected void showValidationResult(@NotNull Integer result) {
        synchronized (val) {
          val[0] = result;
          latch.countDown();
        }
      }

      @NotNull
      @Override
      protected Integer validate() {
        return 3;
      }
    };
    validator.invalidate();
    latch.await();

    assertThat(val[0]).isEqualTo(3);
  }

  @Test
  public void validationCanBeInterruptedByInvalidation() throws InterruptedException {

    final CountDownLatch allowValidationLatch = new CountDownLatch(1);
    final CountDownLatch outputSetLatch = new CountDownLatch(1);

    final Integer[] output = {null};
    final int[] value = {0};

    AsyncValidator<Integer> validator = new AsyncValidator<Integer>(ApplicationManager.getApplication()) {
      @Override
      protected void showValidationResult(@NotNull Integer result) {
        synchronized (output) {
          // Although we call invalidate multiple times below, it keeps interrupting validation
          // each time, so the output is set only once.
          assertThat(output[0]).isNull();
          output[0] = result;
          outputSetLatch.countDown();
        }
      }

      @NotNull
      @Override
      protected Integer validate() {
        try {
          allowValidationLatch.await();
        }
        catch (InterruptedException ignored) {
        }
        return value[0];
      }
    };

    // Make multiple invalidation requests, but make sure (using our latch) that validation takes
    // a long time. By the time first validation will be done, it will need to be recomputed again.
    int MAX_VALUE = 10;
    for (int i = 0; i <= MAX_VALUE; i++) {
      validator.invalidate();
      value[0] = i;
    }

    assertThat(output[0]).isNull(); // Not yet set; should only happen after we release the latch
    allowValidationLatch.countDown();

    outputSetLatch.await();
    assertThat(output[0]).isEqualTo(MAX_VALUE); // Equals the last value set in the above for-loop
  }
}
