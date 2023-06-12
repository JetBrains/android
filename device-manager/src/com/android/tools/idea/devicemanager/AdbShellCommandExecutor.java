/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.tools.idea.adb.AdbShellCommandResult;
import com.android.tools.idea.adb.AdbShellCommandsUtil;
import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public final class AdbShellCommandExecutor {
  public @NotNull Optional<List<String>> execute(@NotNull IDevice device, @NotNull String command) {
    try {
      return getOutput(AdbShellCommandsUtil.create(device).executeCommandBlocking(command));
    }
    catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException exception) {
      Logger.getInstance(AdbShellCommandExecutor.class).warn(exception);
      return Optional.empty();
    }
  }

  private static @NotNull Optional<List<String>> getOutput(@NotNull AdbShellCommandResult result) {
    List<String> output = result.getOutput();

    if (result.isError()) {
      String separator = System.lineSeparator();

      StringBuilder builder = new StringBuilder("Command failed:")
        .append(separator);

      output.forEach(line -> builder.append(line).append(separator));

      Logger.getInstance(AdbShellCommandExecutor.class).warn(builder.toString());
      return Optional.empty();
    }

    return Optional.of(output);
  }
}
