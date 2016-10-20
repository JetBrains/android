/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import org.jetbrains.annotations.NotNull;

import static com.google.common.truth.Truth.assertThat;

public class CachedDependencyNotFoundErrorHandlerTest extends SyncErrorHandlerTestCase {
  @NotNull
  @Override
  protected SyncErrorHandler createErrorHandler() {
    return new CachedDependencyNotFoundErrorHandler();
  }

  public void testHandleErrorWithNotificationData() {
    String expectedNotificationMessage = "No cached version of dependency, available for offline mode.";
    String text = expectedNotificationMessage + "\nExtra error message.";
    ExternalSystemException cause = new ExternalSystemException(text);

    // Verify that the error handler was capable of handling the exception.
    boolean result = myErrorHandler.handleError(createExternalSystemException(cause), myNotification, myProject);
    assertTrue(result);

    // Verity that SyncMessages was invoked to update NotificationData.
    String message = myNotification.getMessage();
    assertThat(message).contains(expectedNotificationMessage);
  }
}
