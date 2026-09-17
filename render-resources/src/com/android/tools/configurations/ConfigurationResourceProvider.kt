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
package com.android.tools.configurations

import com.android.ide.common.resources.ResourceItemResolver
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.ResourceResolver

internal class ConfigurationResourceProvider(private val config: Configuration) : ResourceItemResolver.ResourceProvider {
  override fun getResolver(createIfNecessary: Boolean): ResourceResolver? {
    if (createIfNecessary) {
      return config.getResourceResolver()
    }
    return config
      .getSettings()
      .resolverCache
      .getCachedResourceResolver(config.getTarget(), config.getTheme(), config.getFullConfig(), config.getOverlays())
  }

  override fun getFrameworkResources(): ResourceRepository? {
    val manager = config.getConfigModule().resourceRepositoryManager
    return if (manager != null) manager.getFrameworkResources(manager.languagesInProject, config.getOverlays()) else null
  }

  override fun getAppResources(): ResourceRepository? {
    val manager = config.getConfigModule().resourceRepositoryManager
    return if (manager != null) manager.appResources else null
  }
}
