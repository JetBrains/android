/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.explorer.adbimpl;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AdbDeviceFutureCapabilitiesTest {
  private final TestShellCommands myCommands = new TestShellCommands();

  @Test
  public void logcatSupportsEpochFormatModifier() throws Exception {
    TestDevices.addLogcatSupportsEpochFormatModifierCommands(myCommands);
    assertTrue(newAdbDeviceFutureCapabilities().hasLogcatThatSupportsEpochFormatModifier().get());
  }

  @Test
  public void logcatDoesntSupportEpochFormatModifier() throws Exception {
    TestDevices.addLogcatDoesntSupportEpochFormatModifierCommands(myCommands);
    assertFalse(newAdbDeviceFutureCapabilities().hasLogcatThatSupportsEpochFormatModifier().get());
  }

  @NotNull
  private AdbDeviceFutureCapabilities newAdbDeviceFutureCapabilities() throws Exception {
    return new AdbDeviceFutureCapabilities(myCommands.createMockDevice(), new TestExecutorService());
  }

  private static final class TestExecutorService extends AbstractExecutorService {
    @Override
    public void execute(@NotNull Runnable command) {
      command.run();
    }

    @Override
    public void shutdown() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public List<Runnable> shutdownNow() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShutdown() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isTerminated() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) {
      throw new UnsupportedOperationException();
    }
  }
}
