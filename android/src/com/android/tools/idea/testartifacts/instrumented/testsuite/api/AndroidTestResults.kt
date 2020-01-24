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

/**
 * An interface to access to instrumentation test results of a single test case executed
 * on multiple devices.
 */
interface AndroidTestResults {
  /**
   * A name of the test case. The name is unique in associated instrumentation test run.
   */
  val testCaseName: String

  /**
   * Returns the test case result of a given device. Null if the test is not executed on a given device.
   */
  fun getTestCaseResult(device: AndroidDevice): AndroidTestCaseResult?
}