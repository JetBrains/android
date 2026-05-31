/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.logging

import com.intellij.openapi.project.Project

/** Data class for AI event stats. */
data class ModelStats(val project: Project?, val modelName: String?, val usage: ModelUsageStats?) : LoggedEvent

data class ModelUsageStats(
  val inputTokens: Long,
  val outputTokens: Long,
  val thinkingOutputTokens: Long,
  val totalTokens: Long,
  val cacheReadTokens: Long,
)
