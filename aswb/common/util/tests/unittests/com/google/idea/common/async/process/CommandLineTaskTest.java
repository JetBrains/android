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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CommandLineTaskTest {
  @Test
  public void success() throws IOException, InterruptedException, TimeoutException {
    assertThat(CommandLineTask.builder().args("true").build().run()).isEqualTo(0);
  }

  @Test
  public void fail() throws IOException, InterruptedException, TimeoutException {
    assertThat(CommandLineTask.builder().args("false").build().run()).isNotEqualTo(0);
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
                  .run())
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
                .run());
  }
}
