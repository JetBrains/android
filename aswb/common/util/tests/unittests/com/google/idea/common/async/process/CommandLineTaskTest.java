/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.common.async.process;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.async.process.CommandLineTask;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Output;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CommandLineTaskTest {
  @Test
  public void success() throws IOException, InterruptedException, TimeoutException {
    assertThat(CommandLineTask.builder().args("true").build().run(null)).isEqualTo(0);
  }

  @Test
  public void fail() throws IOException, InterruptedException, TimeoutException {
    assertThat(CommandLineTask.builder().args("false").build().run(null)).isNotEqualTo(0);
  }

  @Test
  public void output() throws IOException, InterruptedException, TimeoutException {
    try (ByteArrayOutputStream stdout = new ByteArrayOutputStream()) {
      assertThat(
              CommandLineTask.builder()
                  .args("bash")
                  .arg("-c")
                  .arg("echo 123")
                  .stdout(stdout)
                  .build()
                  .run(null))
          .isEqualTo(0);
      assertThat(stdout.toString(StandardCharsets.UTF_8).trim()).isEqualTo("123");
    }
  }

  @Test
  public void timeout() throws IOException, InterruptedException {
    Optional<Integer> result;
    Assert.assertThrows(
        TimeoutException.class,
        () ->
            CommandLineTask.builder()
                .args("bash")
                .arg("-c")
                .arg("sleep 10")
                .timeout(Duration.ofMillis(100))
                .build()
                .run(null));
  }

  @Test
  public void cancellation() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Runnable> cancellationHandler = new AtomicReference<>();
    TestContext context = new TestContext(cancellationHandler, latch);

    CommandLineTask task = CommandLineTask.builder().args("bash").arg("-c").arg("sleep 10").build();

    Thread t =
        new Thread(
            () -> {
              try {
                task.run(context);
              } catch (Exception e) {
                // expected
              }
            });
    t.start();

    latch.await(); // ignore race condition and just wait for the registration.
    context.cancel(); // Trigger cancellation
    t.join(1000); // Wait for thread to finish
    assertThat(t.isAlive()).isFalse();
  }

  @Test
  public void cancellation_after_start() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Runnable> cancellationHandler = new AtomicReference<>();
    TestContext context = new TestContext(cancellationHandler, latch);
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();

    CommandLineTask task =
        CommandLineTask.builder()
            .args("bash")
            .arg("-c")
            .arg("echo started && sleep 10")
            .stdout(stdout)
            .build();

    Thread t =
        new Thread(
            () -> {
              try {
                task.run(context);
              } catch (Exception e) {
                // expected
              }
            });
    t.start();

    latch.await(); // Wait until handler is registered
    // Wait for process to start and produce output
    while (!stdout.toString(StandardCharsets.UTF_8).contains("started")) {
      Thread.sleep(10);
    }
    context.cancel(); // Trigger cancellation
    t.join(1000); // Wait for thread to finish
    assertThat(t.isAlive()).isFalse();
  }

  private static class TestContext implements Context<TestContext> {
    private final AtomicReference<Runnable> cancellationHandler;
    private final CountDownLatch latch;
    private volatile boolean cancelled = false;

    public TestContext(AtomicReference<Runnable> cancellationHandler, CountDownLatch latch) {
      this.cancellationHandler = cancellationHandler;
      this.latch = latch;
    }

    public void cancel() {
      cancelled = true;
      Runnable runnable = cancellationHandler.get();
      if (runnable != null) {
        runnable.run();
      }
    }

    @Override
    public TestContext push(Scope<? super TestContext> scope) {
      return null;
    }

    @Override
    public <T extends Scope<?>> T getScope(Class<T> scopeClass) {
      return null;
    }

    @Override
    public <T extends Output> void output(T output) {}

    @Override
    public void setHasError() {}

    @Override
    public void setHasWarnings() {}

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public void addCancellationHandler(Runnable runnable) {
      cancellationHandler.set(runnable);
      latch.countDown();
    }
  }
}
