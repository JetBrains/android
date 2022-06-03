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
package com.google.services.firebase.insights

import com.android.tools.idea.testing.NamedExternalResource
import com.google.services.firebase.insights.client.grpc.FakeCrashlyticsDatabase
import com.google.services.firebase.insights.client.grpc.FakeFirebaseCrashlyticsGrpcService
import io.grpc.Server
import io.grpc.ServerBuilder
import org.junit.runner.Description
import java.util.concurrent.TimeUnit

class AppInsightsGrpcServerRule(private val timeoutMs: Long = 5000) : NamedExternalResource() {
  lateinit var database: FakeCrashlyticsDatabase
  lateinit var server: Server

  override fun before(description: Description) {
    database = FakeCrashlyticsDatabase()
    server = ServerBuilder.forPort(0)
      .addService(FakeFirebaseCrashlyticsGrpcService(database))
      .directExecutor()
      .build()
      .start()
  }

  override fun after(description: Description) {
    database.cleanUp()
    server.shutdown()
    try {
      if (!server.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
        server.shutdownNow()
      }
    }
    catch (e: InterruptedException) {
      server.shutdownNow()
    }
  }
}