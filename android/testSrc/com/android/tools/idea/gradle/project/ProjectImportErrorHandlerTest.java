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

import com.intellij.openapi.util.Pair;
import junit.framework.TestCase;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * Tests for {@link ProjectImportErrorHandler}.
 */
@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
public class ProjectImportErrorHandlerTest extends TestCase {
  private ProjectImportErrorHandler myErrorHandler;
  private String myProjectPath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myErrorHandler = new ProjectImportErrorHandler();
    myProjectPath = "basic";
  }

  /**
   * org.gradle.api.internal.MissingMethodException can be thrown for bad gradle DSL also not only for old gradle version.
   * It will confuse user.
   */
  public void ignoreTestGetUserFriendlyErrorWithOldGradleVersion() {
    ClassNotFoundException rootCause = new ClassNotFoundException(ToolingModelBuilderRegistry.class.getName());
    Throwable error = new Throwable(rootCause);
    RuntimeException realCause = myErrorHandler.getUserFriendlyError(error, myProjectPath, null);
    assertNotNull(realCause);
    assertTrue(realCause.getMessage().contains("old, unsupported version of Gradle"));
  }

  public void testGetUserFriendlyErrorWithMissingAndroidSupportRepository() {
    RuntimeException rootCause = new RuntimeException("Could not find any version that matches com.android.support:support-v4:13.0.+");
    Throwable error = new Throwable(rootCause);
    RuntimeException realCause = myErrorHandler.getUserFriendlyError(error, myProjectPath, null);
    assertNotNull(realCause);
    assertTrue(realCause.getMessage().contains("Please install the Android Support Repository"));
  }

  public void testGetUserFriendlyErrorWithMissingAndroidSupportRepository2() {
    RuntimeException rootCause = new RuntimeException("Could not find com.android.support:support-v4:13.0.0");
    Throwable error = new Throwable(rootCause);
    RuntimeException realCause = myErrorHandler.getUserFriendlyError(error, myProjectPath, null);
    assertNotNull(realCause);
    assertTrue(realCause.getMessage().contains("Please install the Android Support Repository"));
  }

  public void testGetUserFriendlyErrorWithOutOfMemoryError() {
    OutOfMemoryError rootCause = new OutOfMemoryError("Java heap space");
    Throwable error = new Throwable(rootCause);
    RuntimeException realCause = myErrorHandler.getUserFriendlyError(error, myProjectPath, null);
    assertNotNull(realCause);
    assertEquals("Out of memory: Java heap space", realCause.getMessage());
  }

  public void testGetUserFriendlyErrorWithNoSuchMethodError() {
    NoSuchMethodError rootCause = new NoSuchMethodError("org.slf4j.spi.LocationAwareLogger.log");
    Throwable error = new Throwable(rootCause);
    RuntimeException realCause = myErrorHandler.getUserFriendlyError(error, myProjectPath, null);
    assertNotNull(realCause);
    assertEquals("Unable to find method 'org.slf4j.spi.LocationAwareLogger.log'.", realCause.getMessage());
  }

  public void testGetUserFriendlyErrorWithClassNotFoundException() {
    ClassNotFoundException rootCause = new ClassNotFoundException("com.android.utils.ILogger");
    Throwable error = new Throwable(rootCause);
    RuntimeException realCause = myErrorHandler.getUserFriendlyError(error, myProjectPath, null);
    assertNotNull(realCause);
    assertEquals("Unable to load class 'com.android.utils.ILogger'.", realCause.getMessage());
  }

  public void testGetUserFriendlyErrorWithClassNotFoundExceptionWithLongerMessage() {
    ClassNotFoundException rootCause = new ClassNotFoundException("com.novoda.gradle.robolectric.RobolectricPlugin not found.");
    Throwable error = new Throwable(rootCause);
    RuntimeException realCause = myErrorHandler.getUserFriendlyError(error, myProjectPath, null);
    assertNotNull(realCause);
    assertEquals("Unable to load class 'com.novoda.gradle.robolectric.RobolectricPlugin'.", realCause.getMessage());
  }

  public void testGetErrorLocationWithBuildFileWithLocation() {
    Pair<String, Integer> location = ProjectImportErrorHandler.getErrorLocation("Build file '/xyz/build.gradle' line: 3");
    assertNotNull(location);
    assertEquals("/xyz/build.gradle", location.getFirst());
    assertEquals(3, location.getSecond().intValue());
  }

  public void testGetErrorLocationWithBuildFileWithoutLocation() {
    Pair<String, Integer> location = ProjectImportErrorHandler.getErrorLocation("Build file '/xyz/build.gradle'");
    assertNotNull(location);
    assertEquals("/xyz/build.gradle", location.getFirst());
    assertEquals(-1, location.getSecond().intValue());
  }

  public void testGetErrorLocationWithSettingsFileWithLocation() {
    Pair<String, Integer> location = ProjectImportErrorHandler.getErrorLocation("Settings file '/xyz/settings.gradle' line: 3");
    assertNotNull(location);
    assertEquals("/xyz/settings.gradle", location.getFirst());
    assertEquals(3, location.getSecond().intValue());
  }

  public void testGetErrorLocationWithSettingsFileWithoutLocation() {
    Pair<String, Integer> location = ProjectImportErrorHandler.getErrorLocation("Settings file '/xyz/settings.gradle'");
    assertNotNull(location);
    assertEquals("/xyz/settings.gradle", location.getFirst());
    assertEquals(-1, location.getSecond().intValue());
  }
}
