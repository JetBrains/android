/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.analytics.energy

import com.android.tools.profilers.energy.EnergyDuration

/**
 * Class with metadata related to a range of energy data.
 */
class EnergyRangeMetadata(energyDurations: List<EnergyDuration>) {
  data class EnergyEventCount(val kind: EnergyDuration.Kind, val count: Int)

  /**
   * Get a tally of each kind of EnergyDuration. Each EnergyDuration.Kind will only appear once.
   */
  val eventCounts = energyDurations.groupBy { it.kind }.map { EnergyEventCount(it.key, it.value.size) }
}
