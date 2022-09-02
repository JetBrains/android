/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.designinfo

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.getLastSyncTimestamp
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key

private val PROVIDERS_STATUS_KEY: Key<ProviderStatus> = Key.create(ProviderStatus::class.java.name)

private data class ProviderStatus(val hasProviders: Boolean, val syncTimeStamp: Long)

/**
 * For the given module, determine if there's any library that can provide a DesignInfo object.
 *
 * The value is cached, and is only refreshed if module dependencies may have changed.
 */
fun hasDesignInfoProviders(module: Module): Boolean {
  val lastSync = module.project.getLastSyncTimestamp()
  val providerStatus = module.getUserData(PROVIDERS_STATUS_KEY)

  return if (providerStatus != null && lastSync == providerStatus.syncTimeStamp) {
    providerStatus.hasProviders
  } else {
    findDesignInfoProviders(module).also {
      module.putUserData(PROVIDERS_STATUS_KEY, ProviderStatus(it, lastSync))
    }
  }
}

private fun findDesignInfoProviders(moduleToSearch: Module): Boolean {
  if (!StudioFlags.COMPOSE_CONSTRAINT_VISUALIZATION.get()) return false

  val gradleCoordinate =
    moduleToSearch
      .getModuleSystem()
      .getResolvedDependency(
        GoogleMavenArtifactId.ANDROIDX_CONSTRAINT_LAYOUT_COMPOSE.getCoordinate("+")
      )
      ?.version
      ?: return false

  // Support for DesignInfo was added in 'constraintlayout-compose:1.0.0-alpha06'
  return gradleCoordinate.isAtLeast(1, 0, 0, "alpha", 6, false)
}
