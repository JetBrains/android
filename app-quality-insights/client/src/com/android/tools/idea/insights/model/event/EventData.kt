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
package com.android.tools.idea.insights.model.event

import java.time.Instant

/** Event metadata captured at the time of the event, plus additional analysis. */
data class EventData(
  // Metadata about the device.
  val device: Device = Device("", "", ""),

  // Metadata about operating system.
  val operatingSystemInfo: OperatingSystemInfo = OperatingSystemInfo("", ""),

  // Time of the event occurrence
  val eventTime: Instant = Instant.EPOCH,
)
