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

import java.io.File


/**
 * Encapsulates an Android test case metadata to be displayed in Android test suite view.
 *
 * @param id a test case identifier. This can be arbitrary string as long as it is unique to other test cases.
 * @param methodName a name of the test method
 * @param className a name of the test class
 * @param packageName a name of the tested APP
 * @param result a result of this test case. Null when the test case execution hasn't finished yet.
 * @param logcat a logcat message emitted during this test case.
 * @param errorStackTrace an error stack trace. Empty if a test passes.
 * @param startTimestampMillis a timestamp when this test execution starts in milliseconds in unix time.
 * @param endTimestampMillis a timestamp when this test execution finishes in milliseconds in unix time.
 * @param benchmark an output from AndroidX Benchmark library.
 * @param retentionSnapshot an Android Test Retention snapshot artifact.
 */
data class AndroidTestCase(val id: String,
                           val methodName: String,
                           val className: String,
                           val packageName: String,
                           var result: AndroidTestCaseResult = AndroidTestCaseResult.SCHEDULED,
                           var logcat: String = "",
                           var errorStackTrace: String = "",
                           var startTimestampMillis: Long? = null,
                           var endTimestampMillis: Long? = null,
                           var benchmark: String = "",
                           var retentionSnapshot: File? = null)

/**
 * A result of a test case execution.
 */
enum class AndroidTestCaseResult(val isTerminalState: Boolean) {
  /**
   * A test case is failed.
   */
  FAILED(true),

  /**
   * A test case is skipped by test runner.
   */
  SKIPPED(true),

  /**
   * A test case is passed.
   */
  PASSED(true),

  /**
   * A test case is in progress.
   */
  IN_PROGRESS(false),

  /**
   * A test case which is scheduled to run ends up with cancelled.
   */
  CANCELLED(true),

  /**
   * A test case is scheduled but not started yet.
   */
  SCHEDULED(false)
}