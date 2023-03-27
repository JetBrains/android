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

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.io.grpc.Status
import com.android.tools.idea.io.grpc.StatusRuntimeException
import com.google.api.client.auth.oauth2.TokenResponseException
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Test

class RunGrpcCatchingTest {
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
  fun `runGrpcCatching when block throws IOException should return UnknownFailure`() = runBlocking {
    val ready = LoadingState.Ready("hello")
    val result = runGrpcCatching(ready) { throw IOException() }
    assertThat(result).isInstanceOf(LoadingState.UnknownFailure::class.java)
  }
}
