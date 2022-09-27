/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:JvmName("UsageTrackerUtils")

package com.android.tools.idea.stats

import com.android.AndroidProjectTypes
import com.android.tools.idea.model.AndroidModel
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet

/**
 * Create a new event builder with the project information attached.
 *
 * This attaches two pieces of information:
 *
 * 1. The anonymized project ID
 *
 *    This is only stable for this user on this machine for the 28-day log rotation period.
 *
 *    This uses the base path of the project, to match the build system (See
 *    `tools/base/build-system/profile/src/main/java/com/android/builder/profile/ProcessProfileWriterFactory.java#setGlobalProperties`)
 *
 *    The base path is null for the default project (identified by [Project.isDefault] == true), which is used to store defaults.
 *    In that case just the project ID is recorded as "DEFAULT", as this is not a real user project.
 *
 * 2. The raw project ID
 *
 *    This currently is the current variant application ID from the first encountered application module.
 *
 *    This is only populated when the raw project ID is available, which it might not be before the project sync is complete.
 *
 *    TODO(b/123518352): Expand this to include all application IDs in the model.
 */
fun AndroidStudioEvent.Builder.withProjectId(project: Project?) : AndroidStudioEvent.Builder {
  project?.let {

    // The base path is only  null for the default project (identified by [Project.isDefault] == true), which is used to store defaults.
    // In that case just store "DEFAULT", as this is not a real user project.
    this.projectId = if (project.isDefault) {
      "DEFAULT"
    }
    else {
      AnonymizerUtil.anonymizeUtf8(project.basePath!!)
    }
    val appId = kotlin.runCatching { getApplicationId(it) }.getOrLogException(LOG)
    if (appId != null) {
      this.rawProjectId = appId
    }
  }
  return this
}

private fun getApplicationId(project: Project): String? {
  if (project.isDisposed) {
    return null
  }
  val moduleManager = ModuleManager.getInstance(project)
  for (module in moduleManager.modules) {
    if (module.isDisposed) {
      continue
    }
    val androidModel = AndroidModel.get(module)
    if (androidModel != null) {
      val faucet = AndroidFacet.getInstance(module)
      if (faucet != null && faucet.properties.PROJECT_TYPE == AndroidProjectTypes.PROJECT_TYPE_APP) {
        return androidModel.applicationId
      }
    }
  }
  return null
}

private val LOG = logger<AndroidStudioEvent>()
