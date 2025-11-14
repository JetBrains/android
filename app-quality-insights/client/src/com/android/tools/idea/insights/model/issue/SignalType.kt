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
package com.android.tools.idea.insights.model.issue

enum class SignalType(private val readableName: String, val description: String?) {
  SIGNAL_UNSPECIFIED("All signal states", null),
  SIGNAL_EARLY(
    "Early",
    "Issues with a high percentage of events within the first five seconds of a user's session",
  ),
  SIGNAL_FRESH("Fresh", "New issues that appeared in the last seven days"),
  SIGNAL_REGRESSED("Regressed", "Issues that have reoccurred and that we've reopened"),
  SIGNAL_REPETITIVE("Repetitive", "Issues with events that happened multiple times per user");

  override fun toString() = readableName
}
