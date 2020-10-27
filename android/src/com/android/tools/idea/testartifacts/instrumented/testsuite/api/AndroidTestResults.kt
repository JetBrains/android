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
package com.android.tools.idea.testartifacts.instrumented.testsuite.api

import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import java.io.File

/**
 * An interface to access to instrumentation test results of a single test case executed
 * on multiple devices.
 */
interface AndroidTestResults {
  /**
   * A name of the test method.
   */
  val methodName: String
  /**
   * A name of the test class
   */
  val className: String
  /**
   * A package name of the tested APP.
   */
  val packageName: String

  /**
   * Returns the test case result of a given device. Null if the test is not executed on a given device.
   */
  fun getTestCaseResult(device: AndroidDevice): AndroidTestCaseResult?

  /**
   * Returns the aggregated test result.
   */
  fun getTestResultSummary(): AndroidTestCaseResult

  /**
   * Returns the logcat message emitted during the test on a given device.
   */
  fun getLogcat(device: AndroidDevice): String

  /**
   * Returns an error stack trace or empty if a test passes.
   */
  fun getErrorStackTrace(device: AndroidDevice): String

  /**
   * Returns a benchmark test results.
   */
  fun getBenchmark(device: AndroidDevice): String
  /**
   * Returns the snapshot artifact from Android Test Retention if available.
   */
  fun getRetentionSnapshot(device: AndroidDevice): File?
}

/**
 * Returns the name of the test case.
 */
fun AndroidTestResults.getTestCaseName(): String = "$className.$methodName"

/**
 * Returns the fully qualified name of the test case.
 */
fun AndroidTestResults.getFullTestCaseName(): String {
  return if (packageName.isBlank()) {
    "$className.$methodName"
  } else {
    "$packageName.$className.$methodName"
  }
}
