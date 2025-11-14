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
package com.android.tools.idea.insights.client

import com.android.tools.idea.insights.model.common.Interval
import com.android.tools.idea.insights.model.event.Device
import com.android.tools.idea.insights.model.event.OperatingSystemInfo
import com.android.tools.idea.insights.model.event.Version
import com.android.tools.idea.insights.model.issue.FailureType
import com.android.tools.idea.insights.model.issue.SignalType
import com.android.tools.idea.insights.model.issue.VisibilityType

/** Filtering options for queries. */
data class QueryFilters(
  val interval: Interval,
  val versions: Set<Version> = setOf(Version.ALL),
  val devices: Set<Device> = setOf(Device.ALL),
  val operatingSystems: Set<OperatingSystemInfo> = setOf(OperatingSystemInfo.ALL),
  val eventTypes: List<FailureType> = emptyList(),
  val signal: SignalType = SignalType.SIGNAL_UNSPECIFIED,
  val visibilityType: VisibilityType = VisibilityType.ALL,
)
