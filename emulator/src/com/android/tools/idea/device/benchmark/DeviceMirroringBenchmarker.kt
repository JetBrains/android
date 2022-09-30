/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.benchmark

import com.android.tools.idea.emulator.AbstractDisplayView
import java.awt.Point
import java.util.Timer
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

data class DeviceMirroringBenchmarkTarget(val name: String, val serialNumber: String, val view: AbstractDisplayView)

/** A class to conduct benchmarking of device mirroring. */
@OptIn(ExperimentalTime::class)
class DeviceMirroringBenchmarker(
  target: DeviceMirroringBenchmarkTarget,
  bitsPerChannel: Int = 0,
  latencyBits: Int = 6,
  touchRateHz: Int = 60,
  maxTouches: Int = 10_000,
  step: Int = 1,
  spikiness: Int = 1,
  timeSource: TimeSource = TimeSource.Monotonic,
  timer: Timer = Timer(),
) : Benchmarker<Point>(
  DeviceAdapter(target, timeSource, bitsPerChannel, latencyBits, maxTouches, step, spikiness),
  touchRateHz,
  maxTouches,
  timeSource,
  timer)
