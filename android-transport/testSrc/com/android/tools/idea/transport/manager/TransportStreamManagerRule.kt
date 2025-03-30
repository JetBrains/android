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
package com.android.tools.idea.transport.manager

import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.rules.ExternalResource
import kotlin.coroutines.EmptyCoroutineContext

class TransportStreamManagerRule(private val fakeGrpcServer: FakeGrpcServer) : ExternalResource() {
  lateinit var streamManager: TransportStreamManager
  private lateinit var scope: CoroutineScope
  private lateinit var client: TransportClient

  override fun before() {
    scope = CoroutineScope(EmptyCoroutineContext)
    client = TransportClient(fakeGrpcServer.name)
    streamManager =
      TransportStreamManager(client.transportStub, scope)
  }

  override fun after() {
    client.shutdown()
    scope.cancel()
  }
}
