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

import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.openapi.options.ConfigurationQuickFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A validation error encountered when checking a configuration.
 * Within the Android run configurations, we collect validation errors rather than using
 * {@link RuntimeConfigurationException} and subclasses, to
 * avoid the common mistake of throwing an exception with a warning before getting the chance to
 * check for some fatal error.
 * See {@link AndroidRunConfigurationBase#checkConfiguration}.
 */
public final class ValidationError implements Comparable<ValidationError> {
  /**
   * Severity levels in ascending order.
   */
  public enum Severity {
    WARNING,
    FATAL,
    INFO
  }

  /**
   * Category describing the area of validation.
   */
  public enum Category {
    PROFILER,
  }

  @NotNull private final Severity mySeverity;
  @NotNull private final String myMessage;
  @Nullable private final Category myCategory;
  @Nullable private final ConfigurationQuickFix myQuickfix;

  private ValidationError(@NotNull Severity severity, @NotNull String message, @Nullable Category category, @Nullable ConfigurationQuickFix quickfix) {
    mySeverity = severity;
    myMessage = message;
    myCategory = category;
    myQuickfix = quickfix;
  }

  @NotNull
  public static ValidationError fatal(@NotNull String message) {
    return fatal(message, null);
  }

  @NotNull
  public static ValidationError fatal(@NotNull String message, @Nullable ConfigurationQuickFix quickfix) {
    return new ValidationError(Severity.FATAL, message, null, quickfix);
  }

  @NotNull
  public static ValidationError fatal(@NotNull String message, @Nullable Category category, @Nullable ConfigurationQuickFix quickfix) {
    return new ValidationError(Severity.FATAL, message, category, quickfix);
  }

  @NotNull
  public static ValidationError warning(@NotNull String message) {
    return warning(message, null);
  }

  @NotNull
  public static ValidationError warning(@NotNull String message, @Nullable ConfigurationQuickFix quickfix) {
    return new ValidationError(Severity.WARNING, message, null, quickfix);
  }

  @NotNull
  public static ValidationError warning(@NotNull String message, @Nullable Category category, @Nullable ConfigurationQuickFix quickfix) {
    return new ValidationError(Severity.WARNING, message, category, quickfix);
  }

  @NotNull
  public static ValidationError info(@NotNull String message) {
    return info(message, null);
  }

  @NotNull
  public static ValidationError info(@NotNull String message, @Nullable ConfigurationQuickFix quickfix) {
    return new ValidationError(Severity.INFO, message, null, quickfix);
  }

  @NotNull
  public static ValidationError info(@NotNull String message, @Nullable Category category, @Nullable ConfigurationQuickFix quickfix) {
    return new ValidationError(Severity.INFO, message, category, quickfix);
  }

  @NotNull
  public static ValidationError fromException(@NotNull RuntimeConfigurationException e) {
    if (e instanceof RuntimeConfigurationError) {
      return fatal(e.getMessage(), e.getConfigurationQuickFix());
    }
    else {
      return warning(e.getMessage(), e.getConfigurationQuickFix());
    }
  }

  @NotNull
  public Severity getSeverity() {
    return mySeverity;
  }

  @NotNull
  public String getMessage() {
    return myMessage;
  }

  @Nullable
  public Category getCategory() {
    return myCategory;
  }

  @Nullable
  public ConfigurationQuickFix getQuickfix() {
    return myQuickfix;
  }

  public boolean isFatal() {
    return mySeverity.equals(Severity.FATAL);
  }

  @Override
  public int compareTo(ValidationError o) {
    return mySeverity.compareTo(o.getSeverity());
  }
}
