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
package com.android.tools.profilers.leakcanary

/** A generic container of values that may not yet be ready with a possibility of failure. */
sealed class LoadingState<out T> {

  /** The value is being loaded and is not yet available. */
  data class Loading(val message: String = "") : LoadingState<Nothing>()

  /** The value is ready to be used. */
  data class Ready<out T>(val value: T) : LoadingState<T>()

  /** Loading the value failed. */
  data class Failure(val message: String) : LoadingState<Nothing>()
}
