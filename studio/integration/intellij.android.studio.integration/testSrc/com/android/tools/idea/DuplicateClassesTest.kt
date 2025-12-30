/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea

import com.android.tools.asdriver.tests.AndroidSystem
import java.util.concurrent.TimeUnit
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Invokes (internal) TestDuplicateClassesAction to ensure that there are no duplicate
 * instances of any classes on any plugin's classpath at runtime.
 */
class DuplicateClassesTest {
  @get:Rule val system: AndroidSystem = AndroidSystem.standard()

  @Before
  fun setup() {
    system.installation.addVmOption("-Didea.is.internal=true")
  }

  @Test
  fun testDuplicateClasses() {
    system.runStudioWithoutProject().use { studio ->
      studio.executeAction("Internal.TestDuplicateClasses")
      system.installation.ideaLog.waitForMatchingLine(
        ".*duplicate classes scan done",
        3,
        TimeUnit.MINUTES,
      )
      if (!system.installation.ideaLog.hasMatchingLine(".*No duplicate classes found!")) {
        fail("Duplicate classes found, see idea.log for details.")
      }
    }
  }
}
