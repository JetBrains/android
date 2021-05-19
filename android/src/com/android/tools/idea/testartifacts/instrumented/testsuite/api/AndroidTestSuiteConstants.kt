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

import com.intellij.openapi.util.Key

/**
 * A key to access to [AndroidTestResultListener] from [com.intellij.openapi.util.UserDataHolder].
 */
@JvmField
val ANDROID_TEST_RESULT_LISTENER_KEY = Key<AndroidTestResultListener>(
  "android.testartifacts.instrumented.testsuite.ANDROID_TEST_RESULT_LISTENER_KEY")

object ActionPlaces {
  const val ANDROID_TEST_SUITE_TABLE = "AndroidTestSuiteTable"
  const val ANDROID_TEST_SUITE_RAW_LOG = "AndroidTestSuiteRawLog"
  const val ANDROID_TEST_SUITE_DETAILS_VIEW_LOG = "AndroidTestSuiteDetailsViewLog"
  const val ANDROID_TEST_SUITE_DETAILS_VIEW_BENCHMARK = "AndroidTestSuiteDetailsViewBenchmark"
}