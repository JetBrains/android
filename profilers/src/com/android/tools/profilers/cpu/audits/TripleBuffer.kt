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
 * When your app is pushing frames to the Android system faster than it can draw them to the surface,
 * what your user sees will be one  frame behind. Since user input is processed in the frames sent by
 * your app to the Android system, lagging behind means your app will take longer to process user input.
 */
data class TripleBuffer(
  val startUs: Long,
  val endUs: Long)