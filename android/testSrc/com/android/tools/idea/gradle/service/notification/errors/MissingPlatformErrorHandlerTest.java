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
package com.android.tools.idea.gradle.service.notification.errors;

import junit.framework.TestCase;

import static com.android.tools.idea.gradle.service.notification.errors.MissingPlatformErrorHandler.getMissingPlatform;

/**
 * Tests for {@link MissingPlatformErrorHandler}
 */
public class MissingPlatformErrorHandlerTest extends TestCase {
  public void testMissingPlatform() throws Exception {
    assertEquals("android-21", getMissingPlatform("Failed to find target with hash string 'android-21' in: /pat/tp/sdk"));
    assertEquals("android-21", getMissingPlatform("failed to find target with hash string 'android-21' in: /pat/tp/sdk"));
    assertEquals("android-21", getMissingPlatform("Cause: Failed to find target with hash string 'android-21' in: /pat/tp/sdk"));
    assertEquals("android-21", getMissingPlatform("Cause: failed to find target with hash string 'android-21' in: /pat/tp/sdk"));
    assertEquals("android-21", getMissingPlatform("Failed to find target android-21 : /pat/tp/sdk"));
    assertEquals("android-21", getMissingPlatform("failed to find target android-21 : /pat/tp/sdk"));
    assertEquals("android-21", getMissingPlatform("Cause: Failed to find target android-21 : /pat/tp/sdk"));
    assertEquals("android-21", getMissingPlatform("Cause: failed to find target android-21 : /pat/tp/sdk"));
    assertEquals("android-21", getMissingPlatform("Failed to find target android-21"));
    assertEquals("android-21", getMissingPlatform("failed to find target android-21"));
    assertEquals("android-21", getMissingPlatform("Cause: Failed to find target android-21"));
    assertEquals("android-21", getMissingPlatform("Cause: failed to find target android-21"));
  }
}
