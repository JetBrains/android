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

import com.android.testutils.time.FakeClock
import com.android.tools.idea.testing.NamedExternalResource
import com.android.tools.idea.vitals.datamodel.VitalsConnection
import com.google.protobuf.GeneratedMessageV3
import com.studiogrpc.testutils.GrpcConnectionRule
import io.grpc.ManagedChannel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import org.junit.runner.Description

class VitalsGrpcConnectionRule(connection: VitalsConnection) : NamedExternalResource() {
  private val channel = Channel<GeneratedMessageV3>(capacity = 10)

  val database = FakeVitalsDatabase(connection)

  val clock = FakeClock()

  private val errorsService = FakeErrorsService(connection, database, clock, channel)
  private val reportingService = FakeReportingService(connection, channel)

  private val grpcConnectionRule = GrpcConnectionRule(listOf(errorsService, reportingService))

  val grpcChannel: ManagedChannel
    get() = grpcConnectionRule.channel

  override fun before(description: Description) {
    grpcConnectionRule.before(description)
  }

  override fun after(description: Description) {
    grpcConnectionRule.after(description)
  }

  suspend fun collectEvents(): List<GeneratedMessageV3> =
    flow {
        while (true) {
          withTimeout(200) { emit(channel.receive()) }
        }
      }
      .catch { e -> if (e !is TimeoutCancellationException) throw e }
      .toList(mutableListOf())
}
