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
package com.android.tools.adtui.validation;

import com.google.common.base.Strings;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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
    OK(null),
    INFO(AllIcons.General.BalloonInformation),
    WARNING(AllIcons.General.BalloonWarning),
    ERROR(AllIcons.General.BalloonError);

    @Nullable
    private final Icon myIcon;

    Severity(@Nullable Icon icon) {
      myIcon = icon;
    }

    @Nullable
    public Icon getIcon() {
      return myIcon;
    }
  }

  /**
   * The result of a call to {@link Validator#validate(Object)}. Test against {@link Result#OK} to
   * see if the input is fine, or otherwise call {@link Result#getMessage()} to get a readable
   * error / warning string which can be displayed to the user.
   */
  final class Result {
    public static final Result OK = new Result(Severity.OK, "");

    @NotNull private final Severity mySeverity;
    @NotNull private final String myMessage;

    public Result(@NotNull Severity severity, @NotNull String message) {
      mySeverity = severity;
      myMessage = message;
    }

    /**
     * Returns an error result, if given an error message, or an OK result if given a null or an empty message.
     *
     * @param errorMessage an error message, or null or an empty string to produce an OK result
     */
    @NotNull
    public static Result fromNullableMessage(@Nullable String errorMessage) {
      return Strings.isNullOrEmpty(errorMessage) ? OK : new Result(Severity.ERROR, errorMessage);
    }

    /**
     * Returns an error result for the given throwable.
     *
     * @param throwable a throwable to produce error validation result for
     */
    @NotNull
    public static Result fromThrowable(@NotNull Throwable throwable) {
      String errorMessage = throwable.getMessage();
      if (errorMessage == null) {
        errorMessage = "Error (" + throwable.getClass().getSimpleName() + ")";
      }
      return fromNullableMessage(errorMessage);
    }

    @NotNull
    public Severity getSeverity() {
      return mySeverity;
    }

    @NotNull
    public String getMessage() {
      return myMessage;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null || getClass() != obj.getClass()) return false;
      Result other = (Result)obj;
      return mySeverity == other.mySeverity && myMessage.equals(other.myMessage);
    }

    @Override
    public int hashCode() {
      return myMessage.hashCode() * 31 + mySeverity.ordinal();
    }
  }
}
