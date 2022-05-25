/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.ide.gradle.model

/** List of collected application IDs for an android gradle project */
interface LegacyApplicationIdModel {
  /**
   * Map from Android Gradle Plugin Component name to the associated application ID.
   *
   * For example for a simple application project this might be
   * ```
   * mapOf(
   *     "debug" to "com.example.app.debug",
   *     "debugAndroidTest" to "com.example.app.test",
   *     "release" to "com.example.app",
   * )
   * ```
   */
  val componentToApplicationIdMap: Map<String, String>
  /**
   * Exceptions caught from trying to resolve the application IDs from the model, collected in order to report them as sync issues.
   */
  val problems: List<Exception>
}