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
 * Encapsulates an Android test case metadata to be displayed in Android test suite view.
 *
 * @param id a test case identifier. This can be arbitrary string as long as it is unique to other test cases.
 * @param name a display name of this test case
 * @param result a result of this test case. Null when the test case execution hasn't finished yet.
 */
data class AndroidTestCase(val id: String,
                           val name: String,
                           var result: AndroidTestCaseResult? = null)

/**
 * A result of a test case execution.
 */
enum class AndroidTestCaseResult {
  /**
   * A test case is passed.
   */
  PASSED,

  /**
   * A test case is failed.
   */
  FAILED,

  /**
   * A test case is skipped by test runner.
   */
  SKIPPED
}