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
import com.google.api.client.auth.oauth2.TokenResponseException
import com.intellij.openapi.diagnostic.Logger
import java.io.IOException
import java.net.SocketException
import java.net.UnknownHostException
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** GRPC maximum retry times. */
private const val GRPC_MAX_RETRY_ATTEMPTS = 3

/** GRPC initial exponential backoff in milliseconds. */
private const val GRPC_INITIAL_BACKOFF_MILLIS: Long = 300

/** GRPC maximum exponential backoff in milliseconds. */
private const val GRPC_MAX_BACKOFF_MILLIS: Long = 10_000

/** GRPC exponential backoff multiplier. */
private const val GRPC_BACKOFF_MULTIPLIER: Float = 3.0F

/** GRPC timeout period. */
private const val GRPC_TIMEOUT_MILLIS = 60_000L

private fun log() = Logger.getInstance("GrpcUtils")

/**
 * Performs [rpcCall] with retry strategy if applicable or no retry if [maxRetries] < 1.
 * - The initial retry attempt will occur at random(0, [initialBackoff]).
 * - The nth attempt will occur at random(0, min([initialBackoff] *[backoffMultiplier]**(n-1),
 *   [maxBackoff])).
 *
 * This is targeting those transient faults, so please wrap this method around the lowest level of
 * the rpc call to ensure the accurate management of retrying here.
 */
suspend fun <T> retryRpc(
  maxRetries: Int = GRPC_MAX_RETRY_ATTEMPTS,
  initialBackoff: Long = GRPC_INITIAL_BACKOFF_MILLIS,
  maxBackoff: Long = GRPC_MAX_BACKOFF_MILLIS,
  backoffMultiplier: Float = GRPC_BACKOFF_MULTIPLIER,
  retryableStatusCodes: List<Status.Code> = listOf(Status.Code.UNAVAILABLE),
  timeout: Long = GRPC_TIMEOUT_MILLIS,
  rpcCall: suspend () -> T
) =
  withTimeout(timeout) {
    var currentDelay: Long = initialBackoff
    repeat(maxRetries) { count ->
      try {
        return@withTimeout rpcCall()
      } catch (exception: StatusRuntimeException) {
        if (!retryableStatusCodes.contains(exception.status.code)) throw exception
      }

      Random.nextLong(currentDelay)
        .also {
          log()
            .debug(
              "Retry attempt #${count + 1} for $rpcCall, retrying in ${it / 1000.0} second(s)..."
            )
        }
        .apply { delay(this) }

      currentDelay = (currentDelay * backoffMultiplier).toLong().coerceAtMost(maxBackoff)
    }

    rpcCall()
  }

private const val SUGGEST_FOR_UNAUTHORIZED = "Please log back in."

suspend fun <T> runGrpcCatching(
  notFoundFallbackValue: LoadingState.Done<T>,
  block: suspend () -> LoadingState.Done<T>
): LoadingState.Done<T> =
  withContext(Dispatchers.IO) {
    try {
      block()
    } catch (exception: StatusRuntimeException) {
      when (exception.status.code) {
        Status.Code.NOT_FOUND -> {
          // Requested entity was not found.
          notFoundFallbackValue
        }
        Status.Code.UNAUTHENTICATED -> {
          // This is not supposed to happen as the caller is (will be?) guarding against the remote
          // procedure call if
          // the authentication credentials are not valid -- we still try to handle such just in
          // case.
          LoadingState.Unauthorized("$SUGGEST_FOR_UNAUTHORIZED ${exception.message}", exception)
        }
        Status.Code.PERMISSION_DENIED -> {
          LoadingState.PermissionDenied(exception.message, exception)
        }
        Status.Code.UNKNOWN,
        Status.Code.DEADLINE_EXCEEDED,
        Status.Code.UNAVAILABLE,
        Status.Code.RESOURCE_EXHAUSTED -> {
          LoadingState.NetworkFailure(exception.message, exception)
        }
        else -> {
          log().warn("Got StatusRuntimeException: ${exception.message}")
          LoadingState.UnknownFailure(exception.message, exception)
        }
      }
    } catch (exception: IOException) {
      when (exception) {
        is TokenResponseException ->
          LoadingState.Unauthorized("$SUGGEST_FOR_UNAUTHORIZED ${exception.message}", exception)
        is UnknownHostException,
        is SocketException -> LoadingState.NetworkFailure(exception.message, exception)
        else -> LoadingState.UnknownFailure(exception.message, exception)
      }
    } catch (exception: TimeoutCancellationException) {
      LoadingState.NetworkFailure(exception.message, exception)
    }
  }
