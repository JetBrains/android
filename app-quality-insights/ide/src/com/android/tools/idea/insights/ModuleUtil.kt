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

import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet

val Module.isAndroidApp: Boolean
  get() = AndroidFacet.getInstance(this)?.configuration?.isAppProject ?: false

fun Project.getHolderModules(): List<Module> = getAndroidFacets().map { it.holderModule }

val Module.androidAppId: String?
  get() {
    if (AndroidFacet.getInstance(this)?.configuration?.isAppProject != true) {
      return null
    }
    val appId = AndroidModel.get(this)?.applicationId ?: return null
    if (appId == AndroidModel.UNINITIALIZED_APPLICATION_ID) {
      return null
    }
    return appId
  }
