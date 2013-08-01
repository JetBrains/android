/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import junit.framework.TestCase;
import org.gradle.api.internal.LocationAwareException;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * Tests for {@link ProjectImportErrorHandler}.
 */
@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
public class ProjectImportErrorHandlerTest extends TestCase {
  private ProjectImportErrorHandler myErrorHandler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myErrorHandler = new ProjectImportErrorHandler();
  }

  public void testGetUserFriendlyErrorWithOldGradleVersion() {
    ClassNotFoundException rootCause = new ClassNotFoundException(ToolingModelBuilderRegistry.class.getName());
    Throwable error = new Throwable(rootCause);
    RuntimeException realCause = myErrorHandler.getUserFriendlyError(error, null);
    assertTrue(realCause.getMessage().contains("old, unsupported version of Gradle"));
  }

  public void testGetUserFriendlyErrorWithMissingAndroidSupportRepository() {
    RuntimeException rootCause = new RuntimeException("Could not find any version that matches com.android.support:support-v4:13.0.+");
    Throwable error = new Throwable(rootCause);
    RuntimeException realCause = myErrorHandler.getUserFriendlyError(error, null);
    assertTrue(realCause.getMessage().contains("Please install the Android Support Repository"));
  }

  public void testGetUserFriendlyErrorWithMissingAndroidSupportRepository2() {
    RuntimeException rootCause = new RuntimeException("Could not find com.android.support:support-v4:13.0.0");
    Throwable error = new Throwable(rootCause);
    RuntimeException realCause = myErrorHandler.getUserFriendlyError(error, null);
    assertTrue(realCause.getMessage().contains("Please install the Android Support Repository"));
  }

  public void testGetUserFriendlyErrorWithPlatformVersionNotFound() {
    String causeMsg = "failed to find target current";
    RuntimeException rootCause = new IllegalStateException(causeMsg);
    String locationMsg = "Build file '~/project/build.gradle' line: 86";

    RuntimeException locationError = new RuntimeException(locationMsg, rootCause) {
      @Override
      public String toString() {
        return LocationAwareException.class.getName() + ": " + super.toString();
      }
    };

    Throwable error = new Throwable(locationError);

    RuntimeException realCause = myErrorHandler.getUserFriendlyError(error, null);
    String actualMsg = realCause.getMessage();
    assertTrue(actualMsg.contains(locationMsg));
    assertTrue(actualMsg.contains("Cause: " + causeMsg));
  }
}
