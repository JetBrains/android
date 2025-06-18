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
package com.android.tools.idea.testartifacts.instrumented.testsuite.model

import java.io.File

/**
 * Represents a single step in a test case.
 *
 * A test step represents one of potentially many steps that make up a test case. For example, in a
 * Journeys test, each action in the test is a test step. If any of the test steps fails, then the
 * test case as a whole fails, and subsequent steps will not be run.
 *
 * This model allows us to display the steps in the test tree hierarchy without affecting the
 * overall test counts (e.g. number of passed/failed tests).
 *
 * **Note**: When importing test results with test steps into the standard IntelliJ test panel, the
 * test steps will not be displayed.
 */
data class AndroidTestStep(
  val id: String,
  val index: Int,
  val name: String,
  var result: AndroidTestCaseResult = AndroidTestCaseResult.SCHEDULED,
  var logcat: String = "",
  var errorStackTrace: String = "",
  var startTimestampMillis: Long? = null,
  var endTimestampMillis: Long? = null,
  var benchmark: String = "",
  var retentionInfo: File? = null,
  var retentionSnapshot: File? = null,
  val additionalTestArtifacts: MutableMap<String, String> = mutableMapOf()
)