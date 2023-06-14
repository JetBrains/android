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
package com.android.tools.idea.layoutinspector.common

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.diagnostic.Logger

private const val DIAGNOSTICS_PREFIX = "LI-DIAG: "

/** Optional logging for determining problems in released versions. */
fun logDiagnostics(where: Class<*>, message: String, vararg arguments: Any?) =
  logDiagnostics(where, null, message, *arguments)

/** Optional logging for determining problems in released versions with lazy generated arguments. */
fun logDiagnostics(where: Class<*>, message: String, lazyArguments: () -> Array<Any?>) {
  if (StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_EXTRA_LOGGING.get()) {
    logDiagnostics(where, null, message, *lazyArguments())
  }
}

/** Optional logging for determining problems in released versions with an optional exception. */
fun logDiagnostics(where: Class<*>, t: Throwable?, message: String, vararg arguments: Any?) {
  if (StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_EXTRA_LOGGING.get()) {
    Logger.getInstance(where).warn(String.format(DIAGNOSTICS_PREFIX + message, *arguments), t)
  }
}
