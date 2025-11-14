/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights

import com.android.tools.idea.insights.model.issue.VisibilityType
import com.android.tools.idea.insights.model.issue.VisibilityType.ALL
import com.android.tools.idea.insights.model.issue.VisibilityType.USER_PERCEIVED
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.VisibilityFilter

fun VisibilityType.toLogProto() =
  when (this) {
    ALL -> VisibilityFilter.ALL_VISIBILITY
    USER_PERCEIVED -> VisibilityFilter.USER_PERCEIVED
  }
