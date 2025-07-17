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
package com.android.tools.idea.gradle.model

/**
 * Test suite variant configuration.
 *
 * Test suite definition is obtained at the project level using [IdeAndroidProject.testSuites].
 * Here you will find the list of targets defined for this variant. Each target have an associated
 * test task.
 */
interface IdeTestSuiteVariantTarget {
  /**
   * Name of the test suite.
   */
  val suiteName: String

  /**
   * Name of the targeted variant.
   */
  val targetedVariantName: String

  /**
   * List of defined targets for this test suite in the current variant context.
   */
  val targets: Collection<IdeTestSuiteTarget>
}
