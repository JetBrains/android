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

/** Feedback for AI-generated insights. */
enum class InsightFeedback {
  THUMBS_UP,
  THUMBS_DOWN,
}

/** A simple wrapper for AI-generated insights. */
data class AiInsight(
  val rawInsight: String,
  val feedback: InsightFeedback? = null,
  // Retrieve the active model name officially from GeminiPluginApi once it is exposed,
  val modelName: String = "AI Assistant",
)
