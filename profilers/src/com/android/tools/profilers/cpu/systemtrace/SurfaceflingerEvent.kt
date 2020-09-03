/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.cpu.systemtrace

/**
 * Represents a trace event in the Surfaceflinger process.
 */
data class SurfaceflingerEvent(val start: Long,
                               val end: Long,
                               val type: Type,
                               val name: String = "") {
  enum class Type(val displayName: String) {
    /**
     * Represents the state when there are no trace events in the Surfaceflinger process.
     */
    IDLE("Idle"),

    /**
     * Represents the state when there are any trace event in the Surfaceflinger process.
     */
    PROCESSING("Processing");
  }
}