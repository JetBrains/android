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
package com.android.tools.idea.insights.client

import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.io.grpc.Status
import com.android.tools.idea.io.grpc.StatusRuntimeException
import com.android.tools.idea.testing.DebugLoggerRule
import com.google.api.client.auth.oauth2.TokenResponseException
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.Logger.Factory
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

class RunGrpcCatchingTest {

  @get:Rule val logRule = DebugLoggerRule(FakeFactory::class.java)

  private val logger: FakeLogger
    get() = FakeFactory.LOGGER

  @Before
  fun setup() {
    FakeFactory.LOGGER.clear()
  }

  @Test
  fun `runGrpcCatching when block returns ready should return its result`() = runBlocking {
    val ready = LoadingState.Ready("hello")
    val result = runGrpcCatching(LoadingState.Unauthorized("unauthorized")) { ready }
    assertThat(result).isEqualTo(ready)
  }

  @Test
  fun `runGrpcCatching when block returns failure return its result`() = runBlocking {
    val failure = LoadingState.UnknownFailure("msg")
    val result = runGrpcCatching(LoadingState.Unauthorized("unauthorized")) { failure }
    assertThat(result).isEqualTo(failure)
  }

  @Test
  fun `runGrpcCatching when block throws NOT_FOUND should return fallbackValue`() = runBlocking {
    val ready = LoadingState.Ready("hello")
    val result = runGrpcCatching(ready) { throw StatusRuntimeException(Status.NOT_FOUND) }
    assertThat(result).isEqualTo(ready)
  }

  @Test
  fun `runGrpcCatching when block throws UNAUTHENTICATED should return Unauthorized`() =
    runBlocking {
      val ready = LoadingState.Ready("hello")
      val result = runGrpcCatching(ready) { throw StatusRuntimeException(Status.UNAUTHENTICATED) }
      assertThat(result).isInstanceOf(LoadingState.Unauthorized::class.java)
    }

  @Test
  fun `runGrpcCatching when block throws other grpc exception should return UnknownFailure`() =
    runBlocking {
      val ready = LoadingState.Ready("hello")
      val result = runGrpcCatching(ready) { throw StatusRuntimeException(Status.INTERNAL) }
      assertThat(result).isInstanceOf(LoadingState.UnknownFailure::class.java)
    }

  @Test
  fun `runGrpcCatching when block throws TokenResponseException should return Unauthorized`() =
    runBlocking {
      val ready = LoadingState.Ready("hello")
      val result = runGrpcCatching(ready) { throw mock<TokenResponseException>() }
      assertThat(result).isInstanceOf(LoadingState.Unauthorized::class.java)
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
      assertThat(result)
        .isEqualTo(LoadingState.ServerFailure("not found: resource is not found", exception))
    }

  @Test
  fun `runGrpcCatching when block throws IOException should return UnknownFailure`() = runBlocking {
    val ready = LoadingState.Ready("hello")
    val result = runGrpcCatching(ready) { throw IOException() }
    assertThat(result).isInstanceOf(LoadingState.UnknownFailure::class.java)
  }

  @Test
  fun `runGrpcCatching logs failing grpc`() = runBlocking {
    val ready = LoadingState.Ready("hello")
    // Call using helper method since the stack will otherwise contain "invokeSuspend" from the
    // CoroutineScope of runBlocking
    callGrpcHelper(ready)
    assertThat(logger.logMessage.size).isEqualTo(1)
    val log = logger.logMessage.first()
    assertThat(log.level).isEqualTo(LogLevel.WARNING)
    assertThat(log.message).isEqualTo("callGrpcHelper - Got exception: null")
    assertThat(log.throwable).isNull()
  }

  private suspend fun callGrpcHelper(ready: LoadingState.Done<String>) =
    runGrpcCatching(ready) { throw RuntimeException() }

  private class FakeFactory : Factory {
    companion object {
      @JvmStatic internal val LOGGER = FakeLogger()
    }

    override fun getLoggerInstance(category: String) = LOGGER
  }

  private class FakeLogger : Logger() {
    private val _logMessages = mutableListOf<LogMessage>()
    val logMessage: List<LogMessage>
      get() = synchronized(this) { _logMessages }

    fun clear() = synchronized(this) { _logMessages.clear() }

    override fun isDebugEnabled() = false

    override fun debug(message: String?, t: Throwable?) {
      synchronized(this) { _logMessages.add(LogMessage(LogLevel.DEBUG, message, t)) }
    }

    override fun info(message: String?, t: Throwable?) {
      synchronized(this) { _logMessages.add(LogMessage(LogLevel.INFO, message, t)) }
    }

    override fun warn(message: String?, t: Throwable?) {
      synchronized(this) { _logMessages.add(LogMessage(LogLevel.WARNING, message, t)) }
    }

    override fun error(message: String?, t: Throwable?, vararg details: String?) {
      synchronized(this) { _logMessages.add(LogMessage(LogLevel.ERROR, message, t)) }
    }

    data class LogMessage(
      val level: LogLevel = LogLevel.INFO,
      val message: String? = null,
      val throwable: Throwable? = null,
    )
  }
}
