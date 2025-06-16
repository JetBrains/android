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
package com.android.tools.idea.wear.dwf.analytics

import com.android.tools.analytics.UsageTracker
import com.android.tools.wear.wff.WFFVersion
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind
import com.google.wireless.android.sdk.stats.DeclarativeWatchFaceEvent
import com.google.wireless.android.sdk.stats.DeclarativeWatchFaceEvent.Type.XML_SCHEMA_USED
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

/** Class that tracks usage related to Declarative Watch Faces. */
@Service
class DeclarativeWatchFaceUsageTracker() {
  fun trackXmlSchemaUsed(wffVersion: WFFVersion, isFallback: Boolean) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(EventKind.DECLARATIVE_WATCH_FACE_EVENT)
        .setDeclarativeWatchFaceEvent(
          DeclarativeWatchFaceEvent.newBuilder()
            .setType(XML_SCHEMA_USED)
            .setWffVersion(
              DeclarativeWatchFaceEvent.WFFVersion.newBuilder()
                .setVersion(wffVersion.version)
                .setIsFallback(isFallback)
            )
        )
    )
  }

  companion object {
    fun getInstance(): DeclarativeWatchFaceUsageTracker = service()
  }
}
