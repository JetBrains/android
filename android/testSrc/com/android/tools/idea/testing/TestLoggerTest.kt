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
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestLoggerFactory
import org.junit.ClassRule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

class TestLoggerTest {
  companion object {
    @JvmStatic
    @get:ClassRule
    val appRule = ApplicationRule()
  }

  @Test
  fun testLoggerWritesToLogFile() {
    val logFile = TestLoggerFactory.getTestLogDir().resolve("idea.log")
    val before = logFile.readText()
    thisLogger().warn("A sample warning message")
    val after = logFile.readText()
    val diff = after.removePrefix(before)
    assertThat(diff).contains("A sample warning message")
  }

  @Test
  fun testConfigFileExists() {
    val configFile = System.getProperty(PathManager.PROPERTY_LOG_CONFIG_FILE)
    assertThat(configFile).isNotNull()
    assertThat(Files.exists(Path.of(configFile))).isTrue()
  }

  // Ideally we could also test that warnings are logged to System.err. But, unfortunately,
  // the relevant ConsoleAppender caches System.err before we can reassign it here.
}
