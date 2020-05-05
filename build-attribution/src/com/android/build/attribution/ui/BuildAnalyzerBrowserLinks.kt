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
package com.android.build.attribution.ui

enum class BuildAnalyzerBrowserLinks(
  val urlTarget: String
) {
  NON_INCREMENTAL_ANNOTATION_PROCESSORS("https://d.android.com/r/tools/build-attribution/non-incremental-ap"),
  CRITICAL_PATH("https://developer.android.com/r/tools/build-attribution/critical-path"),
  DUPLICATE_OUTPUT_FOLDER_ISSUE("https://d.android.com/r/tools/build-attribution/duplicate-output-folder"),
  NO_OUTPUTS_DECLARED_ISSUE("https://d.android.com/r/tools/build-attribution/no-task-outputs-declared"),
  UP_TO_DATE_EQUALS_FALSE_ISSUE("https://d.android.com/r/tools/build-attribution/upToDateWhen-equals-false"),
  OPTIMIZE_CONFIGURATION_PHASE("https://d.android.com/r/tools/build-attribution/optimize-configuration-phase")
}
