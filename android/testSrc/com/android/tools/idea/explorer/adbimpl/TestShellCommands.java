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
package com.android.tools.idea.explorer.adbimpl;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.util.Map;

public class TestShellCommands {
  @NotNull private static final Logger LOGGER = Logger.getInstance(TestShellCommands.class);

  @NotNull private final Map<String, TestShellCommandResult> myCommands = new HashMap<>();
  @NotNull private String myDescription = "[MockDevice]";

  public void setDescription(@NotNull String description) {
    myDescription = description;
  }

  public void add(@NotNull String command, @NotNull String lines) {
    myCommands.put(command, new TestShellCommandResult(lines));
  }

  public void addError(@NotNull String command, @NotNull Exception error) {
    myCommands.put(command, new TestShellCommandResult(error));
  }

  public TestShellCommandResult get(@NotNull String command) {
    return myCommands.get(command);
  }

  public IDevice createMockDevice() throws Exception {
    return new MockDdmlibDevice().setName(myDescription).setShellCommands(this).getIDevice();
  }

  public void executeShellCommand(String command, IShellOutputReceiver receiver) throws Exception {
    TestShellCommandResult commandResult = this.get(command);
    if (commandResult == null) {
      UnsupportedOperationException error = new UnsupportedOperationException(
        String.format("Command \"%s\" not found in mock device \"%s\". Test case is not correctly setup.", command, myDescription));
      LOGGER.error(error);
      throw error;
    }

    LOGGER.info(String.format("executeShellCommand: %s", command));
    if (commandResult.getError() != null) {
      throw commandResult.getError();
    }

    if (commandResult.getOutput() == null) {
      UnsupportedOperationException error = new UnsupportedOperationException(
        String.format("Command \"%s\" has no result in mock device \"%s\". Test case is not setup correctly", command, myDescription));
      LOGGER.error(error);
      throw error;
    }

    byte[] bytes = commandResult.getOutput().getBytes(Charset.forName("UTF-8"));
    int chunkSize = 100;
    for (int i = 0; i < bytes.length; i += chunkSize) {
      int count = Math.min(chunkSize, bytes.length - i);
      receiver.addOutput(bytes, i, count);
    }
    receiver.flush();
  }
}
