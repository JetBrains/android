/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.insights

import com.android.tools.idea.insights.client.runGrpcCatching
import com.google.api.client.auth.oauth2.TokenResponseException
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import com.google.common.truth.Truth
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.io.IOException
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class RunGrpcCatchingTest {

  private val logs = mutableListOf<LogRecord>()

  @Before
  fun setup() {
    val logger = Logger.getLogger("GrpcUtils")
    logger.addHandler(
      object : Handler() {
        override fun publish(record: LogRecord?) {
          record?.let { logs.add(it) }
        }

        override fun flush() = Unit

        override fun close() = Unit
      }
    )
  }

  @Test
  fun `runGrpcCatching when block returns ready should return its result`() = runBlocking {
    val ready = LoadingState.Ready("hello")
    val result = runGrpcCatching(LoadingState.Unauthorized("unauthorized")) { ready }
    Truth.assertThat(result).isEqualTo(ready)
  }

  @Test
  fun `runGrpcCatching when block returns failure return its result`() = runBlocking {
    val failure = LoadingState.UnknownFailure("msg")
    val result = runGrpcCatching(LoadingState.Unauthorized("unauthorized")) { failure }
    Truth.assertThat(result).isEqualTo(failure)
  }

  @Test
  fun `runGrpcCatching when block throws NOT_FOUND should return fallbackValue`() = runBlocking {
    val ready = LoadingState.Ready("hello")
    val result = runGrpcCatching(ready) { throw StatusRuntimeException(Status.NOT_FOUND) }
    Truth.assertThat(result).isEqualTo(ready)
  }

  @Test
  fun `runGrpcCatching when block throws UNAUTHENTICATED should return Unauthorized`() =
    runBlocking {
      val ready = LoadingState.Ready("hello")
      val result = runGrpcCatching(ready) { throw StatusRuntimeException(Status.UNAUTHENTICATED) }
      Truth.assertThat(result).isInstanceOf(LoadingState.Unauthorized::class.java)
    }

  @Test
  fun `runGrpcCatching when block throws other grpc exception should return UnknownFailure`() =
    runBlocking {
      val ready = LoadingState.Ready("hello")
      val result = runGrpcCatching(ready) { throw StatusRuntimeException(Status.INTERNAL) }
      Truth.assertThat(result).isInstanceOf(LoadingState.UnknownFailure::class.java)
    }

  @Test
  fun `runGrpcCatching when block throws TokenResponseException should return Unauthorized`() =
    runBlocking {
      val ready = LoadingState.Ready("hello")
      val result = runGrpcCatching(ready) { throw Mockito.mock<TokenResponseException>() }
      Truth.assertThat(result).isInstanceOf(LoadingState.Unauthorized::class.java)
    }

  @Test
  fun `runGrpcCatching when block throws GoogleJsonResponseException should return ServerFailure`() =
    runBlocking {
      val ready = LoadingState.Ready("hello")
      val httpResponseExceptionBuilder =
        HttpResponseException.Builder(400, "Bad Request", HttpHeaders())
      val jsonError =
        GoogleJsonError().apply {
          set("status", "not found")
          message = "resource is not found"
        }
      val exception = GoogleJsonResponseException(httpResponseExceptionBuilder, jsonError)
      val result = runGrpcCatching(ready) { throw exception }
      Truth.assertThat(result)
        .isEqualTo(LoadingState.ServerFailure("not found: resource is not found", exception))
    }

  @Test
  fun `runGrpcCatching when block throws IOException should return UnknownFailure`() = runBlocking {
    val ready = LoadingState.Ready("hello")
    val result = runGrpcCatching(ready) { throw IOException() }
    Truth.assertThat(result).isInstanceOf(LoadingState.UnknownFailure::class.java)
  }

  @Test
  fun `runGrpcCatching logs failing grpc`() = runBlocking {
    val ready = LoadingState.Ready("hello")
    // Call using helper method since the stack will otherwise contain "invokeSuspend" from the
    // CoroutineScope of runBlocking
    callGrpcHelper(ready)
    Truth.assertThat(logs.size).isEqualTo(1)
    val log = logs.first()
    Truth.assertThat(log.level).isEqualTo(Level.WARNING)
    Truth.assertThat(log.message).isEqualTo("callGrpcHelper - Got exception: null")
  }

  private suspend fun callGrpcHelper(ready: LoadingState.Done<String>) =
    runGrpcCatching(ready) { throw RuntimeException() }
}
