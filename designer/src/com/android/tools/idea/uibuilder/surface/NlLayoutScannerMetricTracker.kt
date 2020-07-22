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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.validator.ValidatorResult

/**
 * Metric tracker for results from accessibility testing framework
 */
class NlLayoutScannerMetricTracker {

  fun trackIssueExpanded(issue: Issue?, expanded: Boolean) {
    // TODO: Once .proto file is updated, add the metrics here..
  }

  fun trackResult(result: ValidatorResult) {
    /*
     * TODO: Add tracking in efficient way. b/158119426
     * - (if not too expensive) elapsed time result.metric.mElapsedMs
     * - (if not too expensive) memory usage result.metric.mImageMemoryBytes
     * - Number of times 1+ atf findings
     * - (if possible) Number of occurrences of each type of check result,
     *      (e.g. N issues by TouchTargetSizeCheck, N1 errors, N2 warnings)
     */

  }
}