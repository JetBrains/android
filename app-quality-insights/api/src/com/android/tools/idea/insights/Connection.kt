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
package com.android.tools.idea.insights

import com.intellij.openapi.module.Module

data class VariantData(val module: Module, val variantName: String)

/**
 * A single connection represented by credentials required to access resources for a given project.
 */
interface Connection {
  val mobileSdkAppId: String?
  val projectId: String?
  val projectNumber: String?
  val appId: String

  val clientId: String
  val isConfigured: Boolean

  fun isPreferredConnection(): Boolean

  fun isMatchingProject(): Boolean
}

fun List<Connection>.noneIsConfigured(): Boolean = none { it.isConfigured }

fun List<Connection>.anyIsConfigured(): Boolean = any { it.isConfigured }
