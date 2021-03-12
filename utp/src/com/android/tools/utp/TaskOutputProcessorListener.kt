/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.utp

/**
 * An interface to receive test progress from [TaskOutputProcessor].
 */
interface TaskOutputProcessorListener {
  /**
   * Called when a test suite execution is started.
   */
  fun onTestSuiteStarted()

  /**
   * Called when a test case execution is started.
   */
  fun onTestCaseStarted()

  /**
   * Called when a test case execution is finished.
   */
  fun onTestCaseFinished()

  /**
   * Called when a test suite execution is finished.
   */
  fun onTestSuiteFinished()

  /**
   * Called when an error happens in AGP/UTP communication. If this method is invoked,
   * it's the last method call and no more methods including [onComplete] are invoked.
   */
  fun onError()

  /**
   * Called when all test result events are processed successfully.
   */
  fun onComplete()
}