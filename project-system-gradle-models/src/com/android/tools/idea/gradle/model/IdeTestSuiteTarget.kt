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
 * Test suite target information.
 */
interface IdeTestSuiteTarget {
  /**
   * Target name as defined using the AGP DSL.
   */
  val targetName: String

  /**
   * Gradle Test task name to run the test suite for that variant's target.
   */
  val testTaskName: String

  /**
   * Targeted devices, possibly empty to use the connected devices.
   */
  val targetedDevices: Collection<String>
}
