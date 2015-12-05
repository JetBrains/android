/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.tools.idea.run.util.LaunchStatus;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An output receiver which stores all output and matches error messages.
 */
public class ErrorMatchingReceiver extends AndroidOutputReceiver {
  public static final int NO_ERROR = -2;
  public static final int UNTYPED_ERROR = -1;

  private static final Pattern FAILURE = Pattern.compile("Failure\\s+\\[(.*)\\]");
  private static final Pattern TYPED_ERROR = Pattern.compile("Error\\s+[Tt]ype\\s+(\\d+).*");
  private static final String ERROR_PREFIX = "Error";

  @NotNull private final LaunchStatus myLaunchStatus;
  private int errorType = NO_ERROR;
  private String failureMessage = null;
  private final StringBuilder output = new StringBuilder();

  public ErrorMatchingReceiver(@NotNull LaunchStatus launchStatus) {
    myLaunchStatus = launchStatus;
  }

  @Override
  protected void processNewLine(@NotNull String line) {
    if (line.length() > 0) {
      Matcher failureMatcher = FAILURE.matcher(line);
      if (failureMatcher.matches()) {
        failureMessage = failureMatcher.group(1);
      }
      Matcher errorMatcher = TYPED_ERROR.matcher(line);
      if (errorMatcher.matches()) {
        errorType = Integer.parseInt(errorMatcher.group(1));
      }
      else if (line.startsWith(ERROR_PREFIX) && errorType == NO_ERROR) {
        errorType = UNTYPED_ERROR;
      }
    }
    output.append(line).append('\n');
  }

  public int getErrorType() {
    return errorType;
  }

  @Nullable
  public String getFailureMessage() {
    return failureMessage;
  }

  @Override
  public boolean isCancelled() {
    return myLaunchStatus.isLaunchTerminated();
  }

  public boolean hasError() {
    return errorType != NO_ERROR;
  }

  public StringBuilder getOutput() {
    return output;
  }
}
