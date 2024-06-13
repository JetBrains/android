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
import com.android.tools.idea.io.grpc.Server
import com.android.tools.idea.io.grpc.ServerBuilder
import com.android.tools.idea.testing.NamedExternalResource
import com.android.tools.idea.vitals.datamodel.VitalsConnection
import java.util.concurrent.TimeUnit
import org.junit.runner.Description

class VitalsGrpcServerRule(
  private val connection: VitalsConnection,
  private val timeoutMs: Long = 5000,
) : NamedExternalResource() {
  lateinit var server: Server
  val database = FakeVitalsDatabase(connection)
  val clock = FakeClock()

  override fun before(description: Description) {
    server =
      ServerBuilder.forPort(0)
        .addService(FakeErrorsService(connection, database, clock))
        .addService(FakeReportingService(connection))
        .directExecutor()
        .build()
        .start()
  }

  override fun after(description: Description) {
    database.clear()
    server.shutdown()
    try {
      if (!server.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
        server.shutdownNow()
      }
    } catch (e: InterruptedException) {
      server.shutdownNow()
    }
  }
}
