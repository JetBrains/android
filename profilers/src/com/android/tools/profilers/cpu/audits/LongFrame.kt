/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu.audits

/**
 * When your app takes longer than 16ms to draw a frame, this means the 60fps mark we want to hit for
 * smooth user performance is missed. We call these long frames, and inspecting these frames' rendering
 * pipeline can help identify what caused it to take longer than 16ms.
 */
data class LongFrame(
  val startUs: Long,
  val endUs: Long
)