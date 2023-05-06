/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.tests

import org.junit.Assume
import org.junit.Test

/** This test is added to all IDE test suites. */
class LastInIdeaTestSuite {
  /**
   * Checks for IDEA project leaks and disposer tree leaks.
   * To disable this check in a specific test target, set the system property idea.leak.check.enabled to false.
   */
  @Test
  fun checkForLeaks() {
    Assume.assumeTrue(System.getProperty("idea.leak.check.enabled", "true").toBoolean())
    LeakCheckerRule.checkForLeaks()
  }
}
