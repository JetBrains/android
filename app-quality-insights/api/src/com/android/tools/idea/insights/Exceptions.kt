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
package com.android.tools.idea.insights

object UnconfiguredAppException : Exception()

object NoTypesSelectedException : Exception()

object NoVersionsSelectedException : Exception()

object NoDevicesSelectedException : Exception()

object NoOperatingSystemsSelectedException : Exception()

open class TimeoutException : Exception()

object CancellableTimeoutException : TimeoutException()

// TODO: this is used for Crashlytics only. To be removed when the offline flag is removed.
data class RevertibleException(
  val snapshot: AppInsightsState? = null,
  override val cause: Throwable? = null
) : Exception()

fun <T> LoadingState<T>.isCancellableTimeoutException(): Boolean {
  return this is LoadingState.UnknownFailure &&
    cause is RevertibleException &&
    cause.cause is CancellableTimeoutException
}
