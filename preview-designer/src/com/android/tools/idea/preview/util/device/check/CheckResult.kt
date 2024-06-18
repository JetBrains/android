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
package com.android.tools.idea.preview.util.device.check

/**
 * Contains any Issues found by the check, if the issues can be resolved, [proposedFix] will be a
 * not-null string that can be applied to resolve the issues.
 *
 * So when [issues] is empty, the check completed successfully and [proposedFix] should be null.
 */
internal data class CheckResult(val issues: List<IssueReason>, val proposedFix: String?) {
  val hasIssues: Boolean = issues.isNotEmpty()
}
