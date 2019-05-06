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
package com.android.tools.idea.gradle.project.sync.idea;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.util.Pair;
import org.gradle.tooling.BuildException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests for {@link ProjectImportErrorHandler}.
 */
public class ProjectImportErrorHandlerTest {
  @Test
  public void getErrorLocationWithBuildFileWithLocation() {
    Pair<String, Integer> location = ProjectImportErrorHandler.getErrorLocation("Build file '/xyz/build.gradle' line: 3");
    assertNotNull(location);
    assertEquals("/xyz/build.gradle", location.getFirst());
    assertEquals(3, location.getSecond().intValue());
  }

  @Test
  public void getErrorLocationWithBuildFileWithoutLocation() {
    Pair<String, Integer> location = ProjectImportErrorHandler.getErrorLocation("Build file '/xyz/build.gradle'");
    assertNotNull(location);
    assertEquals("/xyz/build.gradle", location.getFirst());
    assertEquals(-1, location.getSecond().intValue());
  }

  @Test
  public void getErrorLocationWithSettingsFileWithLocation() {
    Pair<String, Integer> location = ProjectImportErrorHandler.getErrorLocation("Settings file '/xyz/settings.gradle' line: 3");
    assertNotNull(location);
    assertEquals("/xyz/settings.gradle", location.getFirst());
    assertEquals(3, location.getSecond().intValue());
  }

  @Test
  public void getErrorLocationWithSettingsFileWithoutLocation() {
    Pair<String, Integer> location = ProjectImportErrorHandler.getErrorLocation("Settings file '/xyz/settings.gradle'");
    assertNotNull(location);
    assertEquals("/xyz/settings.gradle", location.getFirst());
    assertEquals(-1, location.getSecond().intValue());
  }

  // https://code.google.com/p/android/issues/detail?id=226506
  @Test
  public void getUserFriendlyError() {
    ProjectImportErrorHandler errorHandler = new ProjectImportErrorHandler();

    IllegalStateException cause = new IllegalStateException("Failed to find Build Tools revision 24.0.3");
    BuildException error = new BuildException("Could not run build action.", cause);

    ExternalSystemException userFriendlyError = errorHandler.getUserFriendlyError(error, "fakeProjectPath", null);
    assertNotNull(userFriendlyError);
    assertEquals("Failed to find Build Tools revision 24.0.3", userFriendlyError.getMessage());
  }

  // https://code.google.com/p/android/issues/detail?id=226870
  @Test
  public void getUserFriendlyErrorWithLowerCase() {
    ProjectImportErrorHandler errorHandler = new ProjectImportErrorHandler();
    Throwable error = new Throwable("some random sync error");

    ExternalSystemException userFriendlyError = errorHandler.getUserFriendlyError(error, "fakeProjectPath", null);
    assertNotNull(userFriendlyError);
    assertEquals("Cause: some random sync error", userFriendlyError.getMessage());
  }
}
