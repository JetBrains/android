/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.model


/**
 * Encapsulates an Android test suite metadata to be displayed in Android test suite view.
 *
 * @param id a test suite identifier. This can be arbitrary string as long as it is unique to other test suites.
 * @param name a display name of this test suite
 * @param testCaseCount a number of test cases in the suite including suppressed test cases
 * @param result a result of the test suite. Null when the test suite execution hasn't finished yet.
 */
data class AndroidTestSuite(val id: String,
                            val name: String,
                            val testCaseCount: Int,
                            var result: AndroidTestSuiteResult? = null)

/**
 * A result of a test suite execution.
 */
enum class AndroidTestSuiteResult {
  /**
   * All tests in a test suite are passed.
   */
  PASSED,

  /**
   * At least one test case in a test suite is failed.
   */
  FAILED,

  /**
   * A test suite execution is aborted by tool failure.
   */
  ABORTED,

  /**
   * A test suite execution is cancelled by a user before it completes.
   */
  CANCELLED
}