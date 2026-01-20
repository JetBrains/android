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
package com.android.tools.idea.insights.client

import com.android.tools.idea.insights.ai.codecontext.ContextSharingState
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine

fun <K : Any, V> createNew(maximumSize: Long): Cache<K, V> {
  // TODO: consider adding back weak keys support, which does not
  // work with kotlin String keys.
  return Caffeine.newBuilder().maximumSize(maximumSize).build()
}

data class AiInsightKey(val variantId: String?, val contextSharingState: ContextSharingState)
