/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class RenderLoggerTest {
  @After
  public void tearDown() {
    RenderLogger.resetFidelityErrorsFilters();
  }

  @Test
  public void testFidelityWarningIgnore() {
    final String TAG = "TEST_WARNING";
    final String TEXT = "Test fidelity warning";

    RenderLogger logger = new RenderLogger(null, null);
    logger.fidelityWarning(TAG, TEXT, null, null, null);
    // Check that duplicates are ignored
    logger.fidelityWarning(TAG, TEXT, null, null, null);
    assertEquals(1, logger.getFidelityWarnings().size());
    assertEquals(TAG, logger.getFidelityWarnings().get(0).getTag());
    assertEquals(TEXT, logger.getFidelityWarnings().get(0).getClientData());

    // Test ignoring a single message
    logger = new RenderLogger(null, null);
    RenderLogger.ignoreFidelityWarning(TEXT);
    logger.fidelityWarning(TAG, TEXT, null, null, null);
    assertEquals(0, logger.getFidelityWarnings().size());
    logger.fidelityWarning(TAG, "This should't be ignored", null, null, null);
    assertEquals("This should't be ignored", logger.getFidelityWarnings().get(0).getClientData());

    // Test ignore all
    logger = new RenderLogger(null, null);
    RenderLogger.ignoreAllFidelityWarnings();
    logger.fidelityWarning(TAG, TEXT, null, null, null);
    logger.fidelityWarning(TAG, "This should be ignored", null, null, null);
    logger.fidelityWarning(TAG, "And this", new Throwable("Test"), null, null);
    assertEquals(0, logger.getFidelityWarnings().size());
  }
}