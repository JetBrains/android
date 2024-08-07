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
package com.google.idea.blaze.base.ui;

import static com.google.common.base.Preconditions.checkState;

import com.intellij.openapi.options.CancelledConfigurationException;
import com.intellij.openapi.options.ConfigurationException;
import javax.annotation.Nullable;

/** Pair of (status, validation error) */
public class BlazeValidationResult {

  /** The status of the validation. */
  public enum Status {
    SUCCESS,
    FAILURE,
    CANCELLED
  }

  private final Status status;
  @Nullable private final String error;

  private static final BlazeValidationResult SUCCESS =
      new BlazeValidationResult(Status.SUCCESS, null);
  private static final BlazeValidationResult CANCELLED =
      new BlazeValidationResult(Status.CANCELLED, null);

  private BlazeValidationResult(Status status, @Nullable String error) {
    checkState(status.equals(Status.FAILURE) == (error != null));
    this.status = status;
    this.error = error;
  }

  public static BlazeValidationResult success() {
    return SUCCESS;
  }

  public static BlazeValidationResult failure(BlazeValidationError error) {
    return failure(error.getError());
  }

  public static BlazeValidationResult failure(String error) {
    return new BlazeValidationResult(Status.FAILURE, error);
  }

  /**
   * Represents a state where the validation code presented the user with an error dialog and the
   * user chose 'cancel', to return to the wizard.
   */
  public static BlazeValidationResult cancelled() {
    return CANCELLED;
  }

  public boolean isSuccess() {
    return status.equals(Status.SUCCESS);
  }

  public void throwConfigurationExceptionIfNotSuccess() throws ConfigurationException {
    switch (status) {
      case SUCCESS:
        break;
      case FAILURE:
        throw new ConfigurationException(error);
      case CANCELLED:
        throw new CancelledConfigurationException();
    }
  }
}
