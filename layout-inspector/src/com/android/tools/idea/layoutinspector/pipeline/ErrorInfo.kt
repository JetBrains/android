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
package com.android.tools.idea.layoutinspector.pipeline

import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode

/**
 * An error description with a error [code] and optional [args] for generating a message.
 */
data class ErrorInfo(
  // The analytics error code
  val code: AttachErrorCode,

  // Arguments to use in creating a string representation of the error
  val args: Map<String, String>
)

// Convenience methods for creating an ErrorInfo
fun AttachErrorCode.info(): ErrorInfo = ErrorInfo(this, emptyMap())
fun AttachErrorCode.info(vararg params: Pair<String, String>): ErrorInfo = ErrorInfo(this, params.associate { it })
