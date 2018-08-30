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

import com.android.builder.model.AndroidProject
import com.android.tools.idea.model.AndroidModel
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet


fun AndroidStudioEvent.Builder.withProjectId(project: Project?) : AndroidStudioEvent.Builder {
  project?.let {
    val appId = getApplicationId(it)
    if (appId != null) {
      this.rawProjectId = appId
      this.projectId = AnonymizerUtil.anonymizeUtf8(appId)
    }
    else {
      // if this is not an android app, we still want to distinguish the project, so we use the project's path as a key to the anonymziation.
      this.projectId = AnonymizerUtil.anonymizeUtf8(project.baseDir.path)
    }
  }
  return this
}

private fun getApplicationId(project: Project): String? {
  val moduleManager = ModuleManager.getInstance(project)
  for (module in moduleManager.modules) {
    if (module.isDisposed) {
      continue
    }
    val androidModel = AndroidModel.get(module)
    if (androidModel != null) {
      val faucet = AndroidFacet.getInstance(module)
      if (faucet != null && faucet.properties.PROJECT_TYPE == AndroidProject.PROJECT_TYPE_APP) {
        return androidModel.applicationId
      }
    }
  }
  return null
}