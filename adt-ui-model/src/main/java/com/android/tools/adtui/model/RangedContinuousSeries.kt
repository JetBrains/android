/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.adtui.model

/**
 * This class adds a name and an additional range to RangedSeries. This additional range represents
 * the Y axis to the default range which represents the x axis.
 */
class RangedContinuousSeries @JvmOverloads constructor(val name: String,
                                                       xRange: Range,
                                                       val yRange: Range,
                                                       series: DataSeries<Long>,
                                                       intersectRange: Range = Range(-Double.MAX_VALUE, Double.MAX_VALUE))
  : RangedSeries<Long>(xRange, series, intersectRange)