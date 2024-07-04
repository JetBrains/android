/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.avd

internal enum class Boot(internal val properties: Map<String, String>, private val text: String) {
  COLD(
    mapOf(
      "fastboot.chosenSnapshotFile" to "",
      "fastboot.forceChosenSnapshotBoot" to "no",
      "fastboot.forceColdBoot" to "yes",
      "fastboot.forceFastBoot" to "no",
    ),
    "Cold",
  ),
  QUICK(
    mapOf(
      "fastboot.chosenSnapshotFile" to "",
      "fastboot.forceChosenSnapshotBoot" to "no",
      "fastboot.forceColdBoot" to "no",
      "fastboot.forceFastBoot" to "yes",
    ),
    "Quick",
  );

  override fun toString() = text
}
