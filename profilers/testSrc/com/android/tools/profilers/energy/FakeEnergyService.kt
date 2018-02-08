// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.profilers.energy

import com.android.tools.profiler.proto.EnergyProfiler
import com.android.tools.profiler.proto.EnergyProfiler.*
import com.android.tools.profiler.proto.EnergyServiceGrpc
import io.grpc.stub.StreamObserver
import java.util.*
import java.util.stream.Collectors

class FakeEnergyService(val dataList: List<EnergySample> = ArrayList()) : EnergyServiceGrpc.EnergyServiceImplBase() {

  private var myEvents: List<EnergyProfiler.EnergyEvent> = ArrayList()

  override fun getData(request: EnergyRequest, responseObserver: StreamObserver<EnergyDataResponse>) {
    val listStream = dataList.stream().filter({d -> d.timestamp >= request.startTimestamp && d.timestamp < request.endTimestamp })
    val resultList = listStream.collect(Collectors.toList())
    val response = EnergyDataResponse.newBuilder().addAllSampleData(resultList).build()
    responseObserver.onNext(response)
    responseObserver.onCompleted()
  }

  override fun getEvents(request: EnergyRequest, responseObserver: StreamObserver<EnergyProfiler.EnergyEventsResponse>) {
    val response = EnergyProfiler.EnergyEventsResponse.newBuilder().addAllEvent(myEvents).build()
    responseObserver.onNext(response)
    responseObserver.onCompleted()
  }

  fun setEvents(events: List<EnergyProfiler.EnergyEvent>) {
    myEvents = events
  }
}
