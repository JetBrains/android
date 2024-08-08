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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import java.util.Collection;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/** An error occuring during a blaze validation */
@Immutable
public final class BlazeValidationError {

  private final String error;

  public BlazeValidationError(String validationFailure) {
    this.error = validationFailure;
  }

  public String getError() {
    return error;
  }

  public static void collect(
      @Nullable Collection<BlazeValidationError> errors, BlazeValidationError error) {
    if (errors != null) {
      errors.add(error);
    }
  }

  /**
   * Shows an error dialog.
   *
   * @return true if there are no errors
   */
  public static boolean verify(
      Project project, String title, Collection<BlazeValidationError> errors) {
    if (!errors.isEmpty()) {
      BlazeValidationError error = errors.iterator().next();
      Messages.showErrorDialog(project, error.getError(), title);
      return false;
    }
    return true;
  }
}
