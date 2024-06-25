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

import com.intellij.openapi.util.SystemInfo
import org.junit.Assume
import org.junit.Ignore
import org.junit.Test

/** This test is added to all IDE test suites. */
class LastInIdeaTestSuite {
  /**
   * Checks for IDEA project leaks and disposer tree leaks.
   * To disable this check in a specific test target, set the system property idea.leak.check.enabled to false.
   *
   * NOTE: By default, this test only runs in Bazel. To enable running it in the IDE, adjust the test run configuration.
   */
  @Test
  @Ignore("b/352573491")
  fun checkForLeaks() {
    Assume.assumeFalse(SystemInfo.isWindows) // TODO(b/330534295): debug leaked projects on Windows with IntelliJ 2024.1.
    Assume.assumeTrue(System.getProperty("idea.leak.check.enabled", "true").toBoolean())
    try {
      LeakCheckerRule.checkForLeaks()
    }
    catch (e: AssertionError) {
      val header = """
        The IntelliJ test framework appears to have detected a memory leak
        in this test target (see below). Read the error message carefully to discern
        what caused the leak. If an IDE project instance leaked, then the project name will
        generally indicate which specific test was involved. If available, the leak trace
        (path from GC roots) will help you find the root cause of the leak. Note, this leak
        check is added to all IDE test target automatically. If you need to temporarily
        disable leak checks in a specific test target, set the system property
        idea.leak.check.enabled to false.
      """.trimIndent()
      throw RuntimeException(header, e)
    }
  }
}
