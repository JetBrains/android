/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testing

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.io.readText
import org.junit.Test
import java.nio.file.Paths

class TestLoggerTest : UsefulTestCase() {

  @Test
  fun testLoggerWritesToLogFile() {
    val logFile = Paths.get(TestLoggerFactory.getTestLogDir(), "idea.log")
    val before = logFile.readText()
    thisLogger().warn("A sample warning message")
    val after = logFile.readText()
    val diff = after.removePrefix(before)
    assertThat(diff).contains("A sample warning message")
  }

  // Ideally we could also test that warnings are logged to System.err. But, unfortunately,
  // the relevant ConsoleAppender caches System.err before we can reassign it here.
}
