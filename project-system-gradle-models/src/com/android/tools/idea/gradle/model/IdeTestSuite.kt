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
 * Test Suites are defined at the project level and targets selected variants.
 *
 * At the project level, you will find information related to the suites like the configured
 * source folders as well as the JUnit engines configured to run the tests.
 *
 * At the variant level [IdeTestSuiteVariantTarget] will provide information like which devices are
 * targeted and the Gradle task to run execute the test suite.
 */
interface IdeTestSuite {
  /**
   * Test suite name.
   */
  val name: String

  /**
   * Configured sources for the test suite.
   *
   * Each test suite source will be processed by AGP and binaries will be provided to the JUnit
   * test engines.
   */
  val sources: List<IdeTestSuiteSource>

  /**
   * JUnit engines configuration
   */
  val junitEngineInfo: IdeJUnitEngineInfo

  /**
   * Targeted variants by name.
   */
  val targetedVariants: Collection<String>
}