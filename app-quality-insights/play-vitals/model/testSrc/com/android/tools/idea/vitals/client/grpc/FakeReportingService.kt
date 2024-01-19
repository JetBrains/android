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
package com.android.tools.idea.vitals.client.grpc

import com.android.tools.idea.io.grpc.stub.StreamObserver
import com.android.tools.idea.protobuf.GeneratedMessageV3
import com.android.tools.idea.vitals.datamodel.VitalsConnection
import com.google.play.developer.reporting.App
import com.google.play.developer.reporting.FetchReleaseFilterOptionsRequest
import com.google.play.developer.reporting.Release
import com.google.play.developer.reporting.ReleaseFilterOptions
import com.google.play.developer.reporting.ReportingServiceGrpc.ReportingServiceImplBase
import com.google.play.developer.reporting.SearchAccessibleAppsRequest
import com.google.play.developer.reporting.SearchAccessibleAppsResponse
import com.google.play.developer.reporting.Track
import kotlinx.coroutines.channels.SendChannel

class FakeReportingService(
  private val connection: VitalsConnection,
  private val requestChannel: SendChannel<GeneratedMessageV3>? = null,
) : ReportingServiceImplBase() {
  override fun fetchReleaseFilterOptions(
    request: FetchReleaseFilterOptionsRequest,
    responseObserver: StreamObserver<ReleaseFilterOptions>,
  ) {
    requestChannel?.trySend(request)
    responseObserver.onNext(
      ReleaseFilterOptions.newBuilder()
        .apply {
          addTracks(
            Track.newBuilder()
              .apply {
                displayName = "testing"
                type = "Open testing"
                addServingReleases(
                  Release.newBuilder()
                    .apply {
                      displayName = "first"
                      addVersionCodes(5)
                    }
                    .build()
                )
              }
              .build()
          )
        }
        .build()
    )
    responseObserver.onCompleted()
  }

  override fun searchAccessibleApps(
    request: SearchAccessibleAppsRequest,
    responseObserver: StreamObserver<SearchAccessibleAppsResponse>,
  ) {
    requestChannel?.trySend(request)
    responseObserver.onNext(
      SearchAccessibleAppsResponse.newBuilder()
        .apply {
          addApps(
            App.newBuilder()
              .apply {
                name = "apps/${connection.appId}"
                packageName = connection.appId
                displayName = connection.displayName
              }
              .build()
          )
        }
        .build()
    )
    responseObserver.onCompleted()
  }
}
