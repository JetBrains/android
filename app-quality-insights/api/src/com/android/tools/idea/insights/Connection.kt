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

/** Variant-aware firebase connection. */
data class VariantConnection(
  val module: Module,
  val variantName: String,
  val connection: Connection?
) {
  val displayName = module.name.substringAfterLast(".")

  override fun toString(): String {
    return if (!isConfigured()) "$displayName › (no connection)"
    else "$displayName › $variantName: $connection"
  }

  fun isConfigured(): Boolean {
    return connection != null
  }

  companion object {
    const val ALL_VARIANTS = "All"
    fun createPlaceHolder(module: Module) = VariantConnection(module, ALL_VARIANTS, null)
  }
}
/**
 * A single connection represented by credentials required to access resources for a given project.
 */
data class Connection(
  val appId: String,
  val mobileSdkAppId: String,
  val projectId: String,
  val projectNumber: String
) {
  fun clientId(): String = "android:${appId}"

  override fun toString(): String {
    return "[${stripProjectName(projectId)}] $appId"
  }

  private fun stripProjectName(projectName: String): String {
    return projectName.substringBeforeLast('-')
  }
}

fun List<VariantConnection>.noneIsConfigured(): Boolean = none { it.isConfigured() }

fun List<VariantConnection>.anyIsConfigured(): Boolean = any { it.isConfigured() }

interface ActiveConnectionInferrer {
  /** Determines whether the [variantConnection] can become the active connection. */
  fun canBecomeActiveConnection(variantConnection: VariantConnection): Boolean
}
