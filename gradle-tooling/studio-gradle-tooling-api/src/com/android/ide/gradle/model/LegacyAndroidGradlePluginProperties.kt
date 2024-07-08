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

/**
 * Model that fetches information from older versions of AGP of various properties which
 * were added to the returned models of newer AGPs, in order to simplify consuming code in
 * Android Studio.
 */
interface LegacyAndroidGradlePluginProperties {
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
  /** `AndroidProject.namespace` for AGP 4.1 and below. */
  val namespace: String?
  /** `AndroidProject.androidTestNamespace` for AGP 4.1 and below. */
  val androidTestNamespace: String?
  /** For AGP < 8.7, whether Android resource data binding is enabled. */
  val dataBindingEnabled: Boolean?
  /**
   * Exceptions caught from trying to read the additional information from the model, which should be reported as sync warnings.
   */
  val problems: List<Exception>
}