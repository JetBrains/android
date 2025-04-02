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
package com.android.tools.tests

import com.android.testutils.TestUtils
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A rule that ensures any Gradle daemons started over the course of some tests will be shutdown
 * and also collecting the daemon logs for the failed tests.
 *
 * This may be too aggressive for test classes, but it can be a useful rule for test suites.
 */
class GradleDaemonsRule : TestWatcher() {
  override fun succeeded(description: Description?) {
    closeConnection()
  }

  override fun failed(e: Throwable, description: Description) {
    closeConnection()
    collectDaemonLogs()
  }

  private fun closeConnection() {
    DefaultGradleConnector.close()
  }

  private fun collectDaemonLogs() {
    val gradleHome = File(System.getProperty("gradle.user.home"))
    val testOutputDir = TestUtils.getTestOutputDir()
    gradleHome.resolve("daemon").walk()
      .filter { it.name.endsWith("out.log") }
      .forEach {
        // Replace existing just in case the test itself also attempts to collect it
        Files.copy(it.toPath(), testOutputDir.resolve(it.name), StandardCopyOption.REPLACE_EXISTING)
      }

  }
}
