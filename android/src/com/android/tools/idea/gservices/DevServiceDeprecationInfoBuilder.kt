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
package com.android.tools.idea.gservices

import com.android.tools.idea.gservices.DevServicesDeprecationStatus.DEPRECATED
import com.android.tools.idea.gservices.DevServicesDeprecationStatus.UNSUPPORTED
import com.google.wireless.android.sdk.stats.DevServiceDeprecationInfo
import com.google.wireless.android.sdk.stats.DevServiceDeprecationInfo.DeliveryType
import com.google.wireless.android.sdk.stats.DevServiceDeprecationInfo.DeprecationStatus

@Suppress("FunctionName")
fun DevServiceDeprecationInfoBuilder(
  deprecationStatus: DevServicesDeprecationStatus,
  deliveryType: DeliveryType,
  userNotified: Boolean? = null,
  moreInfoClicked: Boolean? = null,
  updateClicked: Boolean? = null,
  deliveryDismissed: Boolean? = null,
): DevServiceDeprecationInfo =
  DevServiceDeprecationInfo.newBuilder()
    .apply {
      this.deprecationStatus =
        when (deprecationStatus) {
          DEPRECATED -> DeprecationStatus.DEPRECATED
          UNSUPPORTED -> DeprecationStatus.UNSUPPORTED
          else -> throw IllegalArgumentException("SUPPORTED state should not log event")
        }
      this.deliveryType = deliveryType
      userNotified?.let { this.userNotified = it }
      moreInfoClicked?.let { this.moreInfoClicked = it }
      updateClicked?.let { this.updateClicked = it }
      deliveryDismissed?.let { this.deliveryDismissed = it }
    }
    .build()
