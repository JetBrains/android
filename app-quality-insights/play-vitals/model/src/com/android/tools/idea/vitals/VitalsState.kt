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
package com.android.tools.idea.vitals

import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.Filters
import com.android.tools.idea.insights.MultiSelection
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.SignalType
import com.android.tools.idea.insights.TimeIntervalFilter
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.WithCount
import com.android.tools.idea.insights.selectionOf

internal val VitalsTimeIntervals =
  listOf(
    TimeIntervalFilter.ONE_DAY,
    TimeIntervalFilter.SEVEN_DAYS,
    TimeIntervalFilter.FOURTEEN_DAYS,
    TimeIntervalFilter.TWENTY_EIGHT_DAYS,
    TimeIntervalFilter.SIXTY_DAYS
  )

internal val VitalsFailureTypes =
  listOf(FailureType.USER_PERCEIVED_ONLY, FailureType.BACKGROUND, FailureType.FOREGROUND)

fun createVitalsFilters(
  /** Selection of [Version]s. */
  versions: MultiSelection<WithCount<Version>> = MultiSelection.emptySelection(),
  /** Selection of [TimeIntervalFilter]s. */
  timeInterval: Selection<TimeIntervalFilter> =
    Selection(TimeIntervalFilter.TWENTY_EIGHT_DAYS, VitalsTimeIntervals),
  failureTypeToggles: MultiSelection<FailureType> =
    MultiSelection(VitalsFailureTypes.toSet(), VitalsFailureTypes),
  devices: MultiSelection<WithCount<Device>> = MultiSelection.emptySelection(),
  operatingSystems: MultiSelection<WithCount<OperatingSystemInfo>> =
    MultiSelection.emptySelection(),
  signal: Selection<SignalType> = selectionOf(SignalType.SIGNAL_UNSPECIFIED)
) = Filters(versions, timeInterval, failureTypeToggles, devices, operatingSystems, signal)
