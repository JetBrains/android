/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.report

import com.google.wireless.android.sdk.stats.HeapReportEvent

enum class MemoryReportReason {
  None,
  UserInvoked,
  InternalUserInvoked,
  FrequentLowMemoryNotification,
  LowMemory,
  OutOfMemory;

  fun isUserInvoked(): Boolean {
    return this == UserInvoked || this == InternalUserInvoked
  }

  fun asHeapReportEventReason() = when (this) {
      None -> HeapReportEvent.Reason.NONE
      UserInvoked -> HeapReportEvent.Reason.USER_INVOKED
      InternalUserInvoked -> HeapReportEvent.Reason.INTERNAL_USER_INVOKED
      FrequentLowMemoryNotification -> HeapReportEvent.Reason.FREQUENT_LOW_MEMORY_NOTIFICATION
      LowMemory -> HeapReportEvent.Reason.LOW_MEMORY
      OutOfMemory -> HeapReportEvent.Reason.OUT_OF_MEMORY
  }
}
