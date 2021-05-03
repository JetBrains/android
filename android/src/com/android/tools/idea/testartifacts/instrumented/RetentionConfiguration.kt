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
package com.android.tools.idea.testartifacts.instrumented

enum class EnableRetention(private val displayValue: String) {
  YES("Yes"), NO("No"), USE_GRADLE("Use build.gradle settings");

  override fun toString(): String {
    return displayValue
  }
}

/**
 * Configurations for "Emulator Snapshot for Test Failures" (a.k.a Android Test Retention, Icebox.)
 *
 * @param enabled enable "Emulator Snapshot for Test Failures" or not. Can be set to "USE_GRADLE" to ignore the setup
 *        here and use those from build.gradle.
 * @param maxSnapshots maximum number of snapshots it can take per device for the test run
 * @param compressSnapshots compress snapshots or not
 */
data class RetentionConfiguration(
  val enabled: EnableRetention = EnableRetention.NO,
  val maxSnapshots: Int = 2,
  val compressSnapshots: Boolean = false
)