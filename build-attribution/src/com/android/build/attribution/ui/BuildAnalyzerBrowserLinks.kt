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

import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent

enum class BuildAnalyzerBrowserLinks(
  val urlTarget: String,
  val analyticsValue: BuildAttributionUiEvent.OutgoingLinkTarget
) {
  NON_INCREMENTAL_ANNOTATION_PROCESSORS(
    "https://d.android.com/r/tools/build-attribution/incremental-annotation-processor-support",
    BuildAttributionUiEvent.OutgoingLinkTarget.NON_INCREMENTAL_ANNOTATION_PROCESSORS_HELP
  ),
  CRITICAL_PATH(
    "https://developer.android.com/r/tools/build-attribution/critical-path",
    BuildAttributionUiEvent.OutgoingLinkTarget.CRITICAL_PATH_HELP
  ),
  DUPLICATE_OUTPUT_FOLDER_ISSUE(
    "https://d.android.com/r/tools/build-attribution/duplicate-output-folder",
    BuildAttributionUiEvent.OutgoingLinkTarget.DUPLICATE_OUTPUT_FOLDER_ISSUE_HELP
  ),
  NO_OUTPUTS_DECLARED_ISSUE(
    "https://d.android.com/r/tools/build-attribution/no-task-outputs-declared",
    BuildAttributionUiEvent.OutgoingLinkTarget.NO_OUTPUTS_DECLARED_ISSUE_HELP
  ),
  UP_TO_DATE_EQUALS_FALSE_ISSUE(
    "https://d.android.com/r/tools/build-attribution/upToDateWhen-equals-false",
    BuildAttributionUiEvent.OutgoingLinkTarget.UP_TO_DATE_EQUALS_FALSE_ISSUE_HELP
  ),
  OPTIMIZE_CONFIGURATION_PHASE(
    "https://d.android.com/r/tools/build-attribution/optimize-configuration-phase",
    BuildAttributionUiEvent.OutgoingLinkTarget.OPTIMIZE_CONFIGURATION_PHASE_HELP
  ),
  CONFIGURE_GC(
    "https://d.android.com/r/tools/build-attribution/configure-gc",
    BuildAttributionUiEvent.OutgoingLinkTarget.CONFIGURE_GC
  ),
  CONFIGURATION_CACHING(
    "https://d.android.com/r/tools/build-attribution/configuration-cache",
    BuildAttributionUiEvent.OutgoingLinkTarget.CONFIGURATION_CACHING
  ),
  JETIIFER_MIGRATE(
    "https://d.android.com/r/tools/build-attribution/migrate-to-androidx",
    BuildAttributionUiEvent.OutgoingLinkTarget.JETIFIER_MIGRATION
  ),
  DOWNLOADS(
    "https://d.android.com/r/tools/build-attribution/downloads",
    BuildAttributionUiEvent.OutgoingLinkTarget.DOWNLOADS_INFO
  ),
  RENDERSCRIPT_MIGRATE(
    // TODO(b/246718450): Create redirect link
    "https://developer.android.com/guide/topics/renderscript/migrate",
    BuildAttributionUiEvent.OutgoingLinkTarget.RENDERSCRIPT_MIGRATE
  ),
  AIDL_INFO(
    "https://developer.android.com/guide/components/aidl",
    BuildAttributionUiEvent.OutgoingLinkTarget.AIDL_INFO
  ),
  NON_TRANSITIVE_R_CLASS(
    "https://developer.android.com/studio/build/optimize-your-build#use-non-transitive-r-classes",
    BuildAttributionUiEvent.OutgoingLinkTarget.NON_TRANSITIVE_R_CLASS
  )
}
