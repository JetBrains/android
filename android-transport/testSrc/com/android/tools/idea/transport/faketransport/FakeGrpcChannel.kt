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
package com.android.tools.idea.transport.faketransport

import io.grpc.BindableService
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import org.junit.rules.ExternalResource
import java.util.UUID

/**
 * JUnit rule for creating a light, in-process GRPC client / server connection that is initialized
 * with fake services which provides it test data. This class handles starting up / shutting down
 * this connection automatically before / after each test.
 *
 * @param namePrefix A readable name which you can use to identify the server created by this class
 *   if something goes wrong. Often, this will be the name of your test class. To ensure the name
 *   will be unique across all tests, it will additionally be suffixed with a unique ID. Use
 *   [name] to get the full, unique name.
 */
open class FakeGrpcChannel(namePrefix: String, private vararg val services: BindableService) : ExternalResource() {
  // It can be problematic if GRPC channels with the same name are started at the same time
  // across tests boundaries. This can happen, for example, if multiple tests are run in parallel
  // with a copy/pasted name. By appending a UUID, we guarantee that the in-memory GRPC channel
  // being spun up for this test is guaranteed not to be shared with other tests by accident.
  val name: String = "${namePrefix}_${UUID.randomUUID()}"

  lateinit var server: Server
    private set

  lateinit var channel: ManagedChannel
    private set

  @Throws(Throwable::class)
  override fun before() {
    val serverBuilder = InProcessServerBuilder.forName(name)
    for (service in services) {
      serverBuilder.addService(service)
    }
    server = serverBuilder.build()
    server.start()
    channel = InProcessChannelBuilder.forName(name).usePlaintext().directExecutor().build()
  }

  override fun after() {
    server.shutdownNow()
    channel.shutdownNow()
    server.awaitTermination()
  }
}