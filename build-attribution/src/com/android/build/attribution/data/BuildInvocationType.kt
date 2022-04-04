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
package com.android.build.attribution.data

import com.google.wireless.android.sdk.stats.BuildAttributionStats

enum class BuildInvocationType(
  val metricsType: BuildAttributionStats.BuildType
){
  REGULAR_BUILD(BuildAttributionStats.BuildType.REGULAR_BUILD),
  CONFIGURATION_CACHE_TRIAL(BuildAttributionStats.BuildType.CONFIGURATION_CACHE_TRIAL_FLOW_BUILD),
  CHECK_JETIFIER(BuildAttributionStats.BuildType.CHECK_JETIFIER_BUILD)
}