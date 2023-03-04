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
package com.android.gmdcodecompletion

// FTL device catalog should be updated every 7 days
const val FTL_DEVICE_CATALOG_UPDATE_FREQUENCY: Int = 7

// Managed virtual device catalog should be updated every 7 days
const val MANAGED_VIRTUAL_DEVICE_CATALOG_UPDATE_FREQUENCY: Int = 7

// Stores device related information to better sort code completion suggestion list
data class AndroidDeviceInfo(
  val deviceName: String = "",
  val supportedApis: List<Int> = emptyList(),
  // Use supportedApiRange for managed virtual device since max API support can be INT_MAX
  val supportedApiRange: IntRange = IntRange.EMPTY,
  val brand: String = "",
  val formFactor: String = "",
  val deviceForm: String = "",
)