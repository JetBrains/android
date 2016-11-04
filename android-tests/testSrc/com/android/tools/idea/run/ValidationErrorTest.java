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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import junit.framework.TestCase;

import java.util.List;

/**
 * Tests for {@link ValidationError}
 */
public class ValidationErrorTest extends TestCase {
  public void testSortOrder() {
    List<ValidationError> errors = ImmutableList.of(
      ValidationError.warning("warning1"),
      ValidationError.fatal("fatal2"),
      ValidationError.fatal("fatal1"),
      ValidationError.warning("warning2")
    );
    // The top error should be the first encountered fatal error.
    ValidationError topError = Ordering.natural().max(errors);
    assertTrue(topError.isFatal());
    assertEquals("fatal2", topError.getMessage());
    // All the warnings should be followed by all the fatal errors.
    // Within each section, errors should be in the order encountered (stable sort based on severity alone).
    List<ValidationError> sortedErrors = Ordering.natural().sortedCopy(errors);

    assertFalse(sortedErrors.get(0).isFatal());
    assertEquals("warning1", sortedErrors.get(0).getMessage());
    assertFalse(sortedErrors.get(1).isFatal());
    assertEquals("warning2", sortedErrors.get(1).getMessage());

    assertTrue(sortedErrors.get(2).isFatal());
    assertEquals("fatal2", sortedErrors.get(2).getMessage());
    assertTrue(sortedErrors.get(3).isFatal());
    assertEquals("fatal1", sortedErrors.get(3).getMessage());
  }
}
