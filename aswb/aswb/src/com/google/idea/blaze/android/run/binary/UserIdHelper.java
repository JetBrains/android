/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.binary;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.tools.idea.execution.common.RunConfigurationNotifier;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Helpers for user id */
public final class UserIdHelper {
  private UserIdHelper() {}

  private static final Pattern USER_ID_REGEX =
      Pattern.compile("UserInfo\\{([0-9]+):Work profile:[0-9]+\\}");

  @Nullable
  public static Integer getUserIdFromConfigurationState(
      Project project, IDevice device, BlazeAndroidBinaryRunConfigurationState state)
      throws ExecutionException {
    if (state.useWorkProfileIfPresent()) {
      try {
        Integer userId = getWorkProfileId(device);
        if (userId == null) {
          RunConfigurationNotifier.INSTANCE.notifyWarning(
              project,
              "",
              "Could not locate work profile on selected device. Launching default user.");
        }
        return userId;
      } catch (TimeoutException
          | AdbCommandRejectedException
          | ShellCommandUnresponsiveException
          | IOException e) {
        throw new ExecutionException(e);
      }
    }
    return state.getUserId();
  }

  @Nullable
  public static Integer getWorkProfileId(IDevice device)
      throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
          IOException {
    CollectingOutputReceiver receiver = new CollectingOutputReceiver();
    device.executeShellCommand("pm list users", receiver);
    String result = receiver.getOutput();
    Matcher matcher = USER_ID_REGEX.matcher(result);
    if (matcher.find()) {
      return Integer.parseInt(matcher.group(1));
    }
    return null;
  }

  public static String getFlagsFromUserId(@Nullable Integer userId) {
    return userId != null ? ("--user " + userId.intValue()) : "";
  }
}
