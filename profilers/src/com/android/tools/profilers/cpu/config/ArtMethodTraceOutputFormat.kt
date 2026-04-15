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

package com.android.tools.profilers.cpu.config

import com.android.sdklib.AndroidVersion
import com.android.tools.profiler.proto.Common

private const val ART_V2_MIN_VERSION_CODE = 373399999L

/**
 * Returns the output format version for ART method traces for a given device. This corresponds to the --profiler-output-version argument in
 * the 'am' command.
 *
 * We only use version 2 if the PROFILER_METHOD_TRACE_IN_EDITOR flag is enabled, as the legacy trace-viewer in Profiler window cannot parse
 * the new V2 format.
 */
fun getArtMethodTraceOutputVersion(device: Common.Device, isMethodTraceInEditorEnabled: Boolean): Int {
  return when {
    device.featureLevel < AndroidVersion.VersionCodes.CINNAMON_BUN -> 1
    !isMethodTraceInEditorEnabled -> 1
    device.artVersionCode >= ART_V2_MIN_VERSION_CODE -> 2
    else -> 1
  }
}
