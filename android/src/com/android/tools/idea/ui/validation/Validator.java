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
package com.android.tools.idea.ui.validation;

import org.jetbrains.annotations.NotNull;

/**
 * A class which is used to validate some input.
 */
public interface Validator<T> {

  /**
   * Returns {@link Result#OK} if the input is valid, or a result with some other
   * {@link Severity} otherwise.
   */
  @NotNull
  Result validate(@NotNull T value);

  /**
   * Indicates the severity of a validation violation. {@link Severity#OK} should be used if no
   * violation has occurred.
   */
  enum Severity {
    OK,
    INFO,
    WARNING,
    ERROR
  }

  /**
   * The result of a call to {@link Validator#validate(Object)}. Test against {@link Result#OK} to
   * see if the input is fine, or otherwise call {@link Result#getMessage()} to get a readable
   * error / warning string which can be displayed to the user.
   */
  final class Result {
    public static final Result OK = new Result(Severity.OK, "");

    private final Severity mySeverity;
    private final String myMessage;

    public Result(@NotNull Severity severity, @NotNull String message) {
      mySeverity = severity;
      myMessage = message;
    }

    @NotNull
    public Severity getSeverity() {
      return mySeverity;
    }

    @NotNull
    public String getMessage() {
      return myMessage;
    }
  }
}
